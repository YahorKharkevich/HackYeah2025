# BebraRadar API Documentation

## Overview
BebraRadar exposes the PostgreSQL schema via Spring Data REST. Each table is mapped to a HAL-compliant REST resource that supports the standard CRUD operations provided by Spring Data (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`). The API is designed for back-office tools and internal integrations that need direct access to GTFS-like data, realtime events, and user metrics.

- **Base URL:** `http://localhost:8080`
- **Media type:** `application/hal+json` (Spring Data REST default). Plain JSON clients can ignore the `_links` section, but it is recommended to keep the `Accept: application/hal+json` header.
- **Authentication:** not configured (all endpoints are open by default).
- **Error payload:** Spring Data REST uses the standard Spring Boot error format (`{"timestamp": ..., "status": ..., "error": ..., "path": ...}`).
- **Pagination:** `GET` collection resources are paginated. Use `?page=` (0-based) and `?size=` query parameters to control page navigation.

## Resource Directory
| Table | Repository Path | Identifier |
|-------|-----------------|------------|
| `routes` | `/routes` | `route_id` (string)
| `stops` | `/stops` | `stop_id` (string)
| `trips` | `/trips` | `trip_id` (long)
| `stop_times` | `/stop-times` | composite (`trip_id`, `stop_sequence`)
| `calendar` | `/calendars` | `service_id` (string)
| `users` | `/users` | `user_id` (long)
| `exact_trip_event_geo_location` | `/geo-events` | `event_id` (long)
| `exact_trip_event_timetable` | `/timetable-events` | `event_id` (long)
| `exact_trip_anomaly` | `/anomalies` | `event_id` (long)
| `shape_ids` | `/shape-ids` | `shape_id` (string)
| `shapes` | `/shape-points` | composite (`shape_id`, `shape_pt_sequence`)

All endpoints share the same interaction pattern:
- `GET /{resource}` – list with pagination
- `GET /{resource}/{id}` – fetch by identifier
- `POST /{resource}` – create (server generates IDs when the backing table uses sequences)
- `PUT /{resource}/{id}` – full update
- `PATCH /{resource}/{id}` – partial update (JSON Merge Patch)
- `DELETE /{resource}/{id}` – delete

## Working With Relationships
When creating or updating entities that reference other tables, send link URIs in the JSON body following the HAL format. Example for creating a trip that references an existing `route` and `shape`:

```bash
curl -X POST http://localhost:8080/trips \
  -H 'Content-Type: application/json' \
  -d '{
    "route": "http://localhost:8080/routes/{routeId}",
    "startTime": "2024-01-15T08:00:00Z",
    "vehicleNumber": "bus_001",
    "shape": "http://localhost:8080/shape-ids/{shapeId}"
  }'
```

For optional relationships you may omit the field or set it to `null`.

### Composite Identifiers
For repositories backed by composite primary keys (`stop-times` and `shape-points`), Spring Data REST concatenates key parts with a comma:
- `GET /stop-times/{tripId},{stopSequence}`
- `GET /shape-points/{shapeId},{sequence}`

Creating such records requires supplying both the embedded ID fields and links:

```bash
curl -X POST http://localhost:8080/stop-times \
  -H 'Content-Type: application/json' \
  -d '{
    "id": {
      "tripId": 42,
      "stopSequence": 1
    },
    "trip": "http://localhost:8080/trips/42",
    "stop": "http://localhost:8080/stops/STOP_001",
    "arrivalTime": 0,
    "departureTime": 30
  }'
```

## Resource Details and Sample Payloads

### Aggregated Events `/events/{type}`
- **Description:** Convenience endpoint that returns realtime events filtered by event family. Supports optional `since` filter.
- **Path Variables:**
  - `type` – one of `geolocation`, `timetable`, `anomaly`.
- **Query Params:**
  - `since` – ISO 8601 timestamp (`2024-01-15T08:00:00Z`). When omitted, results are sorted by timestamp descending without filtering.
- **Examples:**
  ```bash
  # Последние геолокации с 10 минут назад
  curl "http://localhost:8080/events/geolocation?since=$(date -u -v-10M +%Y-%m-%dT%H:%M:%SZ)"

  # Все последние аномалии
  curl http://localhost:8080/events/anomaly
  ```

### Routes `/routes`
- **Fields:** `id`
- **Example:**
  ```bash
  curl -X POST http://localhost:8080/routes \
    -H 'Content-Type: application/json' \
    -d '{"id": "100A"}'
  ```

### Stops `/stops`
- **Fields:** `id`, `name`, `latitude`, `longitude`
- **Example response (`GET /stops/STOP_001`):**
  ```json
  {
    "id": "STOP_001",
    "name": "Центральная площадь",
    "latitude": 55.7558,
    "longitude": 37.6176,
    "_links": {
      "self": {"href": "http://localhost:8080/stops/STOP_001"},
      "stop": {"href": "http://localhost:8080/stops/STOP_001"}
    }
  }
  ```

### Trips `/trips`
- **Fields:** `id`, `route`, `startTime`, `vehicleNumber`, `shape`
- **Notes:** `route` and `shape` are HAL links.

### Stop Times `/stop-times`
- **Fields:** embedded `id (tripId, stopSequence)`, `trip`, `stop`, `arrivalTime`, `departureTime`
- **Cascade:** deleting a `trip` removes associated stop times (database `ON DELETE CASCADE`).

### Calendars `/calendars`
- **Fields:** `id`, weekday booleans, `startDate`, `endDate`

### Users `/users`
- **Fields:** `id`, `trustLevel`

### Geo Events `/geo-events`
- **Fields:** `id`, `trip`, `user`, `timestamp`, `latitude`, `longitude`, `gpsAccuracyMeters`, `type`
- **Usage:** push realtime vehicle positions.

### Timetable Events `/timetable-events`
- **Fields:** `id`, `trip`, `user`, `timestamp`, `latitude`, `longitude`, `gpsAccuracyMeters`, `type`, `reportedTime`

### Anomalies `/anomalies`
- **Fields:** `id`, `trip`, `user`, `timestamp`, `latitude`, `longitude`, `gpsAccuracyMeters`, `type`, `estimatedDelay`

### Shape IDs `/shape-ids`
- **Fields:** `id`
- **Usage:** acts as parent for shape points; create before sending `/shape-points`.

### Shape Points `/shape-points`
- **Fields:** embedded `id (shapeId, sequence)`, `shape`, `latitude`, `longitude`, `distanceTraveled`
- **Example creation:**
  ```bash
  curl -X POST http://localhost:8080/shape-points \
    -H 'Content-Type: application/json' \
    -d '{
      "id": {"shapeId": "SHAPE_1", "sequence": 1},
      "shape": "http://localhost:8080/shape-ids/SHAPE_1",
      "latitude": 55.751,
      "longitude": 37.618,
      "distanceTraveled": 0.0
    }'
  ```

## Discoverability
Spring Data REST publishes a root catalog at `/`. Issue `GET /` to fetch a list of all exported resources with hyperlinks; clients can traverse those links without hardcoding paths.

```bash
curl http://localhost:8080/
```

## Testing Checklist
1. Start PostgreSQL with the GTFS-like schema applied.
2. Run the application: `./mvnw spring-boot:run`.
3. Verify the root catalog: `curl http://localhost:8080/`.
4. Exercise CRUD flows for key resources (e.g., create a route → trip → stop times → realtime events).

## Notes & Limitations
- The application currently runs without authentication or authorization. Configure Spring Security before exposing the API publicly.
- `spring.jpa.hibernate.ddl-auto=update` will adjust your schema to match entity definitions (including widening text columns to 255). Disable or change this behavior if schema drift is undesirable.
- Spring Data REST payloads are generic; introduce DTOs/controllers if a tailored contract is required.
