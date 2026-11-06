package com.nabajyoti.systemdesign.week3.snowflake;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class SnowflakeHttpServer {
    private static final int DEFAULT_PORT = 8080;

    private SnowflakeHttpServer() {
    }

    public static void main(String[] args) throws IOException {
        long workerId = parseWorkerId(args);
        int port = parsePort(args);

        SnowflakeIdGenerator generator =
                new SnowflakeIdGenerator(workerId);

        HttpServer server = HttpServer.create(
                new InetSocketAddress(port),
                0
        );

        server.createContext(
                "/api/ids",
                exchange -> handleIdRequest(exchange, generator)
        );

        server.setExecutor(
                Executors.newVirtualThreadPerTaskExecutor()
        );

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> server.stop(0))
        );
        server.start();

        System.out.println(
                "Snowflake API listening on http://localhost:" + port
                        + " with worker ID " + workerId
        );
    }

    private static void handleIdRequest(
            HttpExchange exchange,
            SnowflakeIdGenerator generator
    )
            throws IOException {
        try (exchange) {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(
                        exchange,
                        405,
                        "{\"error\":\"Only POST is supported\"}"
                );
                return;
            }

            long id = generator.nextId();
            SnowflakeId decodedId = SnowflakeId.decode(id);

            String response = """
                    {
                      "id": "%d",
                      "timestamp": %d,
                      "workerId": %d,
                      "sequence": %d
                    }
                    """.formatted(
                    decodedId.id(),
                    decodedId.timestamp(),
                    decodedId.workerId(),
                    decodedId.sequence()
            );

            sendJson(exchange, 200, response);
        } catch (IllegalStateException exception) {
            sendJson(
                    exchange,
                    503,
                    "{\"error\":\"ID generation unavailable\"}"
            );
        }
    }

    private static long parseWorkerId(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: SnowflakeHttpServer <workerId> [port]"
            );
        }

        try {
            return Long.parseLong(args[0]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "workerId must be a number",
                    exception
            );
        }
    }

    private static int parsePort(String[] args) {
        if (args.length < 2) {
            return DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(args[1]);

            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException(
                        "port must be between 1 and 65535"
                );
            }

            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "port must be a number",
                    exception
            );
        }
    }

    private static void sendJson(
            HttpExchange exchange,
            int statusCode,
            String body
    ) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );

        exchange.sendResponseHeaders(
                statusCode,
                responseBytes.length
        );

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
