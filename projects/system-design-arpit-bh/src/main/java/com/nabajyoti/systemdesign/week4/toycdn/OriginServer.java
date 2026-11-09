package com.nabajyoti.systemdesign.week4.toycdn;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executors;

public class OriginServer {
    private static final int PORT = 8090;
    private OriginServer() {
    }

    public static void main(String[] args) throws IOException {
        byte[] indexHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                  <title>Toy CDN</title>
                  <link rel="stylesheet" href="/style.css">
                </head>
                <body>
                  <h1>Toy CDN</h1>
                  <p>This page lives on the origin.</p>
                </body>
                </html>
                """.getBytes(StandardCharsets.UTF_8);
        byte[] styleCss = "h1 { color: #1a73e8; }\n"
                .getBytes(StandardCharsets.UTF_8);
        Map<String, Resource> resources = Map.of(
                "/index.html", Resource.of("text/html; charset=utf-8", indexHtml),
                "/style.css", Resource.of("text/css; charset=utf-8", styleCss)
        );
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", exchange -> handle(exchange, resources));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Origin listening on http://localhost:" + PORT);
    }

    private static String etagFor(byte[] body) {
        return "\"" + Integer.toHexString(Arrays.hashCode(body)) + "\"";
    }

    private record Resource(String contentType, byte[] body, String etag) {
        static Resource of(String contentType, byte[] body) {
            return new Resource(contentType, body, etagFor(body));
        }
    }

    private static void handle(HttpExchange exchange, Map<String, Resource> resources) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, "text/plain; charset=utf-8", "GET only\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            Resource resource = resources.get(exchange.getRequestURI().getPath());
            if (resource == null) {
                send(exchange, 404, "text/plain; charset=utf-8", "not found\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            exchange.getResponseHeaders().set("ETag", resource.etag());
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=30");
            String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
            if (ifNoneMatch != null && ifNoneMatch.equals(resource.etag())) {
                exchange.sendResponseHeaders(304, -1);
                return;
            }
            send(exchange, 200, resource.contentType(), resource.body());
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
