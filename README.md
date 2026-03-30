# Webhook Notifier System

A high-performance, fault-tolerant, and fair webhook delivery system built with Java 21 and Spring Boot. Designed for horizontal scalability, it handles large-scale event processing with guaranteed delivery and comprehensive observability.

## Key Features

- **Java 21 Virtual Threads**: High-concurrency event dispatching with minimal memory overhead.
- **Reliable Retries**: Ensuring persistence across worker failures with automatically configured retry topics by Spring Kafka with exponential backoff. Dead-letter queue for failed events.
- **Horizontal Scalability**: Stateless workers that can be scaled independently to handle varying loads.
- **Observability**: Real-time monitoring with Prometheus and Grafana dashboards for throughput, latency, and DLQ health.

## Technology Stack

- **Core**: Java 21, Spring Boot 3.4+
- **Messaging**: Apache Kafka (Event Ingestion & Internal Buffering)
- **Caching & Coordination**: Redis (Redisson) for fairness and lock management
- **Database**: PostgreSQL 15 (Configuration storage & state)
- **Monitoring**: Prometheus, Grafana, Micrometer
- **Infrastructure**: Docker & Docker Compose
- **Build Tool**: Gradle (with custom convention plugins)

## Architecture Components

- **`publisher`**: Internal service to simulate event production at scale.
- **`webhook-worker`**: Consumes events from Kafka, manages delivery lifecycle.
- **`receiver`**: Mock webhook endpoint for testing various failure scenarios (e.g., timeouts, 5xx errors).
- **`Infrastructure`**: Kafka (Zookeeper), Postgres, Redis, Prometheus, Grafana, and Kafka Exporter.
---

## Getting Started

### Prerequisites

- Docker Desktop (or Colima)
- Java 21+ (Optional, if running outside Docker)
- Gradle 8.12+ (Optional)

### Automated Setup

We provide a comprehensive setup script that handles building, infrastructure provisioning, and initial configuration:

```bash
# Clone the repository
git clone <repository-url>
cd webhook-notifier-system

# Run the setup script
./scripts/setup.sh
```

**The `setup.sh` script performs the following:**
1. Builds Java modules using Gradle (skipping tests).
2. Starts infrastructure containers (Kafka, Postgres, Redis, Prometheus, Grafana).
3. Creates required Kafka topics with 2 partitions (due to resources limitation) (e.g., `events`).
4. Scales `webhook-worker` instances to 2 for high availability.
5. Starts the `publisher` to begin generating test events.

### Manual Commands

If you prefer manual control:

```bash
# Build
./gradlew build

# Start Infrastructure
docker-compose up -d

# Check Logs
docker-compose logs -f webhook-worker
```

---

## Monitoring

The system includes pre-configured Grafana dashboards to monitor performance:

1.  Open [Grafana](http://localhost:3000) (Default Credentials: `admin`/`admin`).
2.  Go to **Dashboards** > **Webhook Notifier Dashboard**.
3.  Monitor metrics such as:
    -   **Worker Throughput**: Events processed per second.
    -   **Delivery Latency**: Time from Kafka ingestion to successful HTTP dispatch.
    -   **Error Rates**: Success vs. Failure rates and DLQ status.

---

## Development

### Local Build & Test

```bash
./gradlew clean build
./gradlew test
```

#### Webhook worker
- Adjust number of worker via scripting above (Automatically create consumer group based on HOSTNAME, but due to resource limitation, we only able to create 1 broker, so partitions and replicas are limited as well)

#### Publisher
- Adjust publish rate via environment variable `PUBLISH_RATE_PER_SEC`
- Enable/disable whale accounts via environment variable `WHALE_ENABLED`

#### Receiver
- Adjust failure rate of receiver via environment variable `FAILURE_RATE`

### Adding New Features

Follow the convention plugins in `buildSrc` for maintaining consistent Spring Boot and coding styles across modules.

## NOTE
- For POC purpose, we use local docker-compose to run the system.
- Database schema is a bit different with the actual webhook/event payload from offcial website
