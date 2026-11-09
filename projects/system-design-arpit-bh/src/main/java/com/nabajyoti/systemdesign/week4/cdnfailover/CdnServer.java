package com.nabajyoti.systemdesign.week4.cdnfailover;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

public class CdnServer {

    private static final int PORT = 8081;
    private final Map<String, CdnProperty> properties;
    private final HttpClient originClient;
    public CdnServer(Map<String, CdnProperty> properties) {
        this.properties = properties;
        this.originClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public static void main(String[] args) throws IOException {
        new CdnServer(CdnProperty.defaultProperties()).start();
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("CDN listening on http://localhost:" + PORT);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, "text/plain; charset=utf-8",
                        "GET only\n".getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            String hostname = hostnameFrom(exchange);
            CdnProperty property = properties.get(hostname);
            if (property == null) {
                send(exchange, 404, "text/plain; charset=utf-8",
                        ("unknown host: " + hostname + "\n")
                                .getBytes(StandardCharsets.UTF_8),
                        null);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(property.originBaseUrl() + path))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<byte[]> originResponse = originClient.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = originResponse.headers()
                    .firstValue("Content-Type")
                    .orElse("application/octet-stream");
            send(exchange, originResponse.statusCode(), contentType,
                    originResponse.body(), property.hostname());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            send(exchange, 502, "text/plain; charset=utf-8",
                    "origin interrupted\n".getBytes(StandardCharsets.UTF_8), null);
        } catch (IOException e) {
            send(exchange, 502, "text/plain; charset=utf-8",
                    ("origin error: " + e.getMessage() + "\n")
                            .getBytes(StandardCharsets.UTF_8),
                    null);
        }
    }

    static String hostnameFrom(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            return "";
        }
        int colon = host.lastIndexOf(':');
        if (colon >= 0 && !host.startsWith("[")) {
            return host.substring(0, colon);
        }
        return host;
    }

    private static void send(
            HttpExchange exchange,
            int status,
            String contentType,
            byte[] body,
            String propertyHostname
    ) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (propertyHostname != null) {
            exchange.getResponseHeaders().set("X-Cdn-Property", propertyHostname);
        }
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
