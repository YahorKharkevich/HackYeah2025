-- =======================
-- GTFS-like core
-- =======================hackyeach

CREATE TABLE stops (
  stop_id    TEXT PRIMARY KEY,
  stop_name  TEXT NOT NULL,
  stop_lat   DOUBLE PRECISION,
  stop_lon   DOUBLE PRECISION
);

CREATE TABLE routes (
  route_id   TEXT PRIMARY KEY
);

CREATE TABLE trips (
  trip_id    BIGSERIAL PRIMARY KEY,
  route_id   TEXT NOT NULL REFERENCES routes(route_id),
  start_time TIMESTAMPTZ NOT NULL
);

CREATE INDEX trips_route_time_idx ON trips(route_id, start_time DESC);

CREATE TABLE stop_times (
  trip_id         BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
  stop_sequence   INT    NOT NULL,
  stop_id         TEXT   NOT NULL REFERENCES stops(stop_id),
  arrival_time    INT    NOT NULL DEFAULT 0,  -- сек с полуночи (может быть >86400)
  departure_time  INT    NOT NULL DEFAULT 0,
  PRIMARY KEY (trip_id, stop_sequence),
  UNIQUE (trip_id, stop_id)
);

CREATE INDEX stop_times_by_stop ON stop_times (stop_id, trip_id);

CREATE TABLE calendar (
  service_id  TEXT PRIMARY KEY,
  monday      BOOLEAN NOT NULL,
  tuesday     BOOLEAN NOT NULL,
  wednesday   BOOLEAN NOT NULL,
  thursday    BOOLEAN NOT NULL,
  friday      BOOLEAN NOT NULL,
  saturday    BOOLEAN NOT NULL,
  sunday      BOOLEAN NOT NULL,
  start_date  DATE   NOT NULL,
  end_date    DATE   NOT NULL
);

ALTER TABLE trips
  ADD COLUMN service_id TEXT NOT NULL REFERENCES calendar(service_id);

-- =======================
-- Users
-- =======================

CREATE TABLE users (
  user_id      BIGSERIAL PRIMARY KEY,
  trust_level  DOUBLE PRECISION NOT NULL DEFAULT 0.5
);

-- =======================
-- Realtime: текущие ТС (срез "как VehiclePositions" из GTFS-RT)
-- =======================

CREATE TABLE vehicle_positions_current (
  vehicle_no      TEXT PRIMARY KEY,                          -- PK = vehicle_no
  trip_id         BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,  -- привязка к рейсу
  ts              TIMESTAMPTZ NOT NULL,
  last_stop_ts             TIMESTAMPTZ NOT NULL,
  lat             DOUBLE PRECISION,
  lon             DOUBLE PRECISION,
  speed_mps       DOUBLE PRECISION,
  bearing_deg     DOUBLE PRECISION,
  gps_accuracy_m  DOUBLE PRECISION
);

CREATE INDEX vpc_trip_ts_idx ON vehicle_positions_current(trip_id, ts DESC);

-- =======================
-- Realtime events (разделённые таблицы) + ссылка на vehicle_no
-- =======================

-- 1) Геособытия (live / single-time)
CREATE TABLE exact_trip_event_geo_location (
  event_id        BIGSERIAL PRIMARY KEY,
  trip_id         BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
  vehicle_no      TEXT REFERENCES vehicle_positions_current(vehicle_no),
  user_id         BIGINT REFERENCES users(user_id),
  ts              TIMESTAMPTZ NOT NULL,
  lat             DOUBLE PRECISION,
  lon             DOUBLE PRECISION,
  gps_accuracy_m  DOUBLE PRECISION,
  type            TEXT  -- 'live' | 'single_time'
);

CREATE INDEX ete_geo_trip_ts_idx     ON exact_trip_event_geo_location(trip_id, ts DESC);
CREATE INDEX ete_geo_user_ts_idx     ON exact_trip_event_geo_location(user_id, ts DESC);
CREATE INDEX ete_geo_vehicle_ts_idx  ON exact_trip_event_geo_location(vehicle_no, ts DESC);

-- 2) Табличные события (arrival / departure)
CREATE TABLE exact_trip_event_timetable (
  event_id        BIGSERIAL PRIMARY KEY,
  trip_id         BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
  vehicle_no      TEXT REFERENCES vehicle_positions_current(vehicle_no),
  user_id         BIGINT REFERENCES users(user_id),
  ts              TIMESTAMPTZ NOT NULL,
  lat             DOUBLE PRECISION,
  lon             DOUBLE PRECISION,
  gps_accuracy_m  DOUBLE PRECISION,
  type            TEXT,             -- 'arrival' | 'departure'
  time            TIMESTAMPTZ       -- время, указанное пользователем/табло
);

CREATE INDEX ete_tt_trip_ts_idx      ON exact_trip_event_timetable(trip_id, ts DESC);
CREATE INDEX ete_tt_user_ts_idx      ON exact_trip_event_timetable(user_id, ts DESC);
CREATE INDEX ete_tt_vehicle_ts_idx   ON exact_trip_event_timetable(vehicle_no, ts DESC);

-- 3) Аномалии (traffic_jam / stop / ...)
CREATE TABLE exact_trip_anomaly (
  event_id         BIGSERIAL PRIMARY KEY,
  trip_id          BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
  vehicle_no       TEXT REFERENCES vehicle_positions_current(vehicle_no),
  user_id          BIGINT REFERENCES users(user_id),
  ts               TIMESTAMPTZ NOT NULL,
  lat              DOUBLE PRECISION,
  lon              DOUBLE PRECISION,
  gps_accuracy_m   DOUBLE PRECISION,
  type             TEXT,                   -- 'traffic_jam' | 'stop' | ...
  estimated_delay  DOUBLE PRECISION        -- мин
);

CREATE INDEX eta_trip_ts_idx         ON exact_trip_anomaly(trip_id, ts DESC);
CREATE INDEX eta_user_ts_idx         ON exact_trip_anomaly(user_id, ts DESC);
CREATE INDEX eta_vehicle_ts_idx      ON exact_trip_anomaly(vehicle_no, ts DESC);

-- =======================
-- GTFS shapes (exactly like GTFS) + connections
-- =======================

CREATE TABLE shape_ids (
  shape_id TEXT PRIMARY KEY
);

CREATE TABLE shapes (
  shape_id            TEXT NOT NULL REFERENCES shape_ids(shape_id) ON DELETE CASCADE,
  shape_pt_lat        DOUBLE PRECISION NOT NULL,
  shape_pt_lon        DOUBLE PRECISION NOT NULL,
  shape_pt_sequence   INT NOT NULL,
  shape_dist_traveled DOUBLE PRECISION,
  PRIMARY KEY (shape_id, shape_pt_sequence)
);

CREATE INDEX shapes_by_shape ON shapes(shape_id, shape_pt_sequence);

ALTER TABLE trips
  ADD COLUMN shape_id TEXT REFERENCES shape_ids(shape_id);
