package com.nabajyoti.systemdesign.week4.cdnfailover;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public class OriginServer {
    private final String name;
    private final int port;

    public OriginServer(String name, int port) {
        this.name = name;
        this.port = port;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: OriginServer <name> <port>");
            System.err.println("Example: OriginServer origin-1 8091");
            return;
        }
        String name = args[0];
        int port = Integer.parseInt(args[1]);
        new OriginServer(name, port).start();
    }

    public void start() throws IOException {
        byte[] indexHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                  <title>Same website</title>
                  <link rel="stylesheet" href="/style.css">
                </head>
                <body>
                  <h1>Same website</h1>
                  <p>Served by %s</p>
                </body>
                </html>
                """.formatted(name).getBytes(StandardCharsets.UTF_8);
        byte[] styleCss = "h1 { color: #1a73e8; }\n"
                .getBytes(StandardCharsets.UTF_8);
        Map<String, Resource> resources = Map.of(
                "/index.html", new Resource("text/html; charset=utf-8", indexHtml),
                "/style.css", new Resource("text/css; charset=utf-8", styleCss)
        );
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> handle(exchange, resources));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println(name + " listening on http://localhost:" + port);
    }

    private record Resource(String contentType, byte[] body) {
    }

    private static void handle(HttpExchange exchange, Map<String, Resource> resources)
            throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, "text/plain; charset=utf-8",
                        "GET only\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            Resource resource = resources.get(exchange.getRequestURI().getPath());
            if (resource == null) {
                send(exchange, 404, "text/plain; charset=utf-8",
                        "not found\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            send(exchange, 200, resource.contentType(), resource.body());
        }
    }

    private static void send(
            HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
