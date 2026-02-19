# Roadrunner
Simulates vehicles travelling along routes between addresses at posted speeds.

# Description

Given a starting and ending location, get directions for travelling that route along with the posted speeds for each leg of travel.  The directions are retrieved using a MapBox API.  The starting and ending locations may be specified by street address, or by latitude/longitude.

Vehicle creation, route specification, and status operations are all performed using REST.

Companion project of the Roadrunner Viewer

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
  Active Spring profile.  Should be 'eks' for AWS builds.  
  Example: `eks`

- `com.tarterware.roadrunner.vehicle-polling-period`  
  How often to poll the ready list for vehicles  
  Example: `100ms`

- `com.tarterware.roadrunner.vehicle-update-period`  
  How often each vehicle should be updated.  
  Example: `250ms`

- `com.tarterware.roadrunner.jitter-stat-capacity`  
  Number of reading to consider in jitter statistics  
  Example: `200`

- `prometheus.secret.namespace`  
  Namespace of secret for prometheus to obtain Bearer token.  
  Example: `roadrunner`

- `prometheus.secret.name`  
  Namespace of secret for prometheus to obtain Bearer token.  
  Example: `prometheus-token-secret`
 

## 'Secret' Properties Configuration

The following variables should be set in a 'secrets.properties' file that is peer to the application properties.  Note that this is not in the repo by design.

- `mapbox.api.key`  
  API key for the MapBox mapping provider  
  Example: `pk.bleggity-blah`

- `spring.security.oauth2.resourceserver.jwt.issuer-uri`  
  OAuth2 JWT issuer  
  Example: `https://dev-PROJECTID.us.auth0.com/`

- `auth0.api.audience`  
  OAuth2 JWT issuer  
  Example: `https://auth.PROJECTED.com/`

- `auth0.api.client-id`  
  Auth0 Client ID  
  Example: `a-client-id-but-not-this`

- `auth0.api.client-secret`  
  Auth0 Client Secret  
  Example: `nunyabiznezz`

- `cognito.app-client-id`  
  Cognito Client ID  
  Example: `20ishlongalphanumeric`
  
- `com.tarterware.redis.password`  
  Password to access redis outside AWS  
  Example: `nunyabiznezazzole`


------------------------------------------------------------------------------------------

#### Create new vehicle

<details>
 <summary><code>POST</code> <code><b>/api/vehicle/create-new</b></code> <code>(creates a new vehicle)</code></summary>

##### Parameters

> | name      |  type     | data type               | description                                                           |
> |-----------|-----------|-------------------------|-----------------------------------------------------------------------|
> | None      |  required | object (JSON or YAML)   | N/A  |

##### Responses

> | http code     | content-type                      | response                                                            |
> |---------------|-----------------------------------|---------------------------------------------------------------------|
> | `201`         | `text/plain;charset=UTF-8`        | `Vehicle created successfully`                                      |
> | `400`         | `application/json`                | `{"code":"400","message":"Bad Request"}`                            |
> | `405`         | `text/html;charset=utf-8`         | None                                                                |

##### Example cURL

> ```javascript
>  curl -X POST http://localhost:8080/api/vehicle/create-new -H "Content-Type: application/json" -d "{ \"listStops\": [ { \"address1\": \"10201 White Settlement Rd\", \"city\": \"Fort Worth\", \"state\": \"TX\", \"zipCode\": \"76108\" }, { \"address1\": \"12301 Camp Bowie W Blvd\", \"city\": \"Aledo\", \"state\": \"TX\", \"zipCode\": \"76008\" } ] }"```
</details>

#### Create crisscross pattern of vehicles and routes

<details>
 <summary><code>POST</code> <code><b>/api/vehicle/create-crisscross</b></code> <code>(creates a new "criss cross" set of routes and vehicles)</code></summary>

##### Parameters

> | name      |  type     | data type               | description                                                           |
> |-----------|-----------|-------------------------|-----------------------------------------------------------------------|
> | None      |  required | object (JSON or YAML)   | N/A  |

##### Responses

> | http code     | content-type                      | response                                                            |
> |---------------|-----------------------------------|---------------------------------------------------------------------|
> | `201`         | `text/plain;charset=UTF-8`        | `Configuration created successfully`                                |
> | `400`         | `application/json`                | `{"code":"400","message":"Bad Request"}`                            |
> | `405`         | `text/html;charset=utf-8`         | None                                                                |

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
> | `400`         | `application/json`                | `{"code":"400","message":"Bad Request"}`                            |
> | `405`         | `text/html;charset=utf-8`         | None                                                                |

##### Example cURL

> ```javascript
>  curl -X GET http://localhost:8080/api/vehicle/get-vehicle-state/create-crisscross/778afa04-2fd9-44e7-8e15-a4ccd835a608 -H "Content-Type: application/json"

</details>

#### Get vehicle states for all vehicles

<details>
 <summary><code>GET</code> <code><b>/api/vehicle/get-all-vehicle-states/</b></code> <code>(gets vehicle state for the given vehicle)</code></summary>

##### Parameters

> | name      |  type     | data type               | description                                                           |
> |-----------|-----------|-------------------------|-----------------------------------------------------------------------|
> | vehicleId |  required | string                  | ID of vehicle to retrieve |

##### Responses

> | http code     | content-type                      | response                                                            |
> |---------------|-----------------------------------|---------------------------------------------------------------------|
> | `201`         | `text/plain;charset=UTF-8`        | `Configuration created successfully`                                |
> | `400`         | `application/json`                | `{"code":"400","message":"Bad Request"}`                            |
> | `405`         | `text/html;charset=utf-8`         | None                                                                |

##### Example cURL

> ```javascript
>  curl -X GET http://localhost:8080/api/vehicle/get-all-vehicle-states -H "Content-Type: application/json"

</details>

#### Reset server

<details>
 <summary><code>GET</code> <code><b>/api/vehicle/reset-server</b></code> <code>(Resets vehicle server)</code></summary>

##### Parameters

> | name      |  type     | data type               | description                                                           |
> |-----------|-----------|-------------------------|-----------------------------------------------------------------------|
> | None      |  required | object (JSON or YAML)   | N/A  |


##### Responses

> | http code     | content-type                      | response                                                            |
> |---------------|-----------------------------------|---------------------------------------------------------------------|
> | `201`         | `text/plain;charset=UTF-8`        | `Configuration created successfully`                                |
> | `405`         | `text/html;charset=utf-8`         | None                                                                |

##### Example cURL

> ```javascript
>  curl -X GET http://localhost:8080/api/vehicle/reset-server -H "Content-Type: application/json"

</details>

