# Producer / Consumer Service

A dependency-light Java 26 producer/consumer pair packaged into **one executable JAR**.

- Gradle 9.8.0
- JDK 26
- One JAR, two service modes: `producer` and `consumer`
- Producer generates random data at a configurable frequency.
- Each item contains a UUID, random numeric value, random payload, and `producedAt` timestamp.
- Producer sends items over a TCP newline-delimited wire stream; the statistics APIs return JSON.
- Consumer reconnects automatically if the producer is unavailable.
- Both services expose HTTP APIs for health, statistics, and graceful shutdown.
- Linux `systemd` unit files are included.

## Architecture

```text
             TCP :9000
+----------+  JSON lines  +----------+
| Producer | ------------> | Consumer |
+----------+               +----------+
   :8080                       :8081
    HTTP                        HTTP
```

The producer and consumer are separate JVM processes but use the same JAR.

## Build

Requires JDK 26.

```bash
./gradlew clean test jar
```

The executable JAR is:

```text
build/libs/producer-consumer-service-1.0.0.jar
```

Run:

```bash
java -jar build/libs/producer-consumer-service-1.0.0.jar --mode=producer --frequency-ms=500
java -jar build/libs/producer-consumer-service-1.0.0.jar --mode=consumer
```

The consumer may be started before the producer; it will retry the TCP connection.

`ProducerService` and `ConsumerService` each also have their own `main`, so either can be run
directly by classname from compiled classes instead of the jar, with no `--mode` flag needed
since the classname itself picks the mode:

```bash
java -cp build/classes/java/main com.example.datapipe.ProducerService --frequency-ms=500
java -cp build/classes/java/main com.example.datapipe.ConsumerService
```

## Producer options

Both ports are always bound to `127.0.0.1` — see [Security](#security).

```text
--mode=producer
--data-port=9000
--api-port=8080
--frequency-ms=1000
```

Example:

```bash
java -jar build/libs/producer-consumer-service-1.0.0.jar   --mode=producer   --frequency-ms=250   --data-port=9000   --api-port=8080

# equivalent, run directly by classname instead of the jar:
java -cp build/classes/java/main com.example.datapipe.ProducerService   --frequency-ms=250   --data-port=9000   --api-port=8080
```

## Consumer options

Both ports are always bound to `127.0.0.1` — see [Security](#security).

```text
--mode=consumer
--producer-port=9000
--api-port=8081
--reconnect-ms=1000
```

Example:

```bash
java -jar build/libs/producer-consumer-service-1.0.0.jar   --mode=consumer   --producer-port=9000   --api-port=8081

# equivalent, run directly by classname instead of the jar:
java -cp build/classes/java/main com.example.datapipe.ConsumerService   --producer-port=9000   --api-port=8081
```

## APIs

Producer statistics:

```bash
curl http://127.0.0.1:8080/api/stats
```

Consumer statistics:

```bash
curl http://127.0.0.1:8081/api/stats
```

Example response:

```json
{
  "role": "consumer",
  "total": 42,
  "lastTime": "2026-09-25T11:10:01.123Z",
  "lastItem": {
    "id": "4f0c...",
    "producedAt": "2026-09-25T11:10:01.100Z",
    "value": 123456,
    "payload": "..."
  }
}
```

Health:

```bash
curl http://127.0.0.1:8080/api/health
curl http://127.0.0.1:8081/api/health
```

Graceful shutdown:

```bash
curl -X POST http://127.0.0.1:8080/api/shutdown
curl -X POST http://127.0.0.1:8081/api/shutdown
```

## Security

Every listener — the producer's TCP data port, and both services' HTTP APIs — is hardcoded to bind to `127.0.0.1` and cannot be changed. `--bind-host`, `--api-host`, and `--producer-host` are not accepted; passing any of them fails fast with an error instead of silently being ignored. Only ports are configurable.

This is a deliberate tradeoff, not an oversight: none of the HTTP endpoints (including `/api/shutdown`) require authentication, and the TCP wire protocol has no auth or encryption, so loopback-only binding is the only thing standing between these services and anyone who can reach the port. If you need to reach them from another host, put a reverse proxy or SSH tunnel in front that adds its own authentication — do not fork this code to accept a non-loopback host.

## Service deployment

Linux `systemd` examples are in `deploy/systemd/`.

Install the JAR under `/opt/producer-consumer-service/`, then adapt the service files as needed:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now producer.service
sudo systemctl enable --now consumer.service
```

You can still shut either service down cleanly without killing its PID:

```bash
curl -X POST http://127.0.0.1:8080/api/shutdown
curl -X POST http://127.0.0.1:8081/api/shutdown
```

`systemd stop` also sends the normal JVM termination signal, which is handled by the shutdown hook.

## Design notes

This intentionally avoids a heavyweight application framework. The JDK HTTP server and virtual threads are sufficient for the small service/API surface, which keeps the single JAR simple and portable.

The TCP stream is intentionally simple rather than a durable messaging system. If the next requirement is guaranteed delivery, replay, persistence, multiple consumers, or back-pressure across machines, the transport should be replaced with Kafka, NATS, RabbitMQ, or another broker.
