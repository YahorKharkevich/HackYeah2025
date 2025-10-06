-- Seed schedules for 3 lines (routes), each with 3 trips (buses)
-- Compatible with schema.sql in this repo (PostgreSQL)

BEGIN;

-- Clean previous demo data
TRUNCATE TABLE stop_times RESTART IDENTITY CASCADE;
TRUNCATE TABLE trips RESTART IDENTITY CASCADE;
TRUNCATE TABLE routes RESTART IDENTITY CASCADE;
TRUNCATE TABLE stops RESTART IDENTITY CASCADE;
TRUNCATE TABLE calendar RESTART IDENTITY CASCADE;

-- Calendar: DAILY service active the whole year (all days on)
INSERT INTO calendar (
  service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, start_date, end_date
) VALUES
  ('DAILY', true, true, true, true, true, true, true, DATE '2025-01-01', DATE '2025-12-31');

-- Routes (3 lines)
INSERT INTO routes (route_id) VALUES
  ('R1'), ('R2'), ('R3');

-- Stops for each route (5 per route). Coordinates are sample values.
INSERT INTO stops (stop_id, stop_name, stop_lat, stop_lon) VALUES
  -- R1 stops
  ('R1_S1', 'R1 - Stop 1', 55.7500, 37.6100),
  ('R1_S2', 'R1 - Stop 2', 55.7525, 37.6150),
  ('R1_S3', 'R1 - Stop 3', 55.7550, 37.6200),
  ('R1_S4', 'R1 - Stop 4', 55.7575, 37.6250),
  ('R1_S5', 'R1 - Stop 5', 55.7600, 37.6300),
  -- R2 stops
  ('R2_S1', 'R2 - Stop 1', 59.9300, 30.3000),
  ('R2_S2', 'R2 - Stop 2', 59.9325, 30.3050),
  ('R2_S3', 'R2 - Stop 3', 59.9350, 30.3100),
  ('R2_S4', 'R2 - Stop 4', 59.9375, 30.3150),
  ('R2_S5', 'R2 - Stop 5', 59.9400, 30.3200),
  -- R3 stops
  ('R3_S1', 'R3 - Stop 1', 56.8300, 60.6000),
  ('R3_S2', 'R3 - Stop 2', 56.8325, 60.6050),
  ('R3_S3', 'R3 - Stop 3', 56.8350, 60.6100),
  ('R3_S4', 'R3 - Stop 4', 56.8375, 60.6150),
  ('R3_S5', 'R3 - Stop 5', 56.8400, 60.6200);

-- Helper: stop sequences per route
WITH r1_trips AS (
  INSERT INTO trips (route_id, start_time, service_id) VALUES
    ('R1', TIMESTAMPTZ '2025-10-06 08:00:00+00', 'DAILY'),
    ('R1', TIMESTAMPTZ '2025-10-06 09:00:00+00', 'DAILY'),
    ('R1', TIMESTAMPTZ '2025-10-06 10:00:00+00', 'DAILY')
  RETURNING trip_id, start_time
), r1_stops AS (
  SELECT * FROM (VALUES
    (1, 'R1_S1'), (2, 'R1_S2'), (3, 'R1_S3'), (4, 'R1_S4'), (5, 'R1_S5')
  ) AS v(seq, stop_id)
)
INSERT INTO stop_times (trip_id, stop_sequence, stop_id, arrival_time, departure_time)
SELECT t.trip_id,
       s.seq AS stop_sequence,
       s.stop_id,
       -- seconds from midnight for trip start + 10 min per hop
       (EXTRACT(EPOCH FROM (t.start_time - date_trunc('day', t.start_time)))::int + (s.seq - 1) * 600) AS arrival_time,
       (EXTRACT(EPOCH FROM (t.start_time - date_trunc('day', t.start_time)))::int + (s.seq - 1) * 600 + 30) AS departure_time
FROM r1_trips t CROSS JOIN r1_stops s
ORDER BY t.trip_id, s.seq;

WITH r2_trips AS (
  INSERT INTO trips (route_id, start_time, service_id) VALUES
    ('R2', TIMESTAMPTZ '2025-10-06 07:00:00+00', 'DAILY'),
    ('R2', TIMESTAMPTZ '2025-10-06 08:00:00+00', 'DAILY'),
    ('R2', TIMESTAMPTZ '2025-10-06 09:00:00+00', 'DAILY')
  RETURNING trip_id, start_time
), r2_stops AS (
  SELECT * FROM (VALUES
    (1, 'R2_S1'), (2, 'R2_S2'), (3, 'R2_S3'), (4, 'R2_S4'), (5, 'R2_S5')
  ) AS v(seq, stop_id)
)
INSERT INTO stop_times (trip_id, stop_sequence, stop_id, arrival_time, departure_time)
SELECT t.trip_id,
       s.seq,
       s.stop_id,
       (EXTRACT(EPOCH FROM (t.start_time - date_trunc('day', t.start_time)))::int + (s.seq - 1) * 600) AS arrival_time,
       (EXTRACT(EPOCH FROM (t.start_time - date_trunc('day', t.start_time)))::int + (s.seq - 1) * 600 + 30) AS departure_time
FROM r2_trips t CROSS JOIN r2_stops s
ORDER BY t.trip_id, s.seq;

WITH r3_trips AS (
  INSERT INTO trips (route_id, start_time, service_id) VALUES
    ('R3', TIMESTAMPTZ '2025-10-06 06:30:00+00', 'DAILY'),
    ('R3', TIMESTAMPTZ '2025-10-06 07:30:00+00', 'DAILY'),
    ('R3', TIMESTAMPTZ '2025-10-06 08:30:00+00', 'DAILY')
  RETURNING trip_id, start_time
), r3_stops AS (
  SELECT * FROM (VALUES
    (1, 'R3_S1'), (2, 'R3_S2'), (3, 'R3_S3'), (4, 'R3_S4'), (5, 'R3_S5')
  ) AS v(seq, stop_id)
)
INSERT INTO stop_times (trip_id, stop_sequence, stop_id, arrival_time, departure_time)
SELECT t.trip_id,
       s.seq,
       s.stop_id,
       (EXTRACT(EPOCH FROM (t.start_time - date_trunc('day', t.start_time)))::int + (s.seq - 1) * 600) AS arrival_time,
       (EXTRACT(EPOCH FROM (t.start_time - date_trunc('day', t.start_time)))::int + (s.seq - 1) * 600 + 30) AS departure_time
FROM r3_trips t CROSS JOIN r3_stops s
ORDER BY t.trip_id, s.seq;

COMMIT;

