package com.example.datapipe;

import java.util.Map;

record ConsumerConfig(
        String producerHost,
        int producerPort,
        String apiHost,
        int apiPort,
        long reconnectMs
) {
    static ConsumerConfig from(Map<String, String> o) {
        Main.rejectHostOption(o, "producer-host");
        Main.rejectHostOption(o, "api-host");

        long reconnect = Main.longOption(o, "reconnect-ms", 1000);
        if (reconnect < 1) throw new IllegalArgumentException("--reconnect-ms must be >= 1");
        return new ConsumerConfig(
                Main.LOOPBACK,
                Main.intOption(o, "producer-port", 9000),
                Main.LOOPBACK,
                Main.intOption(o, "api-port", 8081),
                reconnect
        );
    }
}
