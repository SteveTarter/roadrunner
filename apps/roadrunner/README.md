# Roadrunner

Roadrunner is a high-performance, distributed vehicle simulation engine designed to model real-time movement across complex topologies. It serves as the backend simulation provider for the [Roadrunner Viewer](https://github.com/SteveTarter/roadrunner-viewer). A related project provides [Terraform automation and Kubernetes manifests](https://github.com/SteveTarter/roadrunner-k8s-orchestration) required to deploy the full Roadrunner simulation suite.

## Related Projects

The Roadrunner simulation suite consists of three repositories that work together:

| Repository | Description |
| --- | --- |
| **roadrunner** (this repo) | Backend simulation engine — REST API, vehicle physics, Kafka telemetry |
| [roadrunner-viewer](https://github.com/SteveTarter/roadrunner-viewer) | Frontend map-based viewer — displays live and historical vehicle positions |
| [roadrunner-k8s-orchestration](https://github.com/SteveTarter/roadrunner-k8s-orchestration) | Terraform and Kubernetes manifests for deploying the full suite to AWS EKS |

The typical data flow is: **roadrunner** simulates vehicles → publishes position events to **Kafka** → **roadrunner-viewer** reads from the Playback API and renders vehicles on a Mapbox map.

---

# Overview

Roadrunner simulates vehicle lifecycles, from creation to arrival, by calculating precise geographic movements based on real-world routing data. The system is built using a **Hexagonal Architecture (Ports and Adapters)**, allowing it to remain infrastructure-agnostic while delivering high-velocity telemetry. The system currently uses Kafka to post position updates to a topic. A previous implementation utilized Redis.

## Key Features

- **Real-World Routing:** Integrates with the **Mapbox Directions API** to retrieve accurate route geometry and posted speed limits for simulation legs.

- **Geospatial Fidelity:** Performs advanced spatial calculations, including coordinate transformations between **WGS84** (GPS) and **UTM** (Planar) projections to maintain accuracy across long-distance travel.

- **Event-Driven Telemetry:** Vehicle positions, status changes, and lifecycle events are broadcast to **Apache Kafka** (e.g. `vehicle.position.v1`), enabling low-latency, asynchronous state updates.

- **Distributed State Management:** Implements an "Event Sourcing" approach where simulation state is maintained in-memory via Kafka stream consumption, ensuring rapid recovery and horizontal scalability.

- **Time-Travel Playback:** Historical vehicle positions can be reconstructed at any point in time by replaying the Kafka event log, with a result cache to avoid redundant log scans.

- **Role-Based Security:** API endpoints are secured with OAuth2 JWT tokens (Auth0). Write operations are restricted to authenticated superusers; read operations are publicly accessible.

- **RESTful Control Plane:** Simple API for vehicle creation (by address or coordinate), bulk simulation generation (e.g., "Criss-Cross" patterns), simulation control, bookmark management, and historical playback.

## Architecture

The project is designed for high concurrency and resilience. Its five primary internal components are:

- **The Runner:** A dedicated simulation loop that advances each vehicle's position on every tick, manages route geometry, performs WGS84 ↔ UTM coordinate projections, and tracks sub-millisecond scheduling jitter.

- **The Store:** Pluggable persistence adapters (currently `KafkaControllerVehicleStateStore`, formerly Redis) that satisfy the `VehicleStateStore` port. The store maintains an in-memory snapshot of all active vehicle states, rebuilt from consumed Kafka events, so reads are always served from memory without hitting the broker.

- **The Messaging Layer:** Conditional Spring autoconfiguration for Kafka-based event publishing and consumption. The producer publishes `VehiclePositionEvent` records to a configurable topic; the consumer rebuilds the in-memory store. Kafka can be disabled via `roadrunner.messaging.kafka.enabled=false` for local development.

- **The Playback Layer:** A long-lived Kafka consumer assigned to all topic partitions that supports time-based queries. For historical requests it performs a "surgical seek" to a timestamp-derived offset and scans a configurable lookback window, returning the most recent state per vehicle. A result cache (`PlaybackResultCache`) avoids repeated log scans for the same timestamp.

- **The Security Layer:** OAuth2 resource-server configuration that validates JWT tokens issued by AWS Cognito (formerly Auth0). User principals are enriched with role information (e.g. `superuser`, `creator`) which is used to enforce per-endpoint access control and per-user daily vehicle creation quotas (tracked in Redis).

## Pluggable Infrastructure

Due to the use of the **Ports and Adapters** pattern, the messaging and persistence layers are entirely decoupled from the core simulation logic. While Kafka is the current standard, the system is designed to support:

- **Message Brokers:** Integration with **RabbitMQ**, **NATS**, or **MQTT** for varying throughput requirements.

- **Cloud Streams:** Native support for **Amazon Kinesis** or **Google Pub/Sub**.

- **Distributed Caching:** Leveraging **Hazelcast** or **Ignite** for high-availability state storage across clusters.

## Prerequisites

Before running Roadrunner, ensure the following are in place:

| Requirement | Notes |
| --- | --- |
| **Java 21+** | The project targets Java 21. Check with `java -version`. |
| **Maven 3.9+** | Used to build and run. Check with `mvn -version`. |
| **Apache Kafka** | A running Kafka broker is required for vehicle telemetry. The default bootstrap address is `localhost:9094`. |
| **Redis** | A running Redis instance is required for route geometry caching and per-user quota tracking. The default address is `127.0.0.1:6379`. |
| **Mapbox account** | A [Mapbox API key](https://account.mapbox.com/) is required for real-world routing data. |
| **Auth0 account** | An [Auth0 tenant](https://auth0.com/) is required to issue JWT tokens for secured endpoints. The issuer URI goes in `secrets.properties`. |
| **`secrets.properties`** | A local file (not committed to the repo) holding sensitive credentials — see the [Secret Properties Configuration](#secret-properties-configuration) section below. |
Note that compatible Kafka and Redis installations can be created within minikube using [roadrunner-k8s-orchestration](https://github.com/SteveTarter/roadrunner-k8s-orchestration).

## Quick Start

**1. Clone the repository:**

```bash
git clone https://github.com/SteveTarter/roadrunner.git
cd roadrunner
```

**2. Create `secrets.properties`** in the same directory as `application.properties` (`src/main/resources/`). This file is excluded from version control. Populate it with your credentials — see the [Secret Properties Configuration](#secret-properties-configuration) section for all required keys. At minimum you will need:

```properties
mapbox.api.key=pk.your-mapbox-key-here
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://your-tenant.us.auth0.com/
com.tarterware.redis.password=your-redis-password
```

**3. Ensure Kafka and Redis are running.** For local development, Docker Compose is the quickest option:

```bash
# Start Kafka (with KRaft, no Zookeeper) and Redis
docker run -d --name redis -p 6379:6379 redis:7
# Use your preferred Kafka setup, e.g. Redpanda or Confluent Platform
# Alternately, if minikube running roadrunner-k8s-orchestration, use port forwarding:
# Kafka port forwarding
kubectl port-forward --namespace roadrunner svc/roadrunner-kafka-kafka-external-bootstrap 9094:9094
# Redis port forwarding (separate window)
kubectl port-forward --namespace roadrunner svc/redis-master 6379:6379
```

**4. Build and run:**

```bash
mvn spring-boot:run
```

**5. Verify the server is healthy.** The management endpoints run on a separate port (`8081` by default):

```bash
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP", ...}
```

The REST API listens on port `8080`:

```bash
curl http://localhost:8080/api/vehicle/get-all-vehicle-states
# Expected: {"content":[], "page":{"size":10,"totalElements":0,...}}
```

> **Note:** When deploying to AWS EKS, set `spring.profiles.active=eks` in `application.properties`. This activates cloud-specific configuration including Cognito-based JWT validation and EKS service discovery.

## Application Properties Configuration

The following variables need to be set in the `application.properties` file for the application to start:

- `spring.config.import`  
Specifies external properties files to import. Optional file for sensitive configuration.  
Example: `optional:secrets.properties`

- `spring.data.rest.base-path`  
Base path for REST API.  
Example: `/`

- `mapbox.api.url`  
URL to the Mapbox API.  
Example: `https://api.mapbox.com/`

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

- `spring.profiles.active`  
Active Spring profile. Should be 'eks' for AWS builds.  
Example: `eks`

- `roadrunner.messaging.kafka.enabled`  
Enable Vehicle update messages using Kafka.  
Example: true

- `com.tarterware.roadrunner.vehicle-update-period`  
How often each vehicle should be updated.  
Example: `250ms`

- `com.tarterware.roadrunner.jitter-stat-capacity`  
Number of reading to consider in jitter statistics.
Example: `200`

- `prometheus.secret.namespace`  
Namespace of secret for prometheus to obtain Bearer token.  
Example: `roadrunner`

- `prometheus.secret.name`  
Namespace of secret for prometheus to obtain Bearer token.  
Example: `prometheus-token-secret`

- `spring.kafka.bootstrap-servers`  
Location of Kafka bootstrap servers.  
Example: localhost:9094

- `spring.kafka.producer.acks`  
Kafka Producer messages to acknowledge.  
Example: all

- `spring.kafka.producer.properties.enable.idempotence`  
Idempotence enabled for Kafka  
Example: true

- `spring.kafka.producer.key-serializer`  
Class for Kafka Producer to use for key serialization.  
Example: org.apache.kafka.common.serialization.StringSerializer

- `spring.kafka.producer.value-serializer`  
Class for Kafka Producer to use for value serialization.  
Example: org.springframework.kafka.support.serializer.JsonSerializer

- `spring.kafka.consumer.properties.spring.json.trusted.packages`  
Packages that Kafka can trust.  
Example: com.tarterware.roadrunner.*

- `spring.kafka.consumer.properties.spring.json.use.type.headers`  
Kafka Consumer should use type headers with JSON.  
Example: false

- `spring.kafka.consumer.properties.spring.json.value.default.type`  
Kafka Consumer JSON value default type.  
Example: com.tarterware.roadrunner.messaging.VehiclePositionEvent

- `roadrunner.kafka.topic.vehicle-position`  
Roadrunner Kafka topic for VehiclePosition.  
Example: vehicle.position.v1

## 'Secret' Properties Configuration

The following variables should be set in a 'secrets.properties' file that is peer to the application properties. Note that this is not in the repo by design.

- `mapbox.api.key`  
API key for the MapBox mapping provider  
Example: `pk.bleggity-blah`

- `spring.security.oauth2.resourceserver.jwt.issuer-uri`  
OAuth2 JWT issuer  
Example: `https://dev-PROJECTID.us.auth0.com/`

- `cognito.app-client-id`  
Cognito Client ID  
Example: `20ishlongalphanumeric`

- `com.tarterware.redis.password`  
Password to access redis outside AWS  
Example: `nunyabiznezazzole`

---

## API Endpoints

All endpoints are prefixed with `/api/vehicle`. The server listens on port `8080` by default.

---

#### Create new vehicle

**`POST` `/api/vehicle/create-new` `(creates a new vehicle and starts its simulation)`**

Creates a single simulated vehicle that will travel along a route defined by an ordered list of address stops. The Mapbox Directions API is called for each leg of the journey to obtain real-world geometry and posted speed data.

##### Request Body

A JSON object (`RouteRequest`) with the following fields:

> | field | type | required | description |
> | --- | --- | --- | --- |
> | `listStops` | array of `RouteStop` | yes | Ordered list of two or more stops defining the route. |

Each `RouteStop` object contains:

> | field | type | required | description |
> | --- | --- | --- | --- |
> | `address1` | string | yes | Street address (e.g. `"10201 White Settlement Rd"`) |
> | `city` | string | yes | City name (e.g. `"Fort Worth"`) |
> | `state` | string | yes | Two-letter US state code (e.g. `"TX"`) |
> | `zipCode` | string | yes | ZIP code (e.g. `"76108"`) |

##### Responses

> | http code | content-type               | response                                              |
> | --------- | -------------------------- | ----------------------------------------------------- |
> | `201`     | `application/json`         | JSON object representing the newly created `VehicleState` |
> | `400`     | `application/json`         | `{"code":"400","message":"Bad Request"}` — malformed body or missing required fields |
> | `405`     | `text/html;charset=utf-8`  | Method Not Allowed — wrong HTTP verb used             |

##### Example cURL

> ```bash
> curl -X POST http://localhost:8080/api/vehicle/create-new \
>   -H "Content-Type: application/json" \
>   -d '{
>     "listStops": [
>       {
>         "address1": "10201 White Settlement Rd",
>         "city": "Fort Worth",
>         "state": "TX",
>         "zipCode": "76108"
>       },
>       {
>         "address1": "12301 Camp Bowie W Blvd",
>         "city": "Aledo",
>         "state": "TX",
>         "zipCode": "76008"
>       }
>     ]
>   }'
> ```

---

#### Create crisscross pattern of vehicles and routes

**`POST` `/api/vehicle/create-crisscross` `(creates a new "criss cross" set of routes and vehicles)`**

Bulk-creates a set of simulated vehicles whose routes criss-cross a circular geographic area centred on the given coordinates. This is useful for quickly populating a simulation with many vehicles spread across a region.

##### Request Body

A JSON object (`CrisscrossRequest`) with the following fields:

> | field | type | required | description |
> | --- | --- | --- | --- |
> | `degLatitude` | number | yes | Latitude of the centre point in decimal degrees (e.g. `32.7507`) |
> | `degLongitude` | number | yes | Longitude of the centre point in decimal degrees (e.g. `-97.3286`) |
> | `kmRadius` | number | yes | Radius of the criss-cross area in kilometres (e.g. `50.0`) |
> | `vehicleCount` | integer | yes | Number of vehicles (and crossing routes) to create (e.g. `36`) |

##### Responses

> | http code | content-type               | response                                                        |
> | --------- | -------------------------- | --------------------------------------------------------------- |
> | `201`     | `application/json`         | JSON array of `VehicleState` objects — one entry per created vehicle |
> | `400`     | `application/json`         | `{"code":"400","message":"Bad Request"}` — malformed body or invalid parameter values |
> | `405`     | `text/html;charset=utf-8`  | Method Not Allowed — wrong HTTP verb used                       |

##### Example cURL

> ```bash
> curl -X POST http://localhost:8080/api/vehicle/create-crisscross \
>   -H "Content-Type: application/json" \
>   -d '{
>     "degLatitude": 32.7507,
>     "degLongitude": -97.3286,
>     "kmRadius": 50.0,
>     "vehicleCount": 36
>   }'
> ```

---

#### Get vehicle state for a given vehicle ID

**`GET` `/api/vehicle/get-vehicle-state/{vehicleId}` `(gets vehicle state for the given vehicle)`**

Returns the current simulation state of a single vehicle, including its position, heading, speed, and route progress.

##### Path Parameters

> | name        | type     | data type | description                              |
> | ----------- | -------- | --------- | ---------------------------------------- |
> | `vehicleId` | required | string (UUID) | The UUID of the vehicle to retrieve (e.g. `778afa04-2fd9-44e7-8e15-a4ccd835a608`) |

##### Responses

> | http code | content-type       | response                                                   |
> | --------- | ------------------ | ---------------------------------------------------------- |
> | `200`     | `application/json` | JSON object representing the current `VehicleState`        |
> | `400`     | `application/json` | `{"code":"400","message":"Bad Request"}` — malformed UUID  |
> | `404`     | `application/json` | Vehicle not found — no vehicle exists with that ID         |
> | `405`     | `text/html;charset=utf-8` | Method Not Allowed — wrong HTTP verb used           |

##### Example cURL

> ```bash
> curl -X GET \
>   http://localhost:8080/api/vehicle/get-vehicle-state/778afa04-2fd9-44e7-8e15-a4ccd835a608 \
>   -H "Content-Type: application/json"
> ```

---

#### Get vehicle states for all vehicles

**`GET` `/api/vehicle/get-all-vehicle-states` `(gets vehicle states for all active vehicles)`**

Returns a paginated list of the current simulation states for every vehicle known to the server. Use the `page` and `pageSize` query parameters to page through large result sets.

##### Query Parameters

> | name       | type     | data type | description                        | default |
> | ---------- | -------- | --------- | ---------------------------------- | ------- |
> | `page`     | optional | integer   | Zero-based page number to retrieve | `0`     |
> | `pageSize` | optional | integer   | Number of vehicle states per page  | `10`    |

##### Responses

> | http code | content-type       | response                                                                        |
> | --------- | ------------------ | ------------------------------------------------------------------------------- |
> | `200`     | `application/json` | JSON array of `VehicleState` objects for the requested page                     |
> | `400`     | `application/json` | `{"code":"400","message":"Bad Request"}` — invalid query parameter values       |
> | `405`     | `text/html;charset=utf-8` | Method Not Allowed — wrong HTTP verb used                                |

##### Example cURL

> ```bash
> # First page, default page size
> curl -X GET \
>   "http://localhost:8080/api/vehicle/get-all-vehicle-states" \
>   -H "Content-Type: application/json"
>
> # Second page, 25 vehicles per page
> curl -X GET \
>   "http://localhost:8080/api/vehicle/get-all-vehicle-states?page=1&pageSize=25" \
>   -H "Content-Type: application/json"
> ```

---

#### Reset server

**`GET` `/api/vehicle/reset-server` `(resets the vehicle server, removing all active vehicles and routes)`**

Clears all in-memory simulation state: every tracked vehicle and its associated route data are removed. The server is returned to a clean initial state without requiring a restart. Useful for re-initialising a simulation run.

##### Parameters

No request body or query parameters are required.

##### Responses

> | http code | content-type               | response                                              |
> | --------- | -------------------------- | ----------------------------------------------------- |
> | `200`     | `text/plain;charset=UTF-8` | `Server reset successfully`                           |
> | `405`     | `text/html;charset=utf-8`  | Method Not Allowed — wrong HTTP verb used             |

##### Example cURL

> ```bash
> curl -X GET \
>   http://localhost:8080/api/vehicle/reset-server \
>   -H "Content-Type: application/json"
> ```

---

## Data Models

### `RouteStop`

Represents a single address stop along a vehicle's route.

```json
{
  "address1": "10201 White Settlement Rd",
  "city":     "Fort Worth",
  "state":    "TX",
  "zipCode":  "76108"
}
```

### `RouteRequest`

Request body for `POST /api/vehicle/create-new`.

```json
{
  "listStops": [
    { "address1": "...", "city": "...", "state": "TX", "zipCode": "..." },
    { "address1": "...", "city": "...", "state": "TX", "zipCode": "..." }
  ]
}
```

### `CrisscrossRequest`

Request body for `POST /api/vehicle/create-crisscross`.

```json
{
  "degLatitude":  32.7507,
  "degLongitude": -97.3286,
  "kmRadius":     50.0,
  "vehicleCount": 36
}
```

### `VehicleState`

Returned by creation and retrieval endpoints. Key fields include:

> | field | type | description |
> | --- | --- | --- |
> | `vehicleId` | string (UUID) | Unique identifier for this vehicle |
> | `degLatitude` | number | Current latitude in decimal degrees |
> | `degLongitude` | number | Current longitude in decimal degrees |
> | `degHeading` | number | Current heading in degrees (0 = North, clockwise) |
> | `metersPerSecond` | number | Current speed in metres per second |
> | `routeProgressPercent` | number | Percentage of the total route completed (0–100) |
> | `status` | string | Lifecycle status (e.g. `RUNNING`, `ARRIVED`) |

---

## Bookmark API Endpoints

Bookmarks allow named geographic positions to be saved and retrieved, giving users quick access to predefined map locations in the Roadrunner Viewer. All bookmark endpoints are prefixed with `/api/bookmarks`.

**Authentication:** Mutating operations (`POST`, `PUT`, `DELETE`) require a valid JWT bearer token issued by the configured OAuth2 provider (Auth0), and the authenticated user must hold the **superuser** role. Read operations (`GET`) are unauthenticated and publicly accessible.

Include the JWT in the `Authorization` header for all write requests:

```
Authorization: Bearer <your-jwt-token>
```

---

#### Create a bookmark

**`POST` `/api/bookmarks` `(creates a new bookmark — superuser only)`**

Persists a new named bookmark. The caller must be authenticated and hold the superuser role; any other authenticated user receives `403 Forbidden`.

##### Request Body

A JSON object (`Bookmark`) with the following fields:

> | field | type | required | description |
> | --- | --- | --- | --- |
> | `vehicleId` | string | yes | Unique identifier for the bookmark (typically a UUID). Used as the bookmark's key in the store. |
> | `name` | string | yes | Human-readable display name for the bookmark (e.g. `"Downtown Fort Worth"`) |
> | `degLatitude` | number | yes | Latitude of the bookmarked position in decimal degrees |
> | `degLongitude` | number | yes | Longitude of the bookmarked position in decimal degrees |

##### Responses

> | http code | content-type | response |
> | --- | --- | --- |
> | `200` | `application/json` | JSON object of the saved `Bookmark` (with any server-assigned fields populated) |
> | `400` | `application/json` | `{"code":"400","message":"Bad Request"}` — malformed request body |
> | `401` | `application/json` | Unauthorized — JWT is missing or invalid |
> | `403` | `application/json` | Forbidden — authenticated user is not a superuser |
> | `405` | `text/html;charset=utf-8` | Method Not Allowed — wrong HTTP verb used |

##### Example cURL

> ```bash
> curl -X POST http://localhost:8080/api/bookmarks \
>   -H "Content-Type: application/json" \
>   -H "Authorization: Bearer <your-jwt-token>" \
>   -d '{
>     "vehicleId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
>     "name": "Downtown Fort Worth",
>     "degLatitude": 32.7507,
>     "degLongitude": -97.3286
>   }'
> ```

---

#### Update a bookmark

**`PUT` `/api/bookmarks` `(updates an existing bookmark — superuser only)`**

Replaces the stored bookmark identified by the `vehicleId` in the request body with the new values supplied. The caller must be authenticated and hold the superuser role.

##### Request Body

A JSON object (`Bookmark`) with the same fields as for `POST /api/bookmarks`. The `vehicleId` field is used to locate the existing record; all other fields are replaced.

> | field | type | required | description |
> | --- | --- | --- | --- |
> | `vehicleId` | string | yes | ID of the bookmark to update — must match an existing record |
> | `name` | string | yes | New display name |
> | `degLatitude` | number | yes | New latitude in decimal degrees |
> | `degLongitude` | number | yes | New longitude in decimal degrees |

##### Responses

> | http code | content-type | response |
> | --- | --- | --- |
> | `200` | `application/json` | JSON object of the updated `Bookmark` |
> | `400` | `application/json` | `{"code":"400","message":"Bad Request"}` — malformed request body |
> | `401` | `application/json` | Unauthorized — JWT is missing or invalid |
> | `403` | `application/json` | Forbidden — authenticated user is not a superuser |
> | `404` | `application/json` | Not Found — no bookmark exists with the given `vehicleId` |
> | `405` | `text/html;charset=utf-8` | Method Not Allowed — wrong HTTP verb used |

##### Example cURL

> ```bash
> curl -X PUT http://localhost:8080/api/bookmarks \
>   -H "Content-Type: application/json" \
>   -H "Authorization: Bearer <your-jwt-token>" \
>   -d '{
>     "vehicleId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
>     "name": "West Fort Worth",
>     "degLatitude": 32.7350,
>     "degLongitude": -97.4800
>   }'
> ```

---

#### Delete a bookmark

**`DELETE` `/api/bookmarks/{vehicleId}` `(deletes a bookmark by ID — superuser only)`**

Permanently removes the bookmark identified by `vehicleId`. The caller must be authenticated and hold the superuser role.

##### Path Parameters

> | name | type | data type | description |
> | --- | --- | --- | --- |
> | `vehicleId` | required | string (UUID) | The ID of the bookmark to delete |

##### Responses

> | http code | content-type | response |
> | --- | --- | --- |
> | `200` | `application/json` | Empty body — bookmark was successfully deleted |
> | `401` | `application/json` | Unauthorized — JWT is missing or invalid |
> | `403` | `application/json` | Forbidden — authenticated user is not a superuser |
> | `404` | `application/json` | Not Found — no bookmark exists with the given `vehicleId` |
> | `405` | `text/html;charset=utf-8` | Method Not Allowed — wrong HTTP verb used |

##### Example cURL

> ```bash
> curl -X DELETE \
>   http://localhost:8080/api/bookmarks/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
>   -H "Authorization: Bearer <your-jwt-token>"
> ```

---

#### Get all bookmarks

**`GET` `/api/bookmarks` `(returns all stored bookmarks)`**

Returns the complete list of saved bookmarks. This endpoint is publicly accessible and does not require authentication.

##### Parameters

No request body, path parameters, or query parameters are required.

##### Responses

> | http code | content-type | response |
> | --- | --- | --- |
> | `200` | `application/json` | JSON array of `Bookmark` objects (empty array `[]` if none exist) |
> | `405` | `text/html;charset=utf-8` | Method Not Allowed — wrong HTTP verb used |

##### Example cURL

> ```bash
> curl -X GET http://localhost:8080/api/bookmarks \
>   -H "Content-Type: application/json"
> ```

---

#### Get a single bookmark

**`GET` `/api/bookmarks/{vehicleId}` `(returns a bookmark by ID)`**

Returns the bookmark identified by `vehicleId`. This endpoint is publicly accessible and does not require authentication.

##### Path Parameters

> | name | type | data type | description |
> | --- | --- | --- | --- |
> | `vehicleId` | required | string (UUID) | The ID of the bookmark to retrieve |

##### Responses

> | http code | content-type | response |
> | --- | --- | --- |
> | `200` | `application/json` | JSON object representing the `Bookmark` |
> | `404` | `application/json` | Not Found — no bookmark exists with the given `vehicleId` |
> | `405` | `text/html;charset=utf-8` | Method Not Allowed — wrong HTTP verb used |

##### Example cURL

> ```bash
> curl -X GET \
>   http://localhost:8080/api/bookmarks/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
>   -H "Content-Type: application/json"
> ```

---

## Bookmark Data Model

### `Bookmark`

Represents a named geographic position saved for quick access in the viewer.

```json
{
  "vehicleId":    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "name":         "Downtown Fort Worth",
  "degLatitude":  32.7507,
  "degLongitude": -97.3286
}
```

> | field | type | description |
> | --- | --- | --- |
> | `vehicleId` | string (UUID) | Unique identifier for the bookmark, used as its storage key |
> | `name` | string | Human-readable display name shown in the viewer UI |
> | `degLatitude` | number | Latitude of the bookmarked map position in decimal degrees |
> | `degLongitude` | number | Longitude of the bookmarked map position in decimal degrees |

---

## Security Notes

The `BookmarkController` enforces role-based access control using OAuth2 JWT tokens issued by Auth0. The relevant security rules are:

- `GET` endpoints (`/api/bookmarks` and `/api/bookmarks/{vehicleId}`) are **publicly accessible** — no token required.
- `POST`, `PUT`, and `DELETE` endpoints require a **valid JWT** in the `Authorization: Bearer` header.
- Even with a valid JWT, the authenticated user must hold the **superuser** flag (checked via `UserPrincipal.isSuperuser()`). Any other user receives `403 Forbidden`.

Configure the OAuth2 issuer URI in `secrets.properties`:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://dev-PROJECTID.us.auth0.com/
```

---

## Playback API Endpoints

The Playback API enables time-travel queries over the vehicle simulation history. It allows clients to retrieve the state of all vehicles — or a single vehicle — either **live** (the current in-memory snapshot) or at any **historical point in time** by replaying the Kafka event log.

All endpoints are prefixed with `/api/playback` and require no authentication.

### How it works

The controller maintains two data sources and selects between them based on whether a `timestamp` query parameter is provided:

- **Hot store (live):** When `timestamp` is omitted or set to `"Unset"`, the controller reads directly from the in-memory `KafkaControllerVehicleStateStore`, returning the current position of every active vehicle with near-zero latency.

- **Cold store (historical):** When a `timestamp` is provided, the controller performs a *surgical seek* on a long-lived Kafka consumer assigned to all topic partitions. It scans a configurable lookback window ending at the requested timestamp, collecting the most recent event per vehicle found within that window. Results are cached to avoid repeated Kafka log scans for the same timestamp.

The cold-store consumer is **not thread-safe**; access is internally synchronized so concurrent HTTP requests are handled safely.

---

#### Get paginated vehicle states at a timestamp

**`GET` `/api/playback/state` `(returns paginated vehicle states, live or at a historical timestamp)`**

Returns a paginated collection of vehicle states. If no timestamp is given the live in-memory snapshot is returned instantly. If a timestamp is given the Kafka event log is replayed over the configured lookback window (default 2 seconds) ending at that timestamp, and the most recent state per vehicle within that window is returned.

Results are cached: repeated requests for the same `timestamp` + `windowPeriod` combination are served from memory without re-scanning Kafka.

##### Query Parameters

> | name | type | data type | description | default |
> | --- | --- | --- | --- | --- |
> | `timestamp` | optional | string (ISO-8601) | Point-in-time to query, e.g. `2026-04-17T21:47:07.113Z`. Omit or pass `"Unset"` to retrieve the current live state. | `"Unset"` |
> | `windowPeriod` | optional | string (ISO-8601 duration) | Width of the lookback window ending at `timestamp`, e.g. `2s`, `PT5S`. Only used for historical queries. | `"2s"` |
> | `page` | optional | integer | Zero-based page number to retrieve. | `0` |
> | `pageSize` | optional | integer | Number of vehicle states per page. | `10` |

##### Responses

> | http code | content-type | response |
> | --- | --- | --- |
> | `200` | `application/json` | `PagedModel` of `VehicleState` objects with pagination metadata |
> | `400` | `application/json` | `{"code":"400","message":"Bad Request"}` — malformed timestamp or duration string |
> | `405` | `text/html;charset=utf-8` | Method Not Allowed — wrong HTTP verb used |

The response body is a HATEOAS `PagedModel` with the following structure:

```json
{
  "content": [ /* array of VehicleState objects */ ],
  "page": {
    "size":          10,
    "totalElements": 36,
    "totalPages":    4,
    "number":        0
  }
}
```

##### Example cURL

> ```bash
> # Live state, first page, default page size
> curl -X GET "http://localhost:8080/api/playback/state"
>
> # Historical state at a specific timestamp, 5-second lookback window, page 2
> curl -X GET \
>   "http://localhost:8080/api/playback/state?timestamp=2026-04-17T21:47:07.113Z&windowPeriod=5s&page=1&pageSize=20"
> ```

---

#### Get a single vehicle's state at a timestamp

**`GET` `/api/playback/get-vehicle-state` `(returns a single vehicle's state, live or at a historical timestamp)`**

Returns the state of the vehicle identified by `vehicleId`, either from the live in-memory snapshot or reconstructed from the Kafka event log at the requested timestamp. The same 2-second lookback window logic applies as for the bulk endpoint. If no matching state is found within the window a `404` is returned.

##### Query Parameters

> | name | type | data type | description | default |
> | --- | --- | --- | --- | --- |
> | `vehicleId` | required | string (UUID) | The ID of the vehicle to retrieve. | `"Unset"` (returns 404 if not supplied) |
> | `timestamp` | optional | string (ISO-8601) | Point-in-time to query, e.g. `2026-04-17T21:47:07.113Z`. Omit or pass `"Unset"` to retrieve the current live state. | `"Unset"` |
> | `windowPeriod` | optional | string (ISO-8601 duration) | Width of the lookback window ending at `timestamp`. Only used for historical queries. | `"2s"` |

##### Responses

> | http code | content-type | response |
> | --- | --- | --- |
> | `200` | `application/json` | JSON object representing the `VehicleState` at the requested time |
> | `400` | `application/json` | `{"code":"400","message":"Bad Request"}` — malformed timestamp or duration string |
> | `404` | `application/json` | Not Found — no state for `vehicleId` was found within the query window |
> | `405` | `text/html;charset=utf-8` | Method Not Allowed — wrong HTTP verb used |

##### Example cURL

> ```bash
> # Live state for a specific vehicle
> curl -X GET \
>   "http://localhost:8080/api/playback/get-vehicle-state?vehicleId=778afa04-2fd9-44e7-8e15-a4ccd835a608"
>
> # Historical state for a specific vehicle at a point in time
> curl -X GET \
>   "http://localhost:8080/api/playback/get-vehicle-state?vehicleId=778afa04-2fd9-44e7-8e15-a4ccd835a608&timestamp=2026-04-17T21:47:07.113Z&windowPeriod=5s"
> ```

---

### Playback Configuration Properties

The following `application.properties` entries control playback behaviour:

> | property | description | default |
> | --- | --- | --- |
> | `com.tarterware.roadrunner.vehicle-state-buffer-period` | Lookback window used when the caller does not specify `windowPeriod` | `2s` |
> | `com.tarterware.roadrunner.playback-consumer-polling-period` | How long the cold-store Kafka consumer waits per poll cycle when scanning the log | `20ms` |
> | `com.tarterware.roadrunner.kafka.topic.vehicle-position` | Kafka topic scanned for historical vehicle position events | `vehicle.position.v1` |

### Playback Response Model

The `VehicleState` object returned by both playback endpoints carries the following key fields (reconstructed from `VehiclePositionEvent` records for historical queries):

> | field | type | description |
> | --- | --- | --- |
> | `id` | string (UUID) | Vehicle identifier |
> | `degLatitude` | number | Latitude in decimal degrees at the queried time |
> | `degLongitude` | number | Longitude in decimal degrees at the queried time |
> | `degBearing` | number | Heading in degrees (0 = North, clockwise) at the queried time |
> | `metersPerSecond` | number | Speed in metres per second at the queried time |
> | `colorCode` | string | Display colour assigned to this vehicle |
> | `managerHost` | string | Hostname of the Roadrunner instance that last updated this vehicle |
> | `msEpochLastRun` | long | Unix epoch timestamp (milliseconds) of the event from which this state was reconstructed |
> | `nsLastExec` | long | Nanosecond execution duration of the simulation step that produced this event |
> | `positionValid` | boolean | Whether the position coordinates are considered valid |
> | `positionLimited` | boolean | Whether the vehicle's position has been clamped to route bounds |
