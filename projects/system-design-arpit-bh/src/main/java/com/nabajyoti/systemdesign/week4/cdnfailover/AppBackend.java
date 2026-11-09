package com.nabajyoti.systemdesign.week4.cdnfailover;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class AppBackend {
    private static final int PORT = 8080;

    private final WeightedCdnUrlPicker picker;

    public AppBackend(WeightedCdnUrlPicker picker) {
        this.picker = picker;
    }

    public static void main(String[] args) throws IOException {
        new AppBackend(new WeightedCdnUrlPicker()).start();
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Backend listening on http://localhost:" + PORT);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                byte[] body = "GET only\n".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(405, body.length);
                exchange.getResponseBody().write(body);
                return;
            }

            String cdnUrl = picker.urlFor("/index.html");
            String chosenHost = URI.create(cdnUrl).getHost();
            byte[] body = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <title>App</title>
                    </head>
                    <body>
                      <p>Open the site through the CDN:</p>
                      <p><a href="%s">%s</a></p>
                    </body>
                    </html>
                    """.formatted(cdnUrl, cdnUrl).getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("X-Chosen-Host", chosenHost);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}