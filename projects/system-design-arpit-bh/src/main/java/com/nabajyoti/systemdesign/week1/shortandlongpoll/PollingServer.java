package com.nabajyoti.systemdesign.week1.shortandlongpoll;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class PollingServer {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        InstanceStore instanceStore = new InstanceStore();
        Ec2InstanceService instanceService =
                new Ec2InstanceService(instanceStore, 10);
        HttpServer server = HttpServer.create(
                new InetSocketAddress(PORT),
                0
        );
        server.createContext(
                "/instances",
                exchange -> handleCreateInstance(exchange, instanceService)
        );
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    instanceService.shutdown();
                    server.stop(0);
                })
        );
        System.out.println(
                "Polling server running at http://localhost:" + PORT
        );


        server.createContext(
                "/instances/",
                exchange -> handleStatus(exchange, instanceStore)
        );
    }

    private static void handleCreateInstance(
            HttpExchange exchange,
            Ec2InstanceService instanceService
    ) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(
                    exchange,
                    405,
                    "{\"error\":\"Method not allowed\"}"
            );
            return;
        }
        EC2Instance instance = instanceService.createInstance();
        String response = """
                {
                  "instanceId": "%s",
                  "status": "%s"
                }
                """.formatted(
                instance.getId(),
                instance.getStatus()
        );
        sendResponse(exchange, 202, response);
    }

    private static void sendResponse(
            HttpExchange exchange,
            int statusCode,
            String response
    ) throws IOException {
        byte[] responseBytes =
                response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );
        exchange.sendResponseHeaders(
                statusCode,
                responseBytes.length
        );
        try {
            exchange.getResponseBody().write(responseBytes);
        } finally {
            exchange.close();
        }
    }

    private static void handleStatus(
            HttpExchange exchange,
            InstanceStore instanceStore
    ) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(
                    exchange,
                    405,
                    "{\"error\":\"Method not allowed\"}"
            );
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String[] pathParts = path.split("/");

        if (pathParts.length == 5
                && "instances".equals(pathParts[1])
                && "status".equals(pathParts[3])
                && "long-poll".equals(pathParts[4])) {
            handleLongPoll(exchange, pathParts[2], instanceStore);
            return;
        }

        if (pathParts.length != 4
                || !"instances".equals(pathParts[1])
                || !"status".equals(pathParts[3])) {
            sendResponse(
                    exchange,
                    404,
                    "{\"error\":\"Unknown endpoint\"}"
            );
            return;
        }

        String instanceId = pathParts[2];

        EC2Instance instance = instanceStore
                .findById(instanceId)
                .orElse(null);

        if (instance == null) {
            sendResponse(
                    exchange,
                    404,
                    "{\"error\":\"Instance not found\"}"
            );
            return;
        }

        String response = """
            {
              "instanceId": "%s",
              "status": "%s"
            }
            """.formatted(
                instance.getId(),
                instance.getStatus()
        );

        sendResponse(exchange, 200, response);
    }

    private static void handleLongPoll(
            HttpExchange exchange,
            String instanceId,
            InstanceStore instanceStore
    ) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(
                    exchange,
                    405,
                    "{\"error\":\"Method not allowed\"}"
            );
            return;
        }

        EC2Instance instance = instanceStore
                .findById(instanceId)
                .orElse(null);

        if (instance == null) {
            sendResponse(
                    exchange,
                    404,
                    "{\"error\":\"Instance not found\"}"
            );
            return;
        }

        try {
            InstanceStatus status =
                    instance.waitForCompletion(15_000);

            String response = """
                {
                  "instanceId": "%s",
                  "status": "%s"
                }
                """.formatted(
                    instance.getId(),
                    status
            );

            sendResponse(exchange, 200, response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            sendResponse(
                    exchange,
                    500,
                    "{\"error\":\"Long poll interrupted\"}"
            );
        }
    }
}


