# BebraRadar API Documentation

## Overview
BebraRadar now exposes its GTFS-like schema through lightweight REST controllers that return plain JSON payloads. Collection endpoints yield raw arrays of table rows without HAL wrappers, pagination metadata, or `_links` sections. Every resource speaks `application/json` and can be consumed by any HTTP client without custom media types.

- **Base URL:** `http://localhost:8080` (override with `server.port` if needed).
- **Default media type:** `application/json`.
- **Authentication:** not enabled out of the box.
- **Error payloads:** standard Spring Boot problem responses (`{"timestamp": ..., "status": ..., "error": ..., "path": ...}`).
- **Pagination:** disabled; `GET` collections stream the full table. Add filtering or pagination in a gateway layer if required.

## Resource Directory
| Table | Path | Identifier |
|-------|------|------------|
| `routes` | `/routes` | `route_id` (string)
| `stops` | `/stops` | `stop_id` (string)
| `trips` | `/trips` | `trip_id` (long, generated)
| `stop_times` | `/stop-times` | composite (`trip_id`, `stop_sequence`)
| `calendar` | `/calendars` | `service_id` (string)
| `users` | `/users` | `user_id` (long, generated)
| `exact_trip_event_geo_location` | `/geo-events` | `event_id` (long, generated)
| `exact_trip_event_timetable` | `/timetable-events` | `event_id` (long, generated)
| `exact_trip_anomaly` | `/anomalies` | `event_id` (long, generated)
| `shape_ids` | `/shape-ids` | `shape_id` (string)
| `shapes` | `/shape-points` | composite (`shape_id`, `shape_pt_sequence`)

Unless noted otherwise, controllers support the standard CRUD surface area:
- `GET /{resource}` – return all rows as an array
- `GET /{resource}/{id}` – fetch a single row
- `POST /{resource}` – create a new row (201 Created)
- `PUT /{resource}/{id}` – replace the row (404 if missing, or create for ID-based entities)
- `DELETE /{resource}/{id}` – delete the row (204 No Content)

Composite identifiers use nested paths: `/stop-times/{tripId}/{sequence}` and `/shape-points/{shapeId}/{sequence}`.

## JSON Schemas & Examples

### Routes `/routes`
- **Fields:** `id`
- **List response:**
  ```json
  [
    {"id": "100A"},
    {"id": "200B"}
  ]
  ```
- **Create:**
  ```bash
  curl -X POST http://localhost:8080/routes \
    -H 'Content-Type: application/json' \
    -d '{"id": "300C"}'
  ```

### Stops `/stops`
- **Fields:** `id`, `name`, `latitude`, `longitude`
- **Fetch single:**
  ```json
  {
    "id": "STOP_001",
    "name": "Центральная площадь",
    "latitude": 55.7558,
    "longitude": 37.6176
  }
  ```

### Trips `/trips`
- **Fields:** `id`, `routeId`, `startTime`, `vehicleNumber`, `shapeId`
- **Create:**
  ```bash
  curl -X POST http://localhost:8080/trips \
    -H 'Content-Type: application/json' \
    -d '{
          "routeId": "100A",
          "startTime": "2024-01-15T08:00:00Z",
          "vehicleNumber": "bus_001",
          "shapeId": "SHAPE_1"
        }'
  ```
- **Response:**
  ```json
  {
    "id": 42,
    "routeId": "100A",
    "startTime": "2024-01-15T08:00:00Z",
    "vehicleNumber": "bus_001",
    "shapeId": "SHAPE_1"
  }
  ```

### Stop Times `/stop-times`
- **Fields:** `tripId`, `stopSequence`, `stopId`, `arrivalTime`, `departureTime`
- **Identifiers:** `/stop-times/{tripId}/{stopSequence}`
- **Create:**
  ```bash
  curl -X POST http://localhost:8080/stop-times \
    -H 'Content-Type: application/json' \
    -d '{
          "tripId": 42,
          "stopSequence": 1,
          "stopId": "STOP_001",
          "arrivalTime": 0,
          "departureTime": 30
        }'
  ```

### Calendars `/calendars`
- **Fields:** `id`, weekday booleans, `startDate`, `endDate`
- **Example:**
  ```json
  {
    "id": "WEEKDAY",
    "monday": true,
    "tuesday": true,
    "wednesday": true,
    "thursday": true,
    "friday": true,
    "saturday": false,
    "sunday": false,
    "startDate": "2024-01-01",
    "endDate": "2024-06-30"
  }
  ```

### Users `/users`
- **Fields:** `id`, `trustLevel`
- **Create:**
  ```bash
  curl -X POST http://localhost:8080/users \
    -H 'Content-Type: application/json' \
    -d '{"trustLevel": 0.8}'
  ```

### Geo Events `/geo-events`
- **Fields:** `id`, `tripId`, `userId`, `timestamp`, `latitude`, `longitude`, `gpsAccuracyMeters`, `type`
- **Response example:**
  ```json
  {
    "id": 10,
    "tripId": 42,
    "userId": 5,
    "timestamp": "2024-01-15T08:03:00Z",
    "latitude": 55.751,
    "longitude": 37.618,
    "gpsAccuracyMeters": 5.0,
    "type": "GPS"
  }
  ```

### Timetable Events `/timetable-events`
- **Fields:** `id`, `tripId`, `userId`, `timestamp`, `latitude`, `longitude`, `gpsAccuracyMeters`, `type`, `reportedTime`

### Anomalies `/anomalies`
- **Fields:** `id`, `tripId`, `userId`, `timestamp`, `latitude`, `longitude`, `gpsAccuracyMeters`, `type`, `estimatedDelay`

### Shape IDs `/shape-ids`
- **Fields:** `id`

### Shape Points `/shape-points`
- **Fields:** `shapeId`, `sequence`, `latitude`, `longitude`, `distanceTraveled`
- **Identifiers:** `/shape-points/{shapeId}/{sequence}`
- **Create:**
  ```bash
  curl -X POST http://localhost:8080/shape-points \
    -H 'Content-Type: application/json' \
    -d '{
          "shapeId": "SHAPE_1",
          "sequence": 1,
          "latitude": 55.751,
          "longitude": 37.618,
          "distanceTraveled": 0.0
        }'
  ```

## Aggregated Feed `/events/{type}`
Use this helper endpoint to pull the latest realtime events without juggling individual tables.

- **Path variable:** `type` ∈ `{geolocation, timetable, anomaly}`
- **Query:** `since` (ISO 8601). When omitted, results are sorted by `timestamp` descending.
- **Example:**
  ```bash
  curl "http://localhost:8080/events/geolocation?since=2024-01-15T08:00:00Z"
  ```
- **Response:** array of the same DTOs returned by `/geo-events`, `/timetable-events`, or `/anomalies` (only data columns, no wrappers).

## Testing Checklist
1. Start PostgreSQL with the expected schema/data.
2. Launch the application: `./mvnw spring-boot:run`.
3. Verify a collection endpoint, e.g. `curl http://localhost:8080/routes` – expect a raw JSON array.
4. Exercise CRUD flows (create/update/delete) for the entities you rely on.

## Notes & Limitations
- Relationships now accept foreign keys directly (e.g., `routeId`, `tripId`). Missing references yield `404` responses.
- Because collections are unpaged, large tables may require client-side filtering or a custom gateway.
- No authentication is baked in; secure the application before exposing it publicly.
