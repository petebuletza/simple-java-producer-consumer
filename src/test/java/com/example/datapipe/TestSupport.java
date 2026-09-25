package com.example.datapipe;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared HTTP and polling helpers for the integration-style tests in this package. */
final class TestSupport {
    private TestSupport() {}

    static String httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(500);
        conn.setReadTimeout(2000);
        conn.setRequestMethod("GET");
        return readBody(conn);
    }

    static int httpStatus(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(500);
        conn.setReadTimeout(2000);
        conn.setRequestMethod(method);
        if ("POST".equals(method)) {
            conn.setDoOutput(true);
            conn.getOutputStream().close();
        }
        int status = conn.getResponseCode();
        readBody(conn);
        return status;
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) return "";
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Polls {@code /api/health} on {@code apiPort} until it answers or {@code timeout} elapses. */
    static void awaitHealthy(int apiPort, Duration timeout) throws InterruptedException {
        String url = "http://127.0.0.1:" + apiPort + "/api/health";
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                httpGet(url);
                return;
            } catch (IOException ignored) {
                // not up yet
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Service on port " + apiPort + " did not become healthy within " + timeout);
    }

    /** Extracts the {@code "total"} field from a {@code /api/stats} JSON body. */
    static int extractTotal(String statsJson) {
        Matcher m = Pattern.compile("\"total\":(\\d+)").matcher(statsJson);
        if (!m.find()) throw new IllegalStateException("No total field in: " + statsJson);
        return Integer.parseInt(m.group(1));
    }

    /** Polls {@code condition} until it returns {@code true} or {@code timeout} elapses. */
    static void awaitTrue(Duration timeout, ThrowingBooleanSupplier condition) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(20);
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError("Condition not met within " + timeout);
        }
    }

    @FunctionalInterface
    interface ThrowingBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
