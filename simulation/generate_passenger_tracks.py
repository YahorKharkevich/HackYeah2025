import hashlib
import math
from pathlib import Path
from typing import Dict

import numpy as np
import pandas as pd


def load_tracks(sim_csv: Path) -> Dict[str, pd.DataFrame]:
    df = pd.read_csv(sim_csv)

    bus = df[
        [
            "time_sec",
            "time_hms",
            "bus_lat",
            "bus_lon",
            "bus_trip_id_at_time",
        ]
    ].rename(
        columns={
            "bus_lat": "lat",
            "bus_lon": "lon",
            "bus_trip_id_at_time": "trip_id",
        }
    )
    bus["vehicle"] = "bus"

    train = df[
        [
            "time_sec",
            "time_hms",
            "train_lat",
            "train_lon",
            "train_trip_id_at_time",
        ]
    ].rename(
        columns={
            "train_lat": "lat",
            "train_lon": "lon",
            "train_trip_id_at_time": "trip_id",
        }
    )
    train["vehicle"] = "train"

    return {"bus": bus, "train": train}


def jitter_track(
    track: pd.DataFrame,
    rng: np.random.Generator,
    acc_mean: float,
    drop_prob: float,
    jump_prob: float,
) -> pd.DataFrame:
    lat = track["lat"].to_numpy(dtype=float)
    lon = track["lon"].to_numpy(dtype=float)
    mask = np.ones(len(track), dtype=bool)

    for i in range(len(track)):
        if np.isnan(lat[i]) or np.isnan(lon[i]):
            mask[i] = False
            continue
        if rng.random() < drop_prob:
            mask[i] = False
            continue

        # базовый изотропный шум
        radius = rng.normal(0, acc_mean)
        angle = rng.uniform(0, 2 * math.pi)
        m2deg_lat = 1 / 111_000
        m2deg_lon = 1 / (111_000 * max(math.cos(math.radians(lat[i])), 1e-6))
        lat[i] += radius * math.sin(angle) * m2deg_lat
        lon[i] += radius * math.cos(angle) * m2deg_lon

        # редкий скачок
        if rng.random() < jump_prob:
            jump = rng.normal(60, 20)  # метров
            angle = rng.uniform(0, 2 * math.pi)
            lat[i] += jump * math.sin(angle) * m2deg_lat
            lon[i] += jump * math.cos(angle) * m2deg_lon

    df = track.copy()
    df["lat"] = lat
    df["lon"] = lon
    df = df[mask].reset_index(drop=True)
    return df


def compute_speed_bearing(df: pd.DataFrame) -> pd.DataFrame:
    speed = np.full(len(df), np.nan)
    bearing = np.full(len(df), np.nan)

    lat_rad = np.radians(df["lat"].to_numpy())
    lon_rad = np.radians(df["lon"].to_numpy())
    times = df["time_sec"].to_numpy()

    for i in range(1, len(df)):
        dt = times[i] - times[i - 1]
        if dt <= 0 or any(
            np.isnan(val) for val in (lat_rad[i], lon_rad[i], lat_rad[i - 1], lon_rad[i - 1])
        ):
            continue

        dlat = lat_rad[i] - lat_rad[i - 1]
        dlon = lon_rad[i] - lon_rad[i - 1]
        a = math.sin(dlat / 2) ** 2 + math.cos(lat_rad[i - 1]) * math.cos(lat_rad[i]) * math.sin(dlon / 2) ** 2
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(max(1 - a, 0)))
        dist_m = 6_371_000 * c
        speed[i] = dist_m / dt

        y = math.sin(dlon) * math.cos(lat_rad[i])
        x = math.cos(lat_rad[i - 1]) * math.sin(lat_rad[i]) - math.sin(lat_rad[i - 1]) * math.cos(lat_rad[i]) * math.cos(dlon)
        brng = math.degrees(math.atan2(y, x))
        bearing[i] = (brng + 360) % 360

    df["speed_mps"] = speed
    df["bearing_deg"] = bearing
    return df


def make_user_hash(vehicle: str, idx: int, seed: int) -> str:
    return hashlib.sha1(f"{vehicle}-{idx}-{seed}".encode()).hexdigest()[:16]


def generate_passengers(
    sim_csv: Path,
    n_bus: int = 15,
    n_train: int = 25,
    seed: int = 42,
) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    tracks = load_tracks(sim_csv)
    passengers = []

    for veh_type, base_track in tracks.items():
        count = n_bus if veh_type == "bus" else n_train
        for idx in range(count):
            acc_mean = rng.uniform(8, 25) if veh_type == "bus" else rng.uniform(5, 15)
            drop_prob = rng.uniform(0.1, 0.3) if veh_type == "bus" else rng.uniform(0.05, 0.2)
            jump_prob = 0.12 if veh_type == "bus" else 0.05

            jittered = jitter_track(base_track, rng, acc_mean, drop_prob, jump_prob)
            jittered = compute_speed_bearing(jittered)

            jittered["accuracy_m"] = rng.normal(acc_mean, acc_mean * 0.3, size=len(jittered)).clip(3, 60)
            jittered["provider"] = rng.choice(["gps", "fused"], size=len(jittered), p=[0.7, 0.3])
            jittered["confidence"] = np.where(jittered["speed_mps"].isna(), 0.4, 0.8)
            jittered["user_id_hash"] = make_user_hash(veh_type, idx, seed)
            jittered["vehicle_type"] = veh_type
            jittered["trip_id_source"] = jittered["trip_id"]

            passengers.append(
                jittered[
                    [
                        "user_id_hash",
                        "vehicle_type",
                        "trip_id_source",
                        "time_sec",
                        "time_hms",
                        "lat",
                        "lon",
                        "accuracy_m",
                        "speed_mps",
                        "bearing_deg",
                        "provider",
                        "confidence",
                    ]
                ]
            )

    return pd.concat(passengers, ignore_index=True)


def main():
    base_dir = Path(__file__).resolve().parent
    sim_csv = base_dir / "simulated_route.csv"
    if not sim_csv.exists():
        raise FileNotFoundError(f"Simulated route file not found: {sim_csv}")

    df_passengers = generate_passengers(sim_csv)
    out_path = base_dir / "synthetic_passengers.csv"
    df_passengers.to_csv(out_path, index=False)
    print(f"Saved {len(df_passengers)} synthetic passenger points to {out_path}")


if __name__ == "__main__":
    main()
