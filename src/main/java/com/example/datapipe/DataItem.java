package com.example.datapipe;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * A single unit of data flowing from the producer to the consumer: a random
 * value and payload, stamped with the time it was produced.
 *
 * <p>On the wire, an item is one newline-terminated line of four
 * tab-separated fields: {@code id<TAB>producedAt<TAB>value<TAB>base64(payload)}.
 * See {@link #toWireLine()} and {@link #fromWireLine(String)}.
 *
 * @param id         a random identifier unique to this item
 * @param producedAt the instant the item was produced
 * @param value      a random value in {@code [0, 1_000_000)}
 * @param payload    a random string payload
 */
public record DataItem(UUID id, Instant producedAt, long value, String payload) {
    /**
     * Creates a new item with a random id, value, and payload, timestamped now.
     *
     * @return the new item
     */
    public static DataItem random() {
        return new DataItem(
                UUID.randomUUID(),
                Instant.now(),
                java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 1_000_000),
                UUID.randomUUID().toString()
        );
    }

    /**
     * Encodes this item as one line of the TCP wire format: four tab-separated
     * fields, with the payload base64-encoded so it can't contain a literal tab
     * or newline.
     *
     * @return the encoded line, without a trailing newline
     */
    public String toWireLine() {
        return String.join("\t",
                id.toString(),
                producedAt.toString(),
                Long.toString(value),
                Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Parses one line previously produced by {@link #toWireLine()}.
     *
     * @param line a wire-format line
     * @return the decoded item
     * @throws IllegalArgumentException if {@code line} isn't exactly four
     *     tab-separated fields, or a field fails to parse
     */
    public static DataItem fromWireLine(String line) {
        String[] parts = line.split("\\t", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Invalid data item");
        return new DataItem(
                UUID.fromString(parts[0]),
                Instant.parse(parts[1]),
                Long.parseLong(parts[2]),
                new String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8));
    }

    /**
     * Renders this item as the JSON object used by the {@code /api/stats} response.
     *
     * @return the item as a JSON object string
     */
    public String toJson() {
        return "{" +
                "\"id\":" + Json.quote(id.toString()) + "," +
                "\"producedAt\":" + Json.quote(producedAt.toString()) + "," +
                "\"value\":" + value + "," +
                "\"payload\":" + Json.quote(payload) +
                "}";
    }
}
