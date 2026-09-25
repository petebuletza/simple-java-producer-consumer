package com.example.datapipe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class HttpApi implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Runnable shutdownAction;
    private final Stats stats;
    private final String role;

    HttpApi(String host, int port, String role, Stats stats, Runnable shutdownAction) throws IOException {
        this.stats = stats;
        this.role = role;
        this.shutdownAction = shutdownAction;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.setExecutor(executor);
        server.createContext("/api/stats", this::stats);
        server.createContext("/api/health", this::health);
        server.createContext("/api/shutdown", this::shutdown);
    }

    void start() {
        server.start();
    }

    private void stats(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        send(exchange, 200, stats.toJson(role));
    }

    private void health(HttpExchange exchange) throws IOException {
        send(exchange, 200, "{\"status\":\"UP\",\"role\":\"" + role + "\"}");
    }

    private void shutdown(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"use POST\"}");
            return;
        }
        send(exchange, 202, "{\"status\":\"SHUTTING_DOWN\"}");
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            shutdownAction.run();
        });
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
