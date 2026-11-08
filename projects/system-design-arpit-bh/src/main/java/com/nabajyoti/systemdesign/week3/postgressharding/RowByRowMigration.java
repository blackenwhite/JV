package com.nabajyoti.systemdesign.week3.postgressharding;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/*
* Why this approach is worse at scale
For every user, the program performs:

One user insert.
One profile lookup.
Possibly one profile insert.
One posts lookup.
One insert per post.
* */

public class RowByRowMigration {

    private static final String USER = "insta_user";
    private static final String PASSWORD = "insta_password";

    private static final String SOURCE_URL =
            "jdbc:postgresql://localhost:5433/insta1";

    private static final String DESTINATION_URL =
            "jdbc:postgresql://localhost:5434/insta1_row_copy";

    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();

        try (Connection source =
                     DriverManager.getConnection(SOURCE_URL, USER, PASSWORD);
             Connection destination =
                     DriverManager.getConnection(DESTINATION_URL, USER, PASSWORD)) {

            destination.setAutoCommit(false);

            String insertUser = """
                    INSERT INTO users (id, username, email, created_at)
                    VALUES (?, ?, ?, ?)
                    """;

            String insertProfile = """
                    INSERT INTO profiles
                        (user_id, display_name, bio, avatar_url)
                    VALUES (?, ?, ?, ?)
                    """;

            String insertPost = """
                    INSERT INTO posts
                        (id, user_id, content, created_at, visibility)
                    VALUES (?, ?, ?, ?, ?)
                    """;

            String findProfile = """
                    SELECT display_name, bio, avatar_url
                    FROM profiles
                    WHERE user_id = ?
                    """;

            String findPosts = """
                    SELECT id, user_id, content, created_at, visibility
                    FROM posts
                    WHERE user_id = ?
                    ORDER BY id
                    """;

            try (PreparedStatement userInsert =
                         destination.prepareStatement(insertUser);
                 PreparedStatement profileInsert =
                         destination.prepareStatement(insertProfile);
                 PreparedStatement postInsert =
                         destination.prepareStatement(insertPost);
                 PreparedStatement profileSelect =
                         source.prepareStatement(findProfile);
                 PreparedStatement postsSelect =
                         source.prepareStatement(findPosts);
                 Statement usersSelect = source.createStatement();
                 ResultSet users = usersSelect.executeQuery("""
                         SELECT id, username, email, created_at
                         FROM users
                         ORDER BY id
                         """)) {

                while (users.next()) {
                    long userId = users.getLong("id");

                    userInsert.setLong(1, userId);
                    userInsert.setString(2, users.getString("username"));
                    userInsert.setString(3, users.getString("email"));
                    userInsert.setObject(4, users.getObject("created_at"));
                    userInsert.executeUpdate();

                    profileSelect.setLong(1, userId);

                    try (ResultSet profile = profileSelect.executeQuery()) {
                        if (profile.next()) {
                            profileInsert.setLong(1, userId);
                            profileInsert.setString(
                                    2, profile.getString("display_name"));
                            profileInsert.setString(
                                    3, profile.getString("bio"));
                            profileInsert.setString(
                                    4, profile.getString("avatar_url"));
                            profileInsert.executeUpdate();
                        }
                    }

                    postsSelect.setLong(1, userId);

                    try (ResultSet posts = postsSelect.executeQuery()) {
                        while (posts.next()) {
                            postInsert.setLong(1, posts.getLong("id"));
                            postInsert.setLong(2, posts.getLong("user_id"));
                            postInsert.setString(
                                    3, posts.getString("content"));
                            postInsert.setObject(
                                    4, posts.getObject("created_at"));
                            postInsert.setString(
                                    5, posts.getString("visibility"));
                            postInsert.executeUpdate();
                        }
                    }
                }

                destination.commit();
            } catch (Exception exception) {
                destination.rollback();
                throw exception;
            }
        }

        long elapsedMillis =
                (System.nanoTime() - start) / 1_000_000;

        System.out.println(
                "Row-by-row migration completed in "
                        + elapsedMillis + " ms");
    }
}