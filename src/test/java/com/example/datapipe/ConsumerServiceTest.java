package com.example.datapipe;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerServiceTest {

    @Test
    void connectsToProducerAndCountsReceivedItems() throws Exception {
        int producerPort = 19850;
        int apiPort = 19851;

        try (FakeProducer producer = new FakeProducer(producerPort)) {
            ConsumerService service = startConsumer(producerPort, apiPort, 200);
            try {
                producer.acceptOneConnection();

                DataItem first = DataItem.random();
                DataItem second = DataItem.random();
                producer.writeLine(first.toWireLine());
                producer.writeLine(second.toWireLine());

                TestSupport.awaitTrue(Duration.ofSeconds(3), () ->
                        TestSupport.extractTotal(TestSupport.httpGet(
                                "http://127.0.0.1:" + apiPort + "/api/stats")) >= 2);

                String stats = TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/stats");
                assertEquals(2, TestSupport.extractTotal(stats));
                assertTrue(stats.contains(second.id().toString()));
            } finally {
                service.stop();
            }
        }
    }

    @Test
    void discardsAMalformedLineWithoutDroppingTheConnection() throws Exception {
        int producerPort = 19860;
        int apiPort = 19861;

        try (FakeProducer producer = new FakeProducer(producerPort)) {
            ConsumerService service = startConsumer(producerPort, apiPort, 200);
            try {
                producer.acceptOneConnection();

                producer.writeLine("this-is-not-a-valid-wire-line");
                DataItem good = DataItem.random();
                producer.writeLine(good.toWireLine());

                TestSupport.awaitTrue(Duration.ofSeconds(3), () ->
                        TestSupport.extractTotal(TestSupport.httpGet(
                                "http://127.0.0.1:" + apiPort + "/api/stats")) >= 1);

                // Written on the same FakeProducer socket: if the malformed line above had
                // forced a reconnect, the consumer would no longer be reading this connection
                // and this item would never show up in stats.
                DataItem afterMalformedLine = DataItem.random();
                producer.writeLine(afterMalformedLine.toWireLine());

                TestSupport.awaitTrue(Duration.ofSeconds(3), () ->
                        TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/stats")
                                .contains(afterMalformedLine.id().toString()));

                assertEquals(2, TestSupport.extractTotal(
                        TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/stats")));
            } finally {
                service.stop();
            }
        }
    }

    @Test
    void reconnectsAfterTheProducerConnectionDrops() throws Exception {
        int producerPort = 19870;
        int apiPort = 19871;

        try (FakeProducer producer = new FakeProducer(producerPort)) {
            ConsumerService service = startConsumer(producerPort, apiPort, 100);
            try {
                producer.acceptOneConnection();
                DataItem beforeDrop = DataItem.random();
                producer.writeLine(beforeDrop.toWireLine());
                TestSupport.awaitTrue(Duration.ofSeconds(3), () ->
                        TestSupport.extractTotal(TestSupport.httpGet(
                                "http://127.0.0.1:" + apiPort + "/api/stats")) >= 1);

                producer.disconnectClient();
                producer.acceptOneConnection(); // blocks until the consumer's reconnect attempt arrives

                DataItem afterReconnect = DataItem.random();
                producer.writeLine(afterReconnect.toWireLine());

                TestSupport.awaitTrue(Duration.ofSeconds(3), () ->
                        TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/stats")
                                .contains(afterReconnect.id().toString()));

                assertEquals(2, TestSupport.extractTotal(
                        TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/stats")));
            } finally {
                service.stop();
            }
        }
    }

    @Test
    void retriesWithBackoffWhileTheProducerIsUnreachable() throws Exception {
        int producerPort = 19880;
        int apiPort = 19881;

        // Nothing is listening on producerPort yet: the consumer's connect attempts fail
        // outright (unlike a graceful disconnect, which the reconnect test above exercises),
        // so this drives the actual catch(Exception)+sleepBeforeReconnect backoff path.
        ConsumerService service = startConsumer(producerPort, apiPort, 100);
        try {
            Thread.sleep(250);

            try (FakeProducer producer = new FakeProducer(producerPort)) {
                producer.acceptOneConnection(); // succeeds once the next retry lands here
                DataItem item = DataItem.random();
                producer.writeLine(item.toWireLine());

                TestSupport.awaitTrue(Duration.ofSeconds(3), () ->
                        TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/stats")
                                .contains(item.id().toString()));
            }
        } finally {
            service.stop();
        }
    }

    @Test
    void mainStartsTheConsumerByClassnameAndReturnsAfterShutdown() throws Exception {
        int producerPort = 19930;
        int apiPort = 19931;
        Thread thread = Thread.startVirtualThread(() -> {
            try {
                ConsumerService.main(new String[] {
                        "--producer-port=" + producerPort,
                        "--api-port=" + apiPort,
                        "--reconnect-ms=200"
                });
            } catch (Exception ignored) {
            }
        });

        TestSupport.awaitHealthy(apiPort, Duration.ofSeconds(3));
        assertEquals(202, TestSupport.httpStatus("http://127.0.0.1:" + apiPort + "/api/shutdown", "POST"));

        thread.join(3000);
        assertFalse(thread.isAlive(), "expected ConsumerService.main to return after shutdown");
    }

    private static ConsumerService startConsumer(int producerPort, int apiPort, long reconnectMs) throws InterruptedException {
        ConsumerService service = new ConsumerService(ConsumerConfig.from(Map.of(
                "producer-port", String.valueOf(producerPort),
                "api-port", String.valueOf(apiPort),
                "reconnect-ms", String.valueOf(reconnectMs)
        )));
        Thread.startVirtualThread(() -> {
            try {
                service.startAndWait();
            } catch (Exception ignored) {
            }
        });
        TestSupport.awaitHealthy(apiPort, Duration.ofSeconds(3));
        return service;
    }

    /** A minimal, test-controlled stand-in for a producer's TCP data port. */
    private static final class FakeProducer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private Socket connected;

        FakeProducer(int port) throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.setSoTimeout(5000);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", port));
        }

        void acceptOneConnection() throws IOException {
            connected = serverSocket.accept();
            connected.setTcpNoDelay(true);
        }

        void writeLine(String line) throws IOException {
            connected.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
            connected.getOutputStream().flush();
        }

        void disconnectClient() throws IOException {
            connected.close();
        }

        @Override
        public void close() throws IOException {
            if (connected != null) {
                try {
                    connected.close();
                } catch (IOException ignored) {
                }
            }
            serverSocket.close();
        }
    }
}
