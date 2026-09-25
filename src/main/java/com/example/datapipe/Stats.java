package com.example.datapipe;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class Stats {
    private final AtomicLong count = new AtomicLong();
    private final AtomicReference<DataItem> lastItem = new AtomicReference<>();
    private final AtomicReference<Instant> lastTime = new AtomicReference<>();

    void record(DataItem item, Instant eventTime) {
        lastItem.set(item);
        lastTime.set(eventTime);
        count.incrementAndGet();
    }

    long count() {
        return count.get();
    }

    DataItem lastItem() {
        return lastItem.get();
    }

    Instant lastTime() {
        return lastTime.get();
    }

    String toJson(String role) {
        DataItem item = lastItem();
        StringBuilder json = new StringBuilder();
        json.append("{")
            .append("\"role\":\"").append(role).append("\",")
            .append("\"total\":").append(count()).append(",")
            .append("\"lastTime\":");
        if (lastTime() == null) {
            json.append("null");
        } else {
            json.append("\"").append(lastTime()).append("\"");
        }
        json.append(",\"lastItem\":");
        if (item == null) {
            json.append("null");
        } else {
            json.append(item.toJson());
        }
        return json.append("}").toString();
    }
}
