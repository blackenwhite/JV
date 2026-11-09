package com.nabajyoti.systemdesign.week4.toycdn;


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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class EdgeServer {

    private static final int DEFAULT_PORT = 8081;
    private static final String ORIGIN = "http://localhost:8090";
    private final int port;
    private final HttpClient originClient;
    private final Map<String, CachedResponse> cache = new HashMap<>();

    public EdgeServer(int port) {
        this.port = port;
        this.originClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new EdgeServer(port).start();
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Edge listening on http://localhost:" + port);

        server.createContext("/purge", this::handlePurge);
        server.createContext("/", this::handle);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(
                        exchange,
                        405,
                        "text/plain; charset=utf-8",
                        "GET only\n".getBytes(StandardCharsets.UTF_8),
                        "NONE",
                        null
                );
                return;
            }
            String path = exchange.getRequestURI().getPath();
            CachedResponse cached = cache.get(path);

            String cacheHeader;
            if (cached != null && isFresh(cached)) {
                cacheHeader = "HIT";
            } else {
                cached = fetchFromOrigin(path, cached);
                cache.put(path, cached);
                cacheHeader = cached.revalidated() ? "REVALIDATED" : "MISS";
            }

            send(
                    exchange,
                    cached.status(),
                    cached.contentType(),
                    cached.body(),
                    cacheHeader,
                    cached
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            send(
                    exchange,
                    502,
                    "text/plain; charset=utf-8",
                    "origin interrupted\n".getBytes(StandardCharsets.UTF_8),
                    "NONE",
                    null
            );
        }
    }
    private static boolean isFresh(CachedResponse cached) {
        return System.currentTimeMillis() < cached.expiresAtMillis();
    }

    private static void send(
            HttpExchange exchange,
            int status,
            String contentType,
            byte[] body,
            String cacheHeader,
            CachedResponse cached
    ) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Cache", cacheHeader);
        if (cached != null) {
            if (!cached.etag().isBlank()) {
                exchange.getResponseHeaders().set("ETag", cached.etag());
            }
            if (!cached.cacheControl().isBlank()) {
                exchange.getResponseHeaders().set("Cache-Control", cached.cacheControl());
            }
        }
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private CachedResponse fetchFromOrigin(
            String path,
            CachedResponse stale
    ) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(ORIGIN + path))
                .GET()
                .timeout(Duration.ofSeconds(3));

        if (stale != null && !stale.etag().isBlank()) {
            builder.header("If-None-Match", stale.etag());
        }

        HttpResponse<byte[]> response = originClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        if (response.statusCode() == 304 && stale != null) {
            String cacheControl = response.headers()
                    .firstValue("Cache-Control")
                    .orElse(stale.cacheControl());
            int maxAgeSeconds = parseMaxAge(cacheControl);
            long expiresAtMillis = System.currentTimeMillis() + maxAgeSeconds * 1000L;
            return new CachedResponse(
                    stale.status(),
                    stale.contentType(),
                    stale.etag(),
                    cacheControl,
                    stale.body(),
                    expiresAtMillis,
                    true
            );
        }

        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("application/octet-stream");
        String etag = response.headers().firstValue("ETag").orElse("");
        String cacheControl = response.headers()
                .firstValue("Cache-Control")
                .orElse("public, max-age=30");
        int maxAgeSeconds = parseMaxAge(cacheControl);
        long expiresAtMillis = System.currentTimeMillis() + maxAgeSeconds * 1000L;

        return new CachedResponse(
                response.statusCode(),
                contentType,
                etag,
                cacheControl,
                response.body(),
                expiresAtMillis,
                false
        );
    }

    private static int parseMaxAge(String cacheControl) {
        String marker = "max-age=";
        int start = cacheControl.toLowerCase().indexOf(marker);
        if (start < 0) {
            return 0;
        }
        start += marker.length();
        int end = start;
        while (end < cacheControl.length() && Character.isDigit(cacheControl.charAt(end))) {
            end++;
        }
        if (end == start) {
            return 0;
        }
        return Integer.parseInt(cacheControl.substring(start, end));
    }

    private record CachedResponse(
            int status,
            String contentType,
            String etag,
            String cacheControl,
            byte[] body,
            long expiresAtMillis,
            boolean revalidated
    ) {
    }

    private void handlePurge(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                send(
                        exchange,
                        405,
                        "text/plain; charset=utf-8",
                        "POST only\n".getBytes(StandardCharsets.UTF_8),
                        "NONE",
                        null
                );
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String path = queryValue(query, "path");
            if (path == null || path.isBlank()) {
                send(
                        exchange,
                        400,
                        "text/plain; charset=utf-8",
                        "missing path query parameter\n".getBytes(StandardCharsets.UTF_8),
                        "NONE",
                        null
                );
                return;
            }

            CachedResponse removed = cache.remove(path);
            String message = removed == null
                    ? "no cache entry for " + path + "\n"
                    : "purged " + path + "\n";
            send(
                    exchange,
                    200,
                    "text/plain; charset=utf-8",
                    message.getBytes(StandardCharsets.UTF_8),
                    "NONE",
                    null
            );
        }
    }

    private static String queryValue(String query, String name) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String prefix = name + "=";
        for (String part : query.split("&")) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        return null;
    }
}