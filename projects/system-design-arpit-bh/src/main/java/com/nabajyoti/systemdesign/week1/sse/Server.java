package com.nabajyoti.systemdesign.week1.sse;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
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
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            try{
                os.write("retry: 3000\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();

                long id = 1;
                while(true) {
                    sendEvent(os, id, "tick", "{\"count\": " + id + "}");
                    id++;
                    Thread.sleep(1000);
                }
            }catch(Exception e) {
                System.out.println("client disconnected");
            }finally {
                exchange.close();
            }
        });
    }

    private static void sendEvent(OutputStream os, long id, String name, String data) throws IOException {
        String event = "id: " + id + "\n"
                + "event: " + name + "\n"
                + "data: " + data + "\n\n";
        os.write(event.getBytes(StandardCharsets.UTF_8));
        os.flush();
    }
}
