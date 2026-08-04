# ADR-0001: Event-Driven Vehicle Creation and Simulation

## Status

Proposed

## Context

The current Roadrunner simulation engine creates vehicles and starts their simulation synchronously during the REST API lifecycle (`POST /api/vehicle/create-new`). This introduces several architectural and operational challenges:
1. **High API Latency:** Creating a vehicle requires calling the external Mapbox Directions API REST endpoint synchronously, which can block the request thread for several seconds.
2. **Bulk Latency (Criss-Cross):** Generating bulk scenarios (criss-cross) with multiple vehicles scales the synchronous blocking linearly, leading to web timeout risks.
3. **No Load Distribution:** The simulation runs entirely on the single backend pod instance that received the HTTP request. There is no way to load-balance simulations across multiple nodes.
4. **Poor Fault Tolerance:** If Mapbox is down or rate-limiting, the REST call fails immediately, preventing vehicle registration.

We need to decouple the HTTP ingestion of trip plans, route/directions retrieval, and simulation execution into an asynchronous, distributed event-driven pipeline.

## Decision Drivers

* **Minimize API Latency:** REST endpoints must respond immediately (within milliseconds).
* **High Availability & Fault Tolerance:** Failure in downstream dependencies (Redis, Mapbox) must be handled gracefully without dropping requests.
* **Distributed Load:** Vehicle simulations should be spread across available backend runner pods.
* **Maintain Decoupling:** Keep simulation domain logic decoupled from transportation infrastructure using the Ports and Adapters pattern.

## Considered Options

### Option 1: Multi-Stage Event-Driven Pipeline (Kafka + Redis Cache)
API writes the `TripPlan` to Redis and publishes a lightweight request to `vehicle.creation-request.v1`. 
* **Consumer 1 (Lookup Consumer):** Consumes the creation request, checks Redis for cached directions, fetches them from Mapbox API if missing (caching the result in Redis), and posts a `SimulationStart` message to `vehicle.simulation-start.v1`.
* **Consumer 2 (Simulation Consumer):** Consumes the simulation start message on any active node and registers the vehicle locally in `VehicleManager`'s active map to run simulation ticks.

### Option 2: Single-Stage Event-Driven Pipeline (API does Mapbox query)
API does the Mapbox Directions lookup synchronously, saves the Directions to Redis, and posts the creation message directly to a simulation topic.
* **Consumer:** Consumes the message and starts the simulation.
* **Trade-off:** This distributes the simulation load, but keeps the high-latency Mapbox Directions REST call in the API request thread, failing to resolve the API latency bottleneck.

## Decision

We will implement **Option 1: Multi-Stage Event-Driven Pipeline (Kafka + Redis Cache)**. 

To maximize resilience and align with the `error-handling-patterns` skill, we will implement the following error propagation and recovery strategies:

1. **Graceful Degradation (Redis Down):**
   * If Redis is unreachable during the Lookup Consumer execution, it will bypass cache checks and retrieve directions directly from Mapbox API.
   * If Redis is unreachable during simulation startup, the runner pod will fetch the plan details directly from the event payload/metadata.
2. **Retry with Exponential Backoff (Mapbox Down):**
   * If the Mapbox API is down or returns transient errors (429 Rate Limit, 5xx Server Error), the Lookup Consumer will retry using Spring Kafka's retry container with exponential backoff (e.g., initial interval 1s, backoff multiplier 2.0, max 5 attempts).
3. **Dead Letter Queues (DLQs):**
   * Unrecoverable errors (e.g., invalid JSON schema, missing coordinate waypoints) or repeated transient failures will be routed to DLQ topics: `vehicle.creation-request.dlq.v1` and `vehicle.simulation-start.dlq.v1`.
   * Trigger alerting/metrics updates when events enter the DLQ.

## Consequences

### Positive
- **Sub-millisecond REST Latency:** Vehicle creation returns immediately (`202 Accepted`) with a generated ID.
- **Resilient Pipeline:** External API errors are managed asynchronously via retries and DLQs. The system never drops requests.
- **Distributed Ingestion:** Bulk vehicle generation (Criss-Cross) is queued on Kafka, preventing thread exhaustion.
- **Load Balancing:** Simulator pods consume simulation starts from the topic, naturally distributing the active fleet.

### Negative
- **Eventual Consistency:** The frontend will initially receive a `PENDING_CREATION` status and must poll or wait for position updates.
- **System Complexity:** Introduces two new Kafka topics, two new consumer classes, and distributed retry configurations.

## References
- [Spring Kafka Retry and DLQ Documentation](https://docs.spring.io/spring-kafka/reference/html/#retry-config)
- [Error Handling Patterns - Graceful Degradation & Retry](file:///.agents/skills/error-handling-patterns/resources/implementation-playbook.md)
