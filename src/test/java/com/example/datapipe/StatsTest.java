package com.example.datapipe;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class StatsTest {
    @Test
    void recordsCountLastItemAndLastTime() {
        Stats stats = new Stats();
        DataItem item = DataItem.random();
        Instant time = Instant.now();

        stats.record(item, time);

        assertEquals(1, stats.count());
        assertEquals(item, stats.lastItem());
        assertEquals(time, stats.lastTime());
    }

    @Test
    void countIncrementsAndLastItemAdvancesAcrossMultipleRecords() {
        Stats stats = new Stats();
        DataItem first = DataItem.random();
        DataItem second = DataItem.random();

        stats.record(first, first.producedAt());
        stats.record(second, second.producedAt());

        assertEquals(2, stats.count());
        assertEquals(second, stats.lastItem());
        assertEquals(second.producedAt(), stats.lastTime());
    }

    @Test
    void toJsonReflectsTheEmptyState() {
        Stats stats = new Stats();

        assertEquals("{\"role\":\"producer\",\"total\":0,\"lastTime\":null,\"lastItem\":null}",
                stats.toJson("producer"));
    }

    @Test
    void toJsonReflectsARecordedItem() {
        Stats stats = new Stats();
        DataItem item = DataItem.random();
        Instant time = Instant.now();
        stats.record(item, time);

        String expected = "{\"role\":\"consumer\",\"total\":1,\"lastTime\":\"" + time + "\",\"lastItem\":"
                + item.toJson() + "}";

        assertEquals(expected, stats.toJson("consumer"));
    }
}
