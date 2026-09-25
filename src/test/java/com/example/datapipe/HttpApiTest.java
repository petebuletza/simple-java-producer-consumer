package com.example.datapipe;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class HttpApiTest {

    @Test
    void healthReturnsStatusAndRole() throws Exception {
        try (HttpApi api = new HttpApi("127.0.0.1", 0, "producer", new Stats(), () -> {})) {
            api.start();

            String body = TestSupport.httpGet("http://127.0.0.1:" + api.port() + "/api/health");

            assertEquals("{\"status\":\"UP\",\"role\":\"producer\"}", body);
        }
    }

    @Test
    void statsReflectsRecordedItems() throws Exception {
        Stats stats = new Stats();
        try (HttpApi api = new HttpApi("127.0.0.1", 0, "consumer", stats, () -> {})) {
            api.start();
            String emptyBody = TestSupport.httpGet("http://127.0.0.1:" + api.port() + "/api/stats");
            assertTrue(emptyBody.contains("\"total\":0"));
            assertTrue(emptyBody.contains("\"lastItem\":null"));

            DataItem item = DataItem.random();
            stats.record(item, item.producedAt());

            String body = TestSupport.httpGet("http://127.0.0.1:" + api.port() + "/api/stats");
            assertTrue(body.contains("\"total\":1"));
            assertTrue(body.contains(item.id().toString()));
        }
    }

    @Test
    void statsRejectsNonGetMethod() throws Exception {
        try (HttpApi api = new HttpApi("127.0.0.1", 0, "producer", new Stats(), () -> {})) {
            api.start();

            int status = TestSupport.httpStatus("http://127.0.0.1:" + api.port() + "/api/stats", "POST");

            assertEquals(405, status);
        }
    }

    @Test
    void shutdownRejectsNonPostMethod() throws Exception {
        AtomicBoolean shutdownCalled = new AtomicBoolean();
        try (HttpApi api = new HttpApi("127.0.0.1", 0, "producer", new Stats(), () -> shutdownCalled.set(true))) {
            api.start();

            int status = TestSupport.httpStatus("http://127.0.0.1:" + api.port() + "/api/shutdown", "GET");

            assertEquals(405, status);
            assertFalse(shutdownCalled.get());
        }
    }

    @Test
    void shutdownAcceptsPostAndInvokesTheShutdownAction() throws Exception {
        AtomicBoolean shutdownCalled = new AtomicBoolean();
        try (HttpApi api = new HttpApi("127.0.0.1", 0, "producer", new Stats(), () -> shutdownCalled.set(true))) {
            api.start();

            int status = TestSupport.httpStatus("http://127.0.0.1:" + api.port() + "/api/shutdown", "POST");

            assertEquals(202, status);
            TestSupport.awaitTrue(Duration.ofSeconds(2), shutdownCalled::get);
        }
    }
}
