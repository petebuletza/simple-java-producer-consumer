package com.example.datapipe;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ProducerService {
    private final ProducerConfig config;
    private final Stats stats = new Stats();
    private final Set<Client> clients = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private ServerSocket serverSocket;
    private HttpApi api;

    ProducerService(ProducerConfig config) {
        this.config = config;
    }

    void startAndWait() throws Exception {
        running.set(true);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        serverSocket = new ServerSocket();
        serverSocket.bind(new java.net.InetSocketAddress(config.bindHost(), config.dataPort()));
        api = new HttpApi(config.apiHost(), config.apiPort(), "producer", stats, this::stop);
        api.start();

        Thread.startVirtualThread(this::acceptLoop);
        scheduler.scheduleAtFixedRate(this::produce, 0, config.frequencyMs(), TimeUnit.MILLISECONDS);

        System.out.printf(
                "Producer started. data=%s:%d api=%s:%d frequency=%dms%n",
                config.bindHost(), config.dataPort(), config.apiHost(), config.apiPort(), config.frequencyMs()
        );

        while (running.get()) {
            Thread.sleep(500);
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                Client client = new Client(socket, clients);
                clients.add(client);
                client.start();
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void produce() {
        if (!running.get()) return;
        DataItem item = DataItem.random();
        stats.record(item, item.producedAt());
        String line = item.toWireLine();
        for (Client client : clients) {
            // Non-blocking: a client whose outbox is full is too slow to keep up and gets
            // dropped here instead of blocking this shared scheduler thread (and therefore
            // every other client) on a stalled socket write.
            if (!client.offer(line)) {
                client.close();
            }
        }
    }

    void stop() {
        if (!running.compareAndSet(true, false)) return;
        scheduler.shutdownNow();
        clients.forEach(Client::close);
        clients.clear();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        if (api != null) api.close();
    }

    private static final class Client {
        // Bounds how far a client may lag behind before it's treated as unresponsive
        // and disconnected, rather than letting its backlog grow without limit.
        private static final int OUTBOX_CAPACITY = 1024;

        private final Socket socket;
        private final Set<Client> registry;
        private final BlockingQueue<String> outbox = new LinkedBlockingQueue<>(OUTBOX_CAPACITY);
        private final AtomicBoolean closed = new AtomicBoolean();

        Client(Socket socket, Set<Client> registry) {
            this.socket = socket;
            this.registry = registry;
        }

        void start() {
            Thread.startVirtualThread(this::writeLoop);
            Thread.startVirtualThread(this::readLoop);
        }

        boolean offer(String line) {
            return outbox.offer(line);
        }

        private void writeLoop() {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                while (!closed.get()) {
                    String line = outbox.poll(500, TimeUnit.MILLISECONDS);
                    if (line == null) continue;
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally {
                close();
            }
        }

        private void readLoop() {
            try {
                socket.getInputStream().readAllBytes();
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }

        void close() {
            if (closed.compareAndSet(false, true)) {
                registry.remove(this);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}
