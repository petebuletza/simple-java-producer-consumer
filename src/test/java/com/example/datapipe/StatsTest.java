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
}
