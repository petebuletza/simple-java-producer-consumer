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

## Producer options

```text
--mode=producer
--data-port=9000
--api-host=127.0.0.1
--api-port=8080
--frequency-ms=1000
```

Example:

```bash
java -jar build/libs/producer-consumer-service-1.0.0.jar   --mode=producer   --frequency-ms=250   --data-port=9000   --api-port=8080
```

## Consumer options

```text
--mode=consumer
--producer-host=127.0.0.1
--producer-port=9000
--api-host=127.0.0.1
--api-port=8081
--reconnect-ms=1000
```

Example:

```bash
java -jar build/libs/producer-consumer-service-1.0.0.jar   --mode=consumer   --producer-host=127.0.0.1   --producer-port=9000   --api-port=8081
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

The shutdown endpoint is deliberately bound to loopback by default. If you expose the API beyond localhost, put authentication/network controls in front of it.

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
