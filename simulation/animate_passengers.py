import math
from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, FFMpegWriter, PillowWriter
import numpy as np
import pandas as pd


BASE_DIR = Path(__file__).resolve().parent
SIM_PATH = BASE_DIR / "simulated_route.csv"
PASSENGER_PATH = BASE_DIR / "synthetic_passengers.csv"

# Change to "train", "bus", or None to control the viewport
FOCUS_VEHICLE = "train"

if not SIM_PATH.exists():
    raise FileNotFoundError(f"Missing simulated route data: {SIM_PATH}")
if not PASSENGER_PATH.exists():
    raise FileNotFoundError(
        f"Missing passenger data: {PASSENGER_PATH}. Run generate_passenger_tracks.py first."
    )

route_df = pd.read_csv(SIM_PATH)
pass_df = pd.read_csv(PASSENGER_PATH)

passenger_by_time = {
    int(t): frame.reset_index(drop=True)
    for t, frame in pass_df.groupby("time_sec", sort=True)
}

if FOCUS_VEHICLE == "train":
    lat_src = [route_df["train_lat"].dropna().values]
    lon_src = [route_df["train_lon"].dropna().values]
elif FOCUS_VEHICLE == "bus":
    lat_src = [route_df["bus_lat"].dropna().values]
    lon_src = [route_df["bus_lon"].dropna().values]
else:
    lat_src = [route_df["bus_lat"].dropna().values, route_df["train_lat"].dropna().values]
    lon_src = [route_df["bus_lon"].dropna().values, route_df["train_lon"].dropna().values]

all_lats = np.concatenate(lat_src)
all_lons = np.concatenate(lon_src)
lat_min, lat_max = float(np.nanmin(all_lats)), float(np.nanmax(all_lats))
lon_min, lon_max = float(np.nanmin(all_lons)), float(np.nanmax(all_lons))

pad_lat = (lat_max - lat_min) * 0.05 if lat_max > lat_min else 0.001
pad_lon = (lon_max - lon_min) * 0.05 if lon_max > lon_min else 0.001

fig, ax = plt.subplots(figsize=(8, 6))
ax.set_xlabel("Longitude")
ax.set_ylabel("Latitude")
ax.set_xlim(lon_min - pad_lon, lon_max + pad_lon)
ax.set_ylim(lat_min - pad_lat, lat_max + pad_lat)
ax.set_aspect("equal", adjustable="box")
ax.set_title("Vehicles and passengers over time")

bus_point, = ax.plot([], [], marker="o", markersize=7, color="#ff7f0e", linestyle="None", label="Bus")
train_point, = ax.plot([], [], marker="s", markersize=7, color="#1f77b4", linestyle="None", label="Train")
bus_tail, = ax.plot([], [], linewidth=1.2, color="#ff7f0e", alpha=0.6)
train_tail, = ax.plot([], [], linewidth=1.2, color="#1f77b4", alpha=0.6)
passenger_scatter = ax.scatter([], [], s=40, alpha=0.55, edgecolors="none")

ax.legend(loc="lower right")

bus_lat = route_df["bus_lat"].values.astype(float)
bus_lon = route_df["bus_lon"].values.astype(float)
train_lat = route_df["train_lat"].values.astype(float)
train_lon = route_df["train_lon"].values.astype(float)

frame_times = route_df["time_sec"].astype(int).values
time_labels = route_df["time_hms"].astype(str).values

COLOR_MAP = {"bus": "#ffbb78", "train": "#9edae5"}

time_text = ax.text(0.02, 0.97, "", transform=ax.transAxes, va="top", fontsize=12)
count_text = ax.text(0.02, 0.90, "", transform=ax.transAxes, va="top", fontsize=10, color="gray")


def build_scatter_payload(frame_df: pd.DataFrame):
    if frame_df is None or frame_df.empty:
        return np.empty((0, 2)), [], np.array([])

    if FOCUS_VEHICLE in ("train", "bus"):
        frame_df = frame_df[frame_df["vehicle_type"] == FOCUS_VEHICLE]
        if frame_df.empty:
            return np.empty((0, 2)), [], np.array([])
    offsets = frame_df[["lon", "lat"]].to_numpy()
    colors = [COLOR_MAP.get(v, "#cccccc") for v in frame_df["vehicle_type"]]
    sizes = 160 - np.clip(frame_df["accuracy_m"].to_numpy(), 5, 120)
    sizes = np.clip(sizes, 20, 140)
    return offsets, colors, sizes


def init_anim():
    bus_point.set_data([], [])
    train_point.set_data([], [])
    bus_tail.set_data([], [])
    train_tail.set_data([], [])
    passenger_scatter.set_offsets(np.empty((0, 2)))
    passenger_scatter.set_sizes([])
    passenger_scatter.set_color([])
    time_text.set_text("")
    count_text.set_text("")
    return bus_point, train_point, bus_tail, train_tail, passenger_scatter, time_text, count_text


def update(frame_idx: int):
    t_sec = int(frame_times[frame_idx])

    bx, by = bus_lon[frame_idx], bus_lat[frame_idx]
    if math.isfinite(bx) and math.isfinite(by):
        bus_point.set_data([bx], [by])
        bus_point.set_visible(True)
    else:
        bus_point.set_visible(False)

    tx, ty = train_lon[frame_idx], train_lat[frame_idx]
    if math.isfinite(tx) and math.isfinite(ty):
        train_point.set_data([tx], [ty])
        train_point.set_visible(True)
    else:
        train_point.set_visible(False)

    if frame_idx > 0:
        bus_tail.set_data(route_df["bus_lon"].iloc[: frame_idx + 1], route_df["bus_lat"].iloc[: frame_idx + 1])
        train_tail.set_data(route_df["train_lon"].iloc[: frame_idx + 1], route_df["train_lat"].iloc[: frame_idx + 1])
    else:
        bus_tail.set_data([], [])
        train_tail.set_data([], [])

    frame_pass = passenger_by_time.get(t_sec)
    offsets, colors, sizes = build_scatter_payload(frame_pass)
    passenger_scatter.set_offsets(offsets)
    passenger_scatter.set_color(colors)
    passenger_scatter.set_sizes(sizes if len(sizes) else [])

    time_text.set_text(time_labels[frame_idx])
    count_text.set_text(f"passengers: {len(frame_pass) if frame_pass is not None else 0}")

    return bus_point, train_point, bus_tail, train_tail, passenger_scatter, time_text, count_text


anim = FuncAnimation(
    fig,
    update,
    frames=len(route_df),
    init_func=init_anim,
    interval=80,
    blit=True,
)

mp4_path = BASE_DIR / "passenger_animation.mp4"
gif_path = BASE_DIR / "passenger_animation.gif"

try:
    anim.save(mp4_path, writer=FFMpegWriter(fps=15, bitrate=2000))
    print(f"Saved MP4 -> {mp4_path}")
except Exception as exc:
    print(f"MP4 save failed: {exc}")

try:
    anim.save(gif_path, writer=PillowWriter(fps=12))
    print(f"Saved GIF -> {gif_path}")
except Exception as exc:
    print(f"GIF save failed: {exc}")

plt.show()
