package com.example.datapipe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the consumer role: connects to a producer's TCP data port, parses each
 * {@link DataItem} off the wire, and exposes an HTTP API for health,
 * statistics, and shutdown. Reconnects automatically if the producer is
 * unavailable or the connection drops.
 */
public final class ConsumerService {
    private final ConsumerConfig config;
    private final Stats stats = new Stats();
    private final AtomicBoolean running = new AtomicBoolean();
    private HttpApi api;

    ConsumerService(ConsumerConfig config) {
        this.config = config;
    }

    /**
     * CLI entry point for running the consumer directly by classname, without
     * the {@code --mode} flag that {@link Main} requires.
     *
     * @param args command-line arguments in {@code --key=value} form; see the
     *     project README's Consumer options
     * @throws Exception if the service fails to start
     */
    public static void main(String[] args) throws Exception {
        new ConsumerService(ConsumerConfig.from(Main.parseArgs(args))).startAndWait();
    }

    void startAndWait() throws Exception {
        running.set(true);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        api = new HttpApi(config.apiHost(), config.apiPort(), "consumer", stats, this::stop);
        api.start();

        Thread.startVirtualThread(this::consumeLoop);

        System.out.printf(
                "Consumer started. producer=%s:%d api=%s:%d reconnect=%dms%n",
                config.producerHost(), config.producerPort(), config.apiHost(), config.apiPort(), config.reconnectMs()
        );

        while (running.get()) {
            Thread.sleep(500);
        }
    }

    private void consumeLoop() {
        while (running.get()) {
            try (Socket socket = new Socket(config.producerHost(), config.producerPort());
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        DataItem item = DataItem.fromWireLine(line);
                        stats.record(item, Instant.now());
                    } catch (RuntimeException e) {
                        // A single corrupt line shouldn't tear down and reconnect the
                        // whole connection; skip it and keep reading.
                        System.err.println("Discarding malformed wire line: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    sleepBeforeReconnect();
                }
            }
        }
    }

    private void sleepBeforeReconnect() {
        try {
            Thread.sleep(config.reconnectMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (api != null) api.close();
    }
}
