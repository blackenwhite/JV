package com.nabajyoti.systemdesign.week2.broadcast;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ChatServer {
    private static final String CHANNEL = "broadcast";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("usage: ChatServer <serverId>");
            return;
        }

        String serverId = args[0];
        startSubscriber(serverId);

        System.out.println(serverId + " ready. Type a line and press enter.");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        try (Jedis publisher = new Jedis("localhost", 6379)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                ChatMessage message = new ChatMessage(serverId, serverId, line);
                publisher.publish(CHANNEL, message.toJson());
                System.out.println("[" + serverId + "] local: " + line);
            }
        }
    }

    private static void startSubscriber(String serverId) {
        Thread thread = new Thread(() -> {
            try (Jedis subscriber = new Jedis("localhost", 6379)) {
                subscriber.subscribe(new JedisPubSub() {
                    @Override
                    public void onSubscribe(String channel, int subscribedChannels) {
                        System.out.println(serverId + " listening on " + channel);
                    }

                    @Override
                    public void onMessage(String channel, String body) {
                        ChatMessage incoming = ChatMessage.fromJson(body);
                        if (incoming.serverId().equals(serverId)) {
                            return;
                        }
                        System.out.println("[" + serverId + "] received: "
                                + incoming.user() + ": " + incoming.text());
                    }
                }, CHANNEL);
            }
        }, "redis-subscriber");
        thread.setDaemon(true);
        thread.start();
    }
}