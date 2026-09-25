package com.example.datapipe;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProducerConfigTest {

    @Test
    void appliesDefaultsWhenOptionsAreAbsent() {
        ProducerConfig config = ProducerConfig.from(Map.of());

        assertEquals(Main.LOOPBACK, config.bindHost());
        assertEquals(9000, config.dataPort());
        assertEquals(Main.LOOPBACK, config.apiHost());
        assertEquals(8080, config.apiPort());
        assertEquals(1000L, config.frequencyMs());
    }

    @Test
    void readsProvidedPortsAndFrequency() {
        ProducerConfig config = ProducerConfig.from(Map.of(
                "data-port", "9500",
                "api-port", "8090",
                "frequency-ms", "250"
        ));

        assertEquals(9500, config.dataPort());
        assertEquals(8090, config.apiPort());
        assertEquals(250L, config.frequencyMs());
    }

    @Test
    void rejectsFrequencyBelowOne() {
        assertThrows(IllegalArgumentException.class,
                () -> ProducerConfig.from(Map.of("frequency-ms", "0")));
    }

    @Test
    void rejectsBindHostOption() {
        assertThrows(IllegalArgumentException.class,
                () -> ProducerConfig.from(Map.of("bind-host", "0.0.0.0")));
    }

    @Test
    void rejectsApiHostOption() {
        assertThrows(IllegalArgumentException.class,
                () -> ProducerConfig.from(Map.of("api-host", "0.0.0.0")));
    }
}
