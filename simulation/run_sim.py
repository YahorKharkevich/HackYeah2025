from __future__ import annotations

import argparse
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

import pandas as pd
import requests


BASE_DEFAULT = "http://localhost:8080"
HEADERS = {
    "Accept": "application/hal+json",
    "Content-Type": "application/json",
}


def post_geo_event(
    trip_id: str,
    ts_iso: str,
    lat: float,
    lon: float,
    user_id: int = 1,
    accuracy_m: float = 10.0,
    base: str = BASE_DEFAULT,
    timeout_s: int = 15,
) -> dict:
    """
    Создаёт geo-event через Spring Data REST.
    trip_id  — идентификатор рейса (как в /trips/{id})
    ts_iso   — ISO-время в UTC, например '2024-10-05T09:22:00Z'
    lat/lon  — координаты WGS84
    user_id  — ID пользователя (должен существовать в /users/{id})
    """
    payload = {
        "trip": f"{base}/trips/{trip_id}",  # HAL-ссылка на trip
        "user": f"{base}/users/{user_id}",  # HAL-ссылка на user
        "timestamp": ts_iso,
        "latitude": float(lat),
        "longitude": float(lon),
        "gpsAccuracyMeters": float(accuracy_m),
        "type": "geo",
    }
    resp = requests.post(f"{BASE_DEFAULT}/geo-events", headers=HEADERS, json=payload, timeout=timeout_s)
    resp.raise_for_status()
    data = resp.json()
    event_id = data.get("id") or data.get("event_id")
    print(f"created geo-event id={event_id} trip={trip_id} ts={ts_iso}")
    return data


def to_iso_utc(service_date: str, time_hms: str) -> str:
    # service_date 'YYYY-MM-DD', time_hms 'HH:MM:SS'
    return f"{service_date}T{time_hms}Z"


def main():
    base_dir = Path(__file__).resolve().parent
    p = argparse.ArgumentParser(description="Post geo events for each simulated row (bus + train)")
    p.add_argument("--csv", default=str(base_dir / "simulated_route.csv"))
    p.add_argument(
        "--service-date",
        default=datetime.now(timezone.utc).date().isoformat(),
        help="Service date in UTC (YYYY-MM-DD)",
    )
    p.add_argument("--base", default=BASE_DEFAULT, help="API base URL")
    p.add_argument("--accuracy", type=float, default=10.0, help="gpsAccuracyMeters for all posts")
    p.add_argument("--user-bus", type=int, default=1, help="User id for bus events")
    p.add_argument("--user-train", type=int, default=2, help="User id for train events")
    p.add_argument("--sleep-ms", type=int, default=0, help="Sleep between row posts (milliseconds)")
    p.add_argument("--limit", type=int, default=0, help="Limit number of rows (0 = all)")
    p.add_argument("--timeout", type=int, default=15, help="HTTP timeout seconds")
    p.add_argument("--only", choices=["bus", "train", "both"], default="both")
    p.add_argument("--dry-run", action="store_true", help="Do not POST, just print payloads")
    args = p.parse_args()

    df = pd.read_csv(args.csv)

    # Columns expected: time_hms, bus_lat, bus_lon, train_lat, train_lon,
    # optionally bus_trip_id_at_time, train_trip_id_at_time
    have_bus_trip_col = "bus_trip_id_at_time" in df.columns
    have_train_trip_col = "train_trip_id_at_time" in df.columns

    rows_iter = df.itertuples(index=False)
    sent = 0
    for row in rows_iter:
        # Access by attribute to support itertuples
        time_hms = getattr(row, "time_hms")
        ts_iso = to_iso_utc(args.service_date, str(time_hms))

        if args.only in ("bus", "both"):
            bus_lat = getattr(row, "bus_lat", float("nan"))
            bus_lon = getattr(row, "bus_lon", float("nan"))
            if pd.notna(bus_lat) and pd.notna(bus_lon):
                bus_trip = (
                    getattr(row, "bus_trip_id_at_time") if have_bus_trip_col else None
                ) or ""
                try:
                    post_geo_event(
                        trip_id=str(bus_trip),
                        ts_iso=ts_iso,
                        lat=float(bus_lat),
                        lon=float(bus_lon),
                        user_id=-1,
                        accuracy_m=-1,
                        base=BASE_DEFAULT,
                        timeout_s=10,
                    )
                    sent += 1
                except Exception as e:
                    print(f"bus post failed: {e}", file=sys.stderr)

        if args.only in ("train", "both"):
            train_lat = getattr(row, "train_lat", float("nan"))
            train_lon = getattr(row, "train_lon", float("nan"))
            if pd.notna(train_lat) and pd.notna(train_lon):
                train_trip = (
                    getattr(row, "train_trip_id_at_time") if have_train_trip_col else None
                ) or ""
                try:
                    post_geo_event(
                        trip_id=str(train_trip),
                        ts_iso=ts_iso,
                        lat=float(train_lat),
                        lon=float(train_lon),
                        user_id=-1,
                        accuracy_m=10,
                        base=BASE_DEFAULT,
                        timeout_s=10,
                    )
                    sent += 1
                except Exception as e:
                    print(f"train post failed: {e}", file=sys.stderr)

        if args.limit and sent >= args.limit:
            break
        if args.sleep_ms > 0:
            time.sleep(args.sleep_ms / 1000.0)

    print(f"done, posted {sent} event(s)")


if __name__ == "__main__":
    
    main()
