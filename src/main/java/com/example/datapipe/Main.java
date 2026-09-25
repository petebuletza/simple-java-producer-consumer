package com.example.datapipe;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Command-line entry point that dispatches to {@link ProducerService} or
 * {@link ConsumerService} based on the required {@code --mode} option.
 *
 * <p>{@code ProducerService} and {@code ConsumerService} each also expose their
 * own {@code main} method for running a specific mode directly by classname,
 * without the {@code --mode} flag.
 */
public final class Main {
    private Main() {}

    static final String LOOPBACK = "127.0.0.1";

    /**
     * Parses {@code args}, then starts the producer or consumer service named
     * by the required {@code --mode} option.
     *
     * @param args command-line arguments in {@code --key=value} form
     * @throws Exception if the selected service fails to start
     */
    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        String mode = required(options, "mode");

        switch (mode.toLowerCase(Locale.ROOT)) {
            case "producer" -> new ProducerService(ProducerConfig.from(options)).startAndWait();
            case "consumer" -> new ConsumerService(ConsumerConfig.from(options)).startAndWait();
            default -> throw new IllegalArgumentException("mode must be 'producer' or 'consumer'");
        }
    }

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Invalid argument: " + arg);
            }
            int equals = arg.indexOf('=');
            if (equals < 0) {
                options.put(arg.substring(2), "true");
            } else {
                options.put(arg.substring(2, equals), arg.substring(equals + 1));
            }
        }
        return options;
    }

    static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + key + "=...");
        }
        return value;
    }

    static int intOption(Map<String, String> options, String key, int defaultValue) {
        return Integer.parseInt(options.getOrDefault(key, Integer.toString(defaultValue)));
    }

    static long longOption(Map<String, String> options, String key, long defaultValue) {
        return Long.parseLong(options.getOrDefault(key, Long.toString(defaultValue)));
    }

    static Duration durationMs(Map<String, String> options, String key, long defaultValue) {
        return Duration.ofMillis(longOption(options, key, defaultValue));
    }

    static void rejectHostOption(Map<String, String> options, String key) {
        if (options.containsKey(key)) {
            throw new IllegalArgumentException(
                    "--" + key + " is not supported: this service only binds to " + LOOPBACK
                            + " for security. Only ports are configurable.");
        }
    }
}
