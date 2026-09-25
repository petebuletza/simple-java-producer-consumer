package com.example.datapipe;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerConfigTest {

    @Test
    void appliesDefaultsWhenOptionsAreAbsent() {
        ConsumerConfig config = ConsumerConfig.from(Map.of());

        assertEquals(Main.LOOPBACK, config.producerHost());
        assertEquals(9000, config.producerPort());
        assertEquals(Main.LOOPBACK, config.apiHost());
        assertEquals(8081, config.apiPort());
        assertEquals(1000L, config.reconnectMs());
    }

    @Test
    void readsProvidedPortsAndReconnectInterval() {
        ConsumerConfig config = ConsumerConfig.from(Map.of(
                "producer-port", "9500",
                "api-port", "8091",
                "reconnect-ms", "250"
        ));

        assertEquals(9500, config.producerPort());
        assertEquals(8091, config.apiPort());
        assertEquals(250L, config.reconnectMs());
    }

    @Test
    void rejectsReconnectBelowOne() {
        assertThrows(IllegalArgumentException.class,
                () -> ConsumerConfig.from(Map.of("reconnect-ms", "0")));
    }

    @Test
    void rejectsProducerHostOption() {
        assertThrows(IllegalArgumentException.class,
                () -> ConsumerConfig.from(Map.of("producer-host", "0.0.0.0")));
    }

    @Test
    void rejectsApiHostOption() {
        assertThrows(IllegalArgumentException.class,
                () -> ConsumerConfig.from(Map.of("api-host", "0.0.0.0")));
    }
}
