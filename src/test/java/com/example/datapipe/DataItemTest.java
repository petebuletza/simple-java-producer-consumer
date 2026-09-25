package com.example.datapipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataItemTest {
    @Test
    void randomItemRoundTripsThroughWireFormat() {
        DataItem original = DataItem.random();
        DataItem decoded = DataItem.fromWireLine(original.toWireLine());

        assertEquals(original.id(), decoded.id());
        assertEquals(original.producedAt(), decoded.producedAt());
        assertEquals(original.value(), decoded.value());
        assertEquals(original.payload(), decoded.payload());
    }

    @Test
    void producedTimestampIsPresent() {
        DataItem item = DataItem.random();
        assertNotNull(item.producedAt());
        assertTrue(item.toJson().contains("\"producedAt\":\"" + item.producedAt()));
    }
}
