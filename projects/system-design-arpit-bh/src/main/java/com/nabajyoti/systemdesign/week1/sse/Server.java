package com.nabajyoti.systemdesign.week1.sse;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Server {
    private static final Set<BlockingQueue<String>> clients = ConcurrentHashMap.newKeySet();
    private static final AtomicLong nextId = new AtomicLong(1);

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/hello", exchange -> {
            byte[] body = "Hello from Java".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.createContext("/events", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            clients.add(queue);
            System.out.println("Client connected. Total clients: " + clients.size());

            try {
                os.write("retry: 3000\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();

                while (true) {
                    // Wait up to 3 seconds for an event to arrive in my queue
                    String event = queue.poll(3, TimeUnit.SECONDS);
                    if (event != null) {
                        os.write(event.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    } else {
                        sendHeartbeat(os); // nothing arrived, send a heartbeat
                    }
                }
            } catch (IOException | InterruptedException e) {
                // client left
            } finally {
                clients.remove(queue);
                System.out.println("Client disconnected. Total clients: " + clients.size());
                exchange.close();
            }
        });

        server.createContext("/publish", exchange -> {
            String query = exchange.getRequestURI().getQuery();   // e.g. "msg=hello"
            String msg = "(empty)";
            if (query != null && query.startsWith("msg=")) {
                msg = URLDecoder.decode(query.substring(4), StandardCharsets.UTF_8);
            }
            msg = msg.replace("\n", " ").replace("\r", " ");      // protect the SSE format

            int count = broadcast("message", msg);

            byte[] body = ("Sent to " + count + " client(s)\n").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Server running on http://localhost:8080");
    }

    private static int broadcast(String name, String data) {
        long id = nextId.getAndIncrement();
        String event = "id: " + id + "\n"
                     + "event: " + name + "\n"
                     + "data: " + data + "\n\n";
        for (BlockingQueue<String> queue : clients) {
            queue.offer(event);
        }
        return clients.size();
    }

    private static void sendHeartbeat(OutputStream os) throws IOException {
        os.write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    private static void sendEvent(OutputStream os, long id, String name, String data) throws IOException {
        String event = "id: " + id + "\n"
                + "event: " + name + "\n"
                + "data: " + data + "\n\n";
        os.write(event.getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    private static long readLastEventId(com.sun.net.httpserver.HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Last-Event-ID");
        if (header == null) {
            return 0;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
