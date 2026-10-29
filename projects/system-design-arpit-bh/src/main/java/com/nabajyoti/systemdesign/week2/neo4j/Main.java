package com.nabajyoti.systemdesign.week2.neo4j;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class Main {

    static void ingestPeople(Session session, int total, int batchSize) {
        for (int start = 1; start <= total; start += batchSize) {
            int end = Math.min(start + batchSize - 1, total);
            List<Integer> ids = new ArrayList<>();
            for (int id = start; id <= end; id++) {
                ids.add(id);
            }

            session.executeWrite(tx -> {
                tx.run("""
                    UNWIND $ids AS id
                    MERGE (p:Person {id: id})
                    SET p.name = 'Person-' + id
                    """, Map.of("ids", ids));
                return null;
            });
        }
    }

    public static void main(String[] args) {
        try (Driver driver = GraphDatabase.driver(
                "bolt://localhost:7687",
                AuthTokens.basic("neo4j", "password"))) {
            driver.verifyConnectivity();
            try (Session session = driver.session()) {
                System.out.println("session open: " + session.isOpen());

                session.executeWrite(tx -> {
                    tx.run("""
                            CREATE CONSTRAINT person_id IF NOT EXISTS
                            FOR (p:Person) REQUIRE p.id IS UNIQUE
                            """);
                    return null;
                });

                System.out.println("constraint created");

                ingestPeople(session, 2000, 500);
                System.out.println("people ingested");

                ingestFollows(session, 1, 2, 51);
                System.out.println("follows ingested");

                for (int page = 0; page < 3; page++) {
                    System.out.println("page " + page);
                    pageByOffset(session, 1, page, 10)
                            .forEach(row -> System.out.println(row.get("id")));
                }

                long cursor = 0;
                while (true) {
                    var page = pageByCursor(session, 1, cursor, 10);
                    if (page.isEmpty()) {
                        break;
                    }
                    System.out.println("cursor " + cursor);
                    page.forEach(row -> System.out.println(row.get("id")));
                    cursor = (long) page.get(page.size() - 1).get("id");
                }
            }
        }
    }

    static void ingestFollows(Session session, long fromId, int firstOtherId, int lastOtherId) {
        List<Integer> otherIds = new ArrayList<>();
        for (int id = firstOtherId; id <= lastOtherId; id++) {
            otherIds.add(id);
        }

        session.executeWrite(tx -> {
            tx.run("""
                MATCH (a:Person {id: $fromId})
                UNWIND $otherIds AS otherId
                MATCH (b:Person {id: otherId})
                MERGE (a)-[:FOLLOWS]->(b)
                """, Map.of("fromId", fromId, "otherIds", otherIds));
            return null;
        });
    }

    static List<Map<String, Object>> pageByCursor(Session session, long userId, long cursor, int size) {
        return session.executeRead(tx -> tx.run("""
            MATCH (me:Person {id: $userId})-[:FOLLOWS]->(other:Person)
            WHERE other.id > $cursor
            RETURN other.id AS id, other.name AS name
            ORDER BY other.id
            LIMIT $size
            """, Map.of("userId", userId, "cursor", cursor, "size", size))
                .list(r -> Map.of(
                        "id", r.get("id").asLong(),
                        "name", r.get("name").asString())));
    }

    static List<Map<String, Object>> pageByOffset(Session session, long userId, int page, int size) {
        int skip = page * size;
        return session.executeRead(tx -> tx.run("""
            MATCH (me:Person {id: $userId})-[:FOLLOWS]->(other:Person)
            RETURN other.id AS id, other.name AS name
            ORDER BY other.id
            SKIP $skip
            LIMIT $size
            """, Map.of("userId", userId, "skip", skip, "size", size))
                .list(r -> Map.of(
                        "id", r.get("id").asLong(),
                        "name", r.get("name").asString())));
    }
}