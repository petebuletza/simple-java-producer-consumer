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
        Main.rejectHostOption(o, "bind-host");
        Main.rejectHostOption(o, "api-host");

        long frequency = Main.longOption(o, "frequency-ms", 1000);
        if (frequency < 1) throw new IllegalArgumentException("--frequency-ms must be >= 1");
        return new ProducerConfig(
                Main.LOOPBACK,
                Main.intOption(o, "data-port", 9000),
                Main.LOOPBACK,
                Main.intOption(o, "api-port", 8080),
                frequency
        );
    }
}
