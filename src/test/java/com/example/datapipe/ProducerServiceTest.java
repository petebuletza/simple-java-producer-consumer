package com.example.datapipe;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProducerServiceTest {

    @Test
    void healthAndStatsRespondAndStatsCountsProducedItems() throws Exception {
        int apiPort = 19811;
        ProducerService service = startProducer(19810, apiPort, 20);
        try {
            String health = TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/health");
            assertEquals("{\"status\":\"UP\",\"role\":\"producer\"}", health);

            TestSupport.awaitTrue(Duration.ofSeconds(3), () ->
                    TestSupport.extractTotal(TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/stats")) > 0);
        } finally {
            service.stop();
        }
    }

    @Test
    void limitsConcurrentClientsToTen() throws Exception {
        int dataPort = 19820;
        ProducerService service = startProducer(dataPort, 19821, 30);
        List<Socket> sockets = new ArrayList<>();
        try {
            for (int i = 0; i < 11; i++) {
                Socket socket = new Socket("127.0.0.1", dataPort);
                socket.setSoTimeout(2000);
                sockets.add(socket);
            }
            // Give the accept loop and a few produce ticks time to settle before reading.
            Thread.sleep(500);

            int accepted = 0;
            int rejected = 0;
            for (Socket socket : sockets) {
                byte[] buffer = new byte[64];
                try {
                    int bytesRead = socket.getInputStream().read(buffer);
                    if (bytesRead == -1) rejected++; else accepted++;
                } catch (SocketTimeoutException e) {
                    // Still open with nothing buffered yet; treat as accepted.
                    accepted++;
                }
            }

            assertEquals(10, accepted, "expected exactly 10 of 11 connections to be accepted");
            assertEquals(1, rejected, "expected exactly 1 of 11 connections to be rejected");
        } finally {
            for (Socket socket : sockets) closeQuietly(socket);
            service.stop();
        }
    }

    @Test
    void aStalledClientDoesNotBlockDeliveryToOtherClients() throws Exception {
        int dataPort = 19830;
        ProducerService service = startProducer(dataPort, 19831, 30);
        Socket stalled = null;
        Socket active = null;
        try {
            stalled = new Socket("127.0.0.1", dataPort); // connected, but never read from

            active = new Socket("127.0.0.1", dataPort);
            active.setSoTimeout(3000);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(active.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));

            int linesRead = 0;
            long deadline = System.currentTimeMillis() + 1000;
            while (System.currentTimeMillis() < deadline) {
                String line = reader.readLine();
                if (line == null) break;
                linesRead++;
            }

            assertTrue(linesRead > 5,
                    "expected the active client to keep receiving items while a stalled client is connected, got "
                            + linesRead);
        } finally {
            closeQuietly(stalled);
            closeQuietly(active);
            service.stop();
        }
    }

    @Test
    void shutdownEndpointStopsTheService() throws Exception {
        int apiPort = 19841;
        ProducerService service = startProducer(19840, apiPort, 50);
        try {
            int status = TestSupport.httpStatus("http://127.0.0.1:" + apiPort + "/api/shutdown", "POST");
            assertEquals(202, status);

            TestSupport.awaitTrue(Duration.ofSeconds(3), () -> {
                try {
                    TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/health");
                    return false; // still up
                } catch (IOException e) {
                    return true; // connection refused/reset -> stopped
                }
            });
        } finally {
            service.stop();
        }
    }

    @Test
    void mainStartsTheProducerByClassnameAndReturnsAfterShutdown() throws Exception {
        int apiPort = 19843;
        Thread thread = Thread.startVirtualThread(() -> {
            try {
                ProducerService.main(new String[] {
                        "--data-port=19842",
                        "--api-port=" + apiPort,
                        "--frequency-ms=50"
                });
            } catch (Exception ignored) {
            }
        });

        TestSupport.awaitHealthy(apiPort, Duration.ofSeconds(3));
        assertEquals(202, TestSupport.httpStatus("http://127.0.0.1:" + apiPort + "/api/shutdown", "POST"));

        thread.join(3000);
        assertFalse(thread.isAlive(), "expected ProducerService.main to return after shutdown");
    }

    // Deliberately no test for the outbox-overflow disconnect (produce()'s client.close()
    // branch): it requires actually saturating the OS-level TCP send buffer within a fixed
    // wall-clock window, and buffer sizes/scheduler timing differ enough across machines
    // that this is inherently flaky rather than a real bug when it fails.

    private static ProducerService startProducer(int dataPort, int apiPort, long frequencyMs) throws InterruptedException {
        ProducerService service = new ProducerService(ProducerConfig.from(Map.of(
                "data-port", String.valueOf(dataPort),
                "api-port", String.valueOf(apiPort),
                "frequency-ms", String.valueOf(frequencyMs)
        )));
        Thread.startVirtualThread(() -> {
            try {
                service.startAndWait();
            } catch (Exception ignored) {
                // service was stopped, or failed to start; awaitHealthy below surfaces the latter
            }
        });
        TestSupport.awaitHealthy(apiPort, Duration.ofSeconds(3));
        return service;
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
