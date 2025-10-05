-- =======================
-- GTFS-like core
-- =======================

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
                       start_time TIMESTAMPTZ NOT NULL,
                       vehicle_no TEXT
);

CREATE INDEX trips_route_time_idx ON trips(route_id, start_time DESC);

CREATE TABLE stop_times (
                            trip_id         BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
                            stop_sequence   INT    NOT NULL,
                            stop_id         TEXT   NOT NULL REFERENCES stops(stop_id),
                            arrival_time    INT    NOT NULL DEFAULT 0,
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

-- =======================
-- Users
-- =======================

CREATE TABLE users (
                       user_id      BIGSERIAL PRIMARY KEY,
                       trust_level  DOUBLE PRECISION NOT NULL DEFAULT 0.5
);

-- =======================
-- Realtime (split by event kind)
-- =======================

-- 1) Геособытия (live / single-time)
CREATE TABLE exact_trip_event_geo_location (
                                               event_id        BIGSERIAL PRIMARY KEY,
                                               trip_id         BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
                                               user_id         BIGINT REFERENCES users(user_id),
                                               ts              TIMESTAMPTZ NOT NULL,
                                               lat             DOUBLE PRECISION,
                                               lon             DOUBLE PRECISION,
                                               gps_accuracy_m  DOUBLE PRECISION,
                                               type            TEXT                           -- напр. 'live' | 'single_time'
);

CREATE INDEX exact_trip_event_geo_location_trip_ts_idx
    ON exact_trip_event_geo_location(trip_id, ts DESC);
CREATE INDEX exact_trip_event_geo_location_user_ts_idx
    ON exact_trip_event_geo_location(user_id, ts DESC);

-- 2) Табличные события (arrival / departure)
CREATE TABLE exact_trip_event_timetable (
                                            event_id        BIGSERIAL PRIMARY KEY,
                                            trip_id         BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
                                            user_id         BIGINT REFERENCES users(user_id),
                                            ts              TIMESTAMPTZ NOT NULL,
                                            lat             DOUBLE PRECISION,
                                            lon             DOUBLE PRECISION,
                                            gps_accuracy_m  DOUBLE PRECISION,
                                            type            TEXT,                          -- напр. 'arrival' | 'departure'
                                            time            TIMESTAMPTZ                    -- время, указанное пользователем/табло
);

CREATE INDEX exact_trip_event_timetable_trip_ts_idx
    ON exact_trip_event_timetable(trip_id, ts DESC);
CREATE INDEX exact_trip_event_timetable_user_ts_idx
    ON exact_trip_event_timetable(user_id, ts DESC);

-- 3) Аномалии (traffic jam / stop / etc.)
CREATE TABLE exact_trip_anomaly (
                                    event_id         BIGSERIAL PRIMARY KEY,
                                    trip_id          BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
                                    user_id          BIGINT REFERENCES users(user_id),
                                    ts               TIMESTAMPTZ NOT NULL,
                                    lat              DOUBLE PRECISION,
                                    lon              DOUBLE PRECISION,
                                    gps_accuracy_m   DOUBLE PRECISION,
                                    type             TEXT,                         -- напр. 'traffic_jam' | 'stop' | ...
                                    estimated_delay  DOUBLE PRECISION              -- оценка задержки (в минутах)
);

CREATE INDEX exact_trip_anomaly_trip_ts_idx
    ON exact_trip_anomaly(trip_id, ts DESC);
CREATE INDEX exact_trip_anomaly_user_ts_idx
    ON exact_trip_anomaly(user_id, ts DESC);

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
