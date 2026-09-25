package com.example.datapipe;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
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
                Client client = new Client(socket);
                clients.add(client);
                Thread.startVirtualThread(() -> client.run());
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
            if (!client.send(line)) {
                clients.remove(client);
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
        private final Socket socket;
        private BufferedWriter writer;

        Client(Socket socket) {
            this.socket = socket;
            try {
                writer = new BufferedWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                close();
            }
        }

        synchronized boolean send(String line) {
            if (writer == null) return false;
            try {
                writer.write(line);
                writer.newLine();
                writer.flush();
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        void run() {
            try {
                socket.getInputStream().readAllBytes();
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }

        void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
