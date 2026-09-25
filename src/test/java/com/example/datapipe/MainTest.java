package com.example.datapipe;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @Test
    void parseArgsSplitsKeyValuePairs() {
        Map<String, String> options = Main.parseArgs(new String[] {"--mode=producer", "--data-port=9001"});

        assertEquals("producer", options.get("mode"));
        assertEquals("9001", options.get("data-port"));
    }

    @Test
    void parseArgsTreatsBareFlagAsTrue() {
        Map<String, String> options = Main.parseArgs(new String[] {"--verbose"});

        assertEquals("true", options.get("verbose"));
    }

    @Test
    void parseArgsKeepsEverythingAfterFirstEqualsAsValue() {
        Map<String, String> options = Main.parseArgs(new String[] {"--key=a=b=c"});

        assertEquals("a=b=c", options.get("key"));
    }

    @Test
    void parseArgsRejectsArgumentWithoutDoubleDashPrefix() {
        assertThrows(IllegalArgumentException.class, () -> Main.parseArgs(new String[] {"mode=producer"}));
    }

    @Test
    void requiredReturnsPresentValue() {
        Map<String, String> options = Map.of("mode", "producer");

        assertEquals("producer", Main.required(options, "mode"));
    }

    @Test
    void requiredRejectsMissingKey() {
        assertThrows(IllegalArgumentException.class, () -> Main.required(Map.of(), "mode"));
    }

    @Test
    void requiredRejectsBlankValue() {
        assertThrows(IllegalArgumentException.class, () -> Main.required(Map.of("mode", "  "), "mode"));
    }

    @Test
    void intOptionFallsBackToDefault() {
        assertEquals(9000, Main.intOption(Map.of(), "data-port", 9000));
    }

    @Test
    void intOptionParsesProvidedValue() {
        assertEquals(9500, Main.intOption(Map.of("data-port", "9500"), "data-port", 9000));
    }

    @Test
    void longOptionFallsBackToDefault() {
        assertEquals(1000L, Main.longOption(Map.of(), "frequency-ms", 1000));
    }

    @Test
    void longOptionParsesProvidedValue() {
        assertEquals(250L, Main.longOption(Map.of("frequency-ms", "250"), "frequency-ms", 1000));
    }

    @Test
    void durationMsWrapsLongOptionAsADuration() {
        assertEquals(Duration.ofMillis(250), Main.durationMs(Map.of("reconnect-ms", "250"), "reconnect-ms", 1000));
    }

    @Test
    void rejectHostOptionAllowsAnAbsentKey() {
        assertDoesNotThrow(() -> Main.rejectHostOption(Map.of(), "bind-host"));
    }

    @Test
    void rejectHostOptionRejectsAPresentKey() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Main.rejectHostOption(Map.of("bind-host", "0.0.0.0"), "bind-host"));

        assertTrue(ex.getMessage().contains("--bind-host"));
        assertTrue(ex.getMessage().contains(Main.LOOPBACK));
    }

    @Test
    void mainRejectsAMissingMode() {
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] {}));
    }

    @Test
    void mainRejectsAnUnknownMode() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Main.main(new String[] {"--mode=bogus"}));

        assertTrue(ex.getMessage().contains("producer"));
        assertTrue(ex.getMessage().contains("consumer"));
    }

    @Test
    void mainDispatchesToProducerMode() throws Exception {
        int apiPort = 19910;
        Thread thread = Thread.startVirtualThread(() -> {
            try {
                Main.main(new String[] {
                        "--mode=producer",
                        "--data-port=19909",
                        "--api-port=" + apiPort,
                        "--frequency-ms=50"
                });
            } catch (Exception ignored) {
            }
        });

        TestSupport.awaitHealthy(apiPort, Duration.ofSeconds(3));
        assertEquals("{\"status\":\"UP\",\"role\":\"producer\"}",
                TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/health"));

        assertEquals(202, TestSupport.httpStatus("http://127.0.0.1:" + apiPort + "/api/shutdown", "POST"));
        thread.join(3000);
        assertFalse(thread.isAlive(), "expected Main.main to return after shutdown");
    }

    @Test
    void mainDispatchesToConsumerMode() throws Exception {
        int producerPort = 19920;
        int apiPort = 19921;
        // Nothing needs to be listening on producerPort: the consumer only needs to start
        // and answer its own API here; connectivity to a producer is covered elsewhere.
        Thread thread = Thread.startVirtualThread(() -> {
            try {
                Main.main(new String[] {
                        "--mode=consumer",
                        "--producer-port=" + producerPort,
                        "--api-port=" + apiPort,
                        "--reconnect-ms=200"
                });
            } catch (Exception ignored) {
            }
        });

        TestSupport.awaitHealthy(apiPort, Duration.ofSeconds(3));
        assertEquals("{\"status\":\"UP\",\"role\":\"consumer\"}",
                TestSupport.httpGet("http://127.0.0.1:" + apiPort + "/api/health"));

        assertEquals(202, TestSupport.httpStatus("http://127.0.0.1:" + apiPort + "/api/shutdown", "POST"));
        thread.join(3000);
        assertFalse(thread.isAlive(), "expected Main.main to return after shutdown");
    }
}
