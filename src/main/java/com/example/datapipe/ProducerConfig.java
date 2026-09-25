package com.example.datapipe;

import java.util.Map;

record ProducerConfig(
        String bindHost,
        int dataPort,
        String apiHost,
        int apiPort,
        long frequencyMs
) {
    static ProducerConfig from(Map<String, String> o) {
        long frequency = Main.longOption(o, "frequency-ms", 1000);
        if (frequency < 1) throw new IllegalArgumentException("--frequency-ms must be >= 1");
        return new ProducerConfig(
                o.getOrDefault("bind-host", "127.0.0.1"),
                Main.intOption(o, "data-port", 9000),
                o.getOrDefault("api-host", "127.0.0.1"),
                Main.intOption(o, "api-port", 8080),
                frequency
        );
    }
}
