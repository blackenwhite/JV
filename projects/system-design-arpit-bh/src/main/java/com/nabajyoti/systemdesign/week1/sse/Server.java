package com.nabajyoti.systemdesign.week1.sse;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class Server {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/hello", exchange -> {
            byte[] body = "Hello from Java".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Server running on http://localhost:8080");

        server.createContext("/events", exchange -> {
            // 1. Tell the client this is an event stream
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");

            // 2. Send headers. Length 0 = "I don't know the size, I'll keep streaming"
            exchange.sendResponseHeaders(200, 0);

            // 3. Write events down the open connection
            OutputStream os = exchange.getResponseBody();
            for (int i = 1; i <= 5; i++) {
                String event = "data: message " + i + "\n\n";
                os.write(event.getBytes(StandardCharsets.UTF_8));
                os.flush();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.close();
        });
    }
}
