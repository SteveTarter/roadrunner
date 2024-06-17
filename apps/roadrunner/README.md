# Roadrunner
Simulates vehicles travelling along routes between addresses at posted speeds.

# Description

Given a starting and ending location, get directions for travelling that route along with the posted speeds for each leg of travel.  The directions are retrieved using a MapBox API.  The starting and ending locations may be specified by street address, or by latitude/longitude.

Vehicle creation, route specification, and status operations are all performed using REST.

Companion project of the Roadrunner Viewer

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

