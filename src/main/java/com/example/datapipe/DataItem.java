package com.example.datapipe;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record DataItem(UUID id, Instant producedAt, long value, String payload) {
    public static DataItem random() {
        return new DataItem(
                UUID.randomUUID(),
                Instant.now(),
                java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 1_000_000),
                UUID.randomUUID().toString()
        );
    }

    // TCP wire format: id<TAB>producedAt<TAB>value<TAB>base64(payload)
    public String toWireLine() {
        return String.join("\t",
                id.toString(),
                producedAt.toString(),
                Long.toString(value),
                Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)));
    }

    public static DataItem fromWireLine(String line) {
        String[] parts = line.split("\\t", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Invalid data item");
        return new DataItem(
                UUID.fromString(parts[0]),
                Instant.parse(parts[1]),
                Long.parseLong(parts[2]),
                new String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8));
    }

    public String toJson() {
        return "{" +
                "\"id\":" + Json.quote(id.toString()) + "," +
                "\"producedAt\":" + Json.quote(producedAt.toString()) + "," +
                "\"value\":" + value + "," +
                "\"payload\":" + Json.quote(payload) +
                "}";
    }
}
