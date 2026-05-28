# Roadrunner
Roadrunner is a high-performance, distributed vehicle simulation engine designed to model real-time movement across complex topologies. It serves as the backend simulation provider for the [Roadrunner Viewer](https://github.com/SteveTarter/roadrunner-viewer).

# Overview

Roadrunner simulates vehicle lifecycles, from creation to arrival, by calculating precise geographic movements based on real-world routing data. The system is built using a **Hexagonal Architecture (Ports and Adapters)**, allowing it to remain infrastructure-agnostic while delivering high-velocity telemetry.  The system currently uses Kafka to post position updates to a topic.  A previous implementation utilized Redis.

## Key Features

- **Real-World Routing:** Integrates with the **Mapbox Directions API** to retrieve accurate route geometry and posted speed limits for simulation legs.

- **Geospatial Fidelity:** Performs advanced spatial calculations, including coordinate transformations between **WGS84** (GPS) and **UTM** (Planar) projections to maintain accuracy across long-distance travel.

- **Event-Driven Telemetry:** Vehicle positions, status changes, and lifecycle events are broadcast to **Apache Kafka** (e.g. `vehicle.position.v1`), enabling low-latency, asynchronous state updates.

- **Distributed State Management:** Implements an "Event Sourcing" approach where simulation state is maintained in-memory via Kafka stream consumption, ensuring rapid recovery and horizontal scalability.

- **RESTful Control Plane:** Simple API for vehicle creation (by address or coordinate), bulk simulation generation (e.g., "Criss-Cross" patterns), and simulation control.

## Architecture

The project is designed for high concurrency and resilience:

- **The Runner:** A dedicated simulation loop that manages physics and topology calculations with sub-millisecond jitter tracking.

- **The Store:** Pluggable persistence adapters (In-Memory/Kafka or formerly, Redis) that satisfy the `VehicleStateStore` port.

- **The Messaging Layer:** Conditional autoconfiguration for Kafka-based event publishing and consumption.

## Pluggable Infrastructure

Due to the use of the **Ports and Adapters** pattern, the messaging and persistence layers are entirely decoupled from the core simulation logic. While Kafka is the current standard, the system is designed to support:

- **Message Brokers:** Integration with **RabbitMQ**, **NATS**, or **MQTT** for varying throughput requirements.

- **Cloud Streams:** Native support for **Amazon Kinesis** or **Google Pub/Sub**.

- **Distributed Caching:** Leveraging **Hazelcast** or **Ignite** for high-availability state storage across clusters.

## Quick Start

The project requires a running Kafka cluster for messaging and Redis for caching route geometry.
  
Configure application properties as specified below.

To run, launch via Maven or as a Spring Boot executable:

```bash
mvn spring-boot:run
```

## Application Properties Configuration

The following variables need to be set in the `application.properties` file for the application to start:

- `spring.config.import`  
  Specifies external properties files to import. Optional file for sensitive configuration.  
  Example: `optional:secrets.properties`

- `spring.application.name`
  Application name
  Example: `roadrunner`

- `spring.data.rest.base-path`  
  Base path for REST API.  
  Example: `/`

- `spring.profiles.active`
  Active Spring profile.  Should be 'eks' for AWS builds.  
  Example: `eks`

- `mapbox.api.url`  
  URL to the Mapbox API.  
  Example: `https://api.mapbox.com/`

- `management.server.port`
  Management endpoint server point.
  Example: 8081

- `management.endpoints.web.exposure.include`  
  Management endpoints exposed via HTTP.  
  Example: `health,info`

- `management.endpoint.health.show-details`  
  Show health endpoint details.  
  Example: `always`

- `com.tarterware.redis.host`  
  Host address of the Redis server.  
  Example: `127.0.0.1`

- `com.tarterware.redis.port`  
  Port of the Redis server.  
  Example: `6379`

- `com.tarterware.roadrunner.vehicle-update-period`  
  How often each vehicle should be updated.  
  Example: `250ms`

- `com.tarterware.roadrunner.jitter-stat-capacity`  
  Number of reading to consider in jitter statistics. 
  Example: `200`

- `com.tarterware.roadrunner.playback-cache-timeout'
  Time after access that playback cache entries are removed.
  Example: `1m`

- `com.tarterware.roadrunner.playback-cache-size`
  Maximum number of entries in the playback cache.
  Example: `100`

- `com.tarterware.roadrunner.usage-limits.default-daily-vehicle-starts`
  Maximum number of vehicles "creator" users can start each day.
  Example: `30`

- `com.tarterware.roadrunner.usage-limits.redis-key-prefix`
  Prefix for the Redis key used to regulate per-user vehicle quotas.
  Example: `roadrunner:usage:vehicle-starts`

- `com.tarterware.roadrunner.usage-limits.counter-ttl-hours`
  Time period between vehicle quota resets.
  Example: `24`

- `com.tarterware.roadrunner.cors.allowed-origins`
  Allowed CORS origins.
  Example: `http://localhost:3000,https://roadrunner-view.tarterware.info,https://roadrunner-view.tarterware.com`

- `roadrunner.messaging.kafka.enabled`  
  Enable Vehicle update messages using Kafka.  
  Example: true

- `prometheus.secret.namespace`  
  Namespace of secret for prometheus to obtain Bearer token.  
  Example: `roadrunner`

- `prometheus.secret.name`  
  Namespace of secret for prometheus to obtain Bearer token.  
  Example: `prometheus-token-secret`
 
- `logging.level.org.apache.kafka.clients.consumer.internals.LegacyKafkaConsumer`
  Needed to silence log-poluting Kafka class.
  Example: `WARN`

- `logging.level.org.apache.kafka.clients.consumer.internals.SubscriptionState`
  Needed to silence log-poluting Kafka class.
  Example: `WARN`

- `logging.level.org.apache.kafka.clients.consumer.internals.ClassicKafkaConsumer
  Needed to silence log-poluting Kafka class.
  Example: `WARN`

- `spring.kafka.bootstrap-servers`  
  Location of Kafka bootstrap servers.  
  Example: localhost:9094

- `spring.kafka.properties.socket.connection.setup.timeout.ms`
  The maximum amount of time the client will wait for the socket connection to be established.
  Example: `30000`

- `spring.kafka.properties.request.timeout.ms`
  Maximum amount of time the client will wait for the response of a request.
  Example: `60000`

- `spring.kafka.listener.concurrency`
  Number of consumer threads to use in the Kafka listener container.
  Example: `3`

- `spring.kafka.producer.acks`  
  Kafka Producer messages to acknowledge.  
  Example: `all`

- `spring.kafka.producer.properties.enable.idempotence`  
  Idempotence enabled for Kafka.
  Example: `true`

- `spring.kafka.producer.properties.linger.ms`
  The maximum wait time before sending a batch.
  Example: `10`

- `spring.kafka.producer.properties.batch.size`
  The producer attempts to batch multiple records destined for the same partition into a single request until this size limit is reached.
  Example: `65536`

- `spring.kafka.producer.properties.compression.type`
  The compression type for all data generated by the producer. 
  Example: `lz4`

- `spring.kafka.producer.key-serializer`  
  Class for Kafka Producer to use for key serialization.  
  Example: org.apache.kafka.common.serialization.StringSerializer

- `spring.kafka.producer.value-serializer`  
  Class for Kafka Producer to use for value serialization.  
  Example: org.springframework.kafka.support.serializer.JsonSerializer

- `spring.kafka.consumer.auto-offset-reset`
  Determines how consumers behave when there are no initial offsets
  Example: `earliest`

- `spring.kafka.consumer.max-poll-records`
  The maximum number of records a consumer fetches in a single po
  Example: `200`

- `spring.kafka.consumer.key-deserializer`
  Class for Kafka Consumer to use for key deserialization.
  Example: `org.apache.kafka.common.serialization.StringDeserializer`

- `spring.kafka.consumer.value-deserializer`
  Class for Kafka Consumer to use for value deserialization.  
  Example: `org.springframework.kafka.support.serializer.ErrorHandlingDeserializer`

- `spring.kafka.consumer.properties.spring.deserializer.value.delegate.class`
  Class for Kafka Consumer to use when ErrorHandlingDeserializer found no error.
  Example: `org.springframework.kafka.support.serializer.JsonDeserializer`

- `spring.kafka.consumer.properties.spring.json.trusted.packages`  
  Packages that Kafka can trust.  
  Example: `com.tarterware.roadrunner.*`

- `spring.kafka.consumer.properties.spring.json.use.type.headers`  
  Kafka Consumer should use type headers with JSON.  
  Example: `true`

- `spring.kafka.consumer.properties.spring.json.value.default.type`  
  Kafka Consumer JSON value default type.  
  Example: `com.tarterware.roadrunner.messaging.VehiclePositionEvent`

- `spring.kafka.consumer.properties.heartbeat.interval.ms`
  Defines the expected time between heartbeats to the consumer coordinator.
  Example: `3000`

- `spring.kafka.consumer.properties.session.timeout.ms`
  How long a Kafka broker waits for a heartbeat from a consumer before marking it as "dead" and triggering a group rebalance
  Example: `10000`

- `com.tarterware.roadrunner.kafka.topic.vehicle-position`  
  Kafka topic for a Roadrunner VehiclePosition.  
  Example: `vehicle.position.v1`

## 'Secret' Properties Configuration

The following variables should be set in a 'secrets.properties' file that is peer to the application properties.  Note that this is not in the repo by design.

- `mapbox.api.key`  
  API key for the MapBox mapping provider  
  Example: `pk.bleggity-blah`
  
- `com.tarterware.redis.password`  
  Password to access redis outside AWS  
  Example: `nunyabiznezazzole`

- `spring.security.oauth2.resourceserver.jwt.issuer-uri`  
  OAuth2 JWT issuer  
  Example: `https://dev-PROJECTID.us.auth0.com/`

- `cognito.app-client-id`  
  Cognito Client ID  
  Example: `20ishlongalphanumeric`

- `com.tarterware.roadrunner.aws.cognito.user-pool-id`
  Cognito User Pool ID
  Example: `us-east-1_obfuscated`

------------------------------------------------------------------------------------------
## API

#### Create new vehicle
*This endpoint requires that the invoking user is a member of the "creator" group.*
<details>
 <summary><code>POST</code> <code><b>/api/vehicle/create-new</b></code> <code>(creates a new vehicle)</code></summary>

##### Parameters

> | name      |  type       | data type               | description                                                           |
> |-----------|-------------|-------------------------|-----------------------------------------------------------------------|
> | `None`    |  `required` | `object (JSON or YAML)` | `N/A`  |

##### Responses

> | http code     | content-type                      | response                                                            |
> |---------------|-----------------------------------|---------------------------------------------------------------------|
> | `200`         | `application/json`        | `Vehicle created successfully`                                      |
> | `403`         | `application/json`        | `{"code":"403","message":"User must be member of the \"creator\" group.", "status": 403, "timestamp": "2026-05-28T20:14:42.395113264Z"}`|

##### Example cURL

> ```javascript
>  curl -X POST http://localhost:8080/api/vehicle/create-new -H "Content-Type: application/json" -d "{ \"listStops\": [ { \"address1\": \"10201 White Settlement Rd\", \"city\": \"Fort Worth\", \"state\": \"TX\", \"zipCode\": \"76108\" }, { \"address1\": \"12301 Camp Bowie W Blvd\", \"city\": \"Aledo\", \"state\": \"TX\", \"zipCode\": \"76008\" } ] }"```
</details>

#### Create crisscross pattern of vehicles and routes
*This endpoint requires that the invoking user is a member of the "creator" group.*

<details>
 <summary><code>POST</code> <code><b>/api/vehicle/create-crisscross</b></code> <code>(creates a new "criss cross" set of routes and vehicles)</code></summary>

##### Parameters

> | name      |  type     | data type               | description                                                           |
> |-----------|-----------|-------------------------|-----------------------------------------------------------------------|
> | None      |  required | object (JSON or YAML)   |`N/A`  |

##### Responses

> | http code     | content-type                      | response                                                            |
> |---------------|-----------------------------------|---------------------------------------------------------------------|
> | `201`         | `text/plain;charset=UTF-8`        | `Configuration created successfully`                                |
> | `400`         | `application/json`                | `{"code":"400","message":"Bad Request"}`                            |
> | `405`         | `text/html;charset=utf-8`         | `None`                                                              |

##### Example cURL

> ```javascript
>  curl -X POST http://localhost:8080/api/vehicle/create-crisscross -H "Content-Type: application/json" -d "{ \"degLatitude\": 32.7507, "degLongitude": -97.3286, "kmRadius": 50.0, "vehicleCount": 36 }"
</details>

#### Get vehicle state for a given vehicle ID

<details>
 <summary><code>GET</code> <code><b>/api/vehicle/get-vehicle-state/</b>{vehicleId}</code> <code>(gets vehicle state for the given vehicle)</code></summary>

##### Parameters

> | name      |  type     | data type               | description                                                           |
> |-----------|-----------|-------------------------|-----------------------------------------------------------------------|
> | vehicleId |  required | string                  | ID of vehicle to retrieve |

##### Responses

> | http code     | content-type                      | response                                                            |
> |---------------|-----------------------------------|---------------------------------------------------------------------|
> | `201`         | `text/plain;charset=UTF-8`        | `Configuration created successfully`                                |
> | `404`         | `text/html;charset=utf-8`         | `None`                                                                |

##### Example cURL

> ```javascript
>  curl -X GET http://localhost:8080/api/vehicle/get-vehicle-state/778afa04

</details>

#### Get vehicle directions for a given vehicle ID

<details>
 <summary><code>GET</code> <code><b>/api/vehicle/get-vehicle-directions/</b>{vehicleId}</code> <code>(gets Mapbox vehicle directions for the given vehicle)</code></summary>

##### Parameters

> | name      |  type     | data type               | description                                                           |
> |-----------|-----------|-------------------------|-----------------------------------------------------------------------|
> | vehicleId |  required | string                  | ID of vehicle direction to retrieve |

##### Responses

> | http code     | content-type                      | response                                                            |
> |---------------|-----------------------------------|---------------------------------------------------------------------|
> | `201`         | `text/plain;charset=UTF-8`        | `Configuration created successfully`                                |
> | `404`         | `text/html;charset=utf-8`         | `None`                                                                |

##### Example cURL

> ```javascript
>  curl -X GET http://localhost:8080/api/vehicle/get-vehicle-state/778afa04 -H "Content-Type: application/json"

</details>


#### Delete vehicle with the given vehicle ID

<details>
 <summary><code>DELETE</code> <code><b>/api/vehicle/delete/</b>{vehicleId}</code> <code>(deletes the specified vehicle)</code></summary>

##### Parameters

> | name      |  type     | data type               | description                                                           |
> |-----------|-----------|-------------------------|-----------------------------------------------------------------------|
> | vehicleId |  required | string                  | ID of vehicle to delete |

##### Responses

> | http code     | content-type        | response                                                                                                    |
> |---------------|---------------------|-------------------------------------------------------------------------------------------------------------|
> | `204`         | `None`              | `None`                                                                                                      |
> | `403`         | `application/json`  | `{"message": "vehicleID 752dd9f6 not found!", "status": 403, "timestamp": "2026-05-28T20:14:42.395113264Z"} |
> | `404`         | `None`              | `None`                                                                                                      |

##### Example cURL

> ```javascript
>  curl -X DELETE http://localhost:8080/api/vehicle/delete/778afa04

</details>

#### Get vehicle states for all vehicles

<details>
 <summary><code>GET</code> <code><b>/api/vehicle/get-all-vehicle-states/</b></code> <code>(gets vehicle state for all vehicles)</code></summary>

##### Parameters

> | name       |  type      | data type               | description                   | default                             |
> |------------|------------|-------------------------|-------------------------------|-------------------------------------|
> | `page`     | `optional` | `integer`               | `Page to retrieve`            |  `0`                                |
> | `pageSize` | `optional` | `integer`               | `Number of Vehicles per page` | `10`                                |

##### Responses

> | http code     | content-type                      | response                                                            |
> |---------------|-----------------------------------|---------------------------------------------------------------------|
> | `201`         | `text/plain;charset=UTF-8`        | `Configuration created successfully`                                |
> | `400`         | `application/json`                | `{"code":"400","message":"Bad Request"}`                            |
> | `405`         | `text/html;charset=utf-8`         | `None`                                                              |

##### Example cURL

> ```javascript
>  curl -X GET http://localhost:8080/api/vehicle/get-all-vehicle-states?page=1&pageSize=100 -H "Content-Type: application/json"

</details>

#### Get simulation sessions

<details>
 <summary><code>GET</code> <code><b>/api/vehicle/simulation-sessions/</b></code> <code>(gets simulation sessions, which are records of start/stop times for each vehicles)</code></summary>

##### Parameters

> | name       |  type      | data type               | description                   | default                             |
> |------------|------------|-------------------------|-------------------------------|-------------------------------------|
> | `page`     | `optional` | `integer`               | `Page to retrieve`            |  `0`                                |
> | `pageSize` | `optional` | `integer`               | `Number of Vehicles per page` | `10`                                |

##### Responses

> | http code     | content-type              | response                                                            |
> |---------------|---------------------------|---------------------------------------------------------------------|
> | `200`         | `application/json`        | `Configuration created successfully`                                |
> | `400`         | `application/json`        | `{"code":"400","message":"Bad Request"}`                            |
> | `405`         | `text/html;charset=utf-8` | `None`                                                              |

##### Example cURL

> ```javascript
>  curl -X GET http://localhost:8080/api/vehicle/get-all-vehicle-states?page=1&pageSize=100 -H "Content-Type: application/json"

</details>

#### Reset server
*This endpoint requires that the invoking user is a member of the "superuser" group.*

<details>
 <summary><code>GET</code> <code><b>/api/vehicle/reset-server</b></code> <code>(Resets vehicle server)</code></summary>

##### Responses

> | http code     | content-type                      | response                                                            |
> |---------------|-----------------------------------|---------------------------------------------------------------------|
> | `201`         | `text/plain;charset=UTF-8`        | `Configuration created successfully`                                |
> | `405`         | `text/html;charset=utf-8`         | `None`                                                              |

##### Example cURL

> ```javascript
>  curl -X GET http://localhost:8080/api/vehicle/reset-server -H "Content-Type: application/json"

</details>

