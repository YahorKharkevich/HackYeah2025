from __future__ import annotations

import math
from pathlib import Path

import numpy as np
import pandas as pd
import plotly.graph_objects as go
import streamlit as st

BASE_DIR = Path(__file__).resolve().parent
SIM_PATH = BASE_DIR / "simulated_route.csv"
PASS_PATH = BASE_DIR / "synthetic_passengers.csv"

if not SIM_PATH.exists():
    raise FileNotFoundError(
        "simulated_route.csv not found. Run simulation/simulator.py first to generate it."
    )
if not PASS_PATH.exists():
    raise FileNotFoundError(
        "synthetic_passengers.csv not found. Run simulation/generate_passenger_tracks.py first."
    )

@st.cache_data(show_spinner=False)
def load_routes(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path)
    return df

@st.cache_data(show_spinner=False)
def load_passengers(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path)
    return df

route_df = load_routes(SIM_PATH)
pass_df = load_passengers(PASS_PATH)

all_times = np.sort(route_df["time_sec"].unique())
time_to_label = dict(zip(route_df["time_sec"], route_df["time_hms"]))

st.set_page_config(
    page_title="Passenger Tracking Dashboard",
    layout="wide",
)

st.title("Passenger Tracking Dashboard")
st.caption("Interactive Mapbox visualisation of simulated passengers for bus and train trips")

st.sidebar.header("Controls")
vehicle_choice = st.sidebar.multiselect(
    "Vehicle type", options=["bus", "train"], default=["bus", "train"],
)

# Optional overlays of geoservice results
show_train_est = st.sidebar.checkbox("Show train estimate", value=True)
show_bus_est = st.sidebar.checkbox("Show bus estimate", value=False)

window_minutes = st.sidebar.slider(
    "Time window (minutes)", min_value=0.0, max_value=10.0, value=1.0, step=0.5
)

selected_time = st.sidebar.slider(
    "Reference time", min_value=int(all_times.min()), max_value=int(all_times.max()),
    value=int(all_times.min()), step=30,
    format="%d s"
)

# compute window range
window_delta = int(window_minutes * 60)
low_bound = selected_time - window_delta
high_bound = selected_time + window_delta

mask = (pass_df["time_sec"] >= low_bound) & (pass_df["time_sec"] <= high_bound)
if vehicle_choice:
    mask &= pass_df["vehicle_type"].isin(vehicle_choice)
filtered_pass = pass_df[mask].copy()
filtered_pass["time_hms"] = filtered_pass["time_sec"].map(time_to_label)

active_vehicle_mask = route_df["time_sec"] == selected_time
active_bus = route_df.loc[active_vehicle_mask, ["bus_lat", "bus_lon", "time_hms"]].dropna().head(1)
active_train = route_df.loc[active_vehicle_mask, ["train_lat", "train_lon", "time_hms"]].dropna().head(1)

# Determine map center
if not filtered_pass.empty:
    center_lat = filtered_pass["lat"].mean()
    center_lon = filtered_pass["lon"].mean()
else:
    center_lat = route_df[["bus_lat", "train_lat"]].mean(axis=1).mean()
    center_lon = route_df[["bus_lon", "train_lon"]].mean(axis=1).mean()

fig = go.Figure()

# Routes
bus_line = route_df[["bus_lat", "bus_lon"]].dropna()
if not bus_line.empty:
    fig.add_trace(
        go.Scattermapbox(
            lat=bus_line["bus_lat"],
            lon=bus_line["bus_lon"],
            mode="lines",
            line=dict(color="#ff7f0e", width=2),
            name="Bus route",
            hoverinfo="skip",
        )
    )

train_line = route_df[["train_lat", "train_lon"]].dropna()
if not train_line.empty:
    fig.add_trace(
        go.Scattermapbox(
            lat=train_line["train_lat"],
            lon=train_line["train_lon"],
            mode="lines",
            line=dict(color="#1f77b4", width=2),
            name="Train route",
            hoverinfo="skip",
        )
    )

# Vehicle current markers
if not active_bus.empty:
    fig.add_trace(
        go.Scattermapbox(
            lat=active_bus["bus_lat"],
            lon=active_bus["bus_lon"],
            marker=dict(size=14, color="#ff7f0e", symbol="bus"),
            name="Bus (current)",
            text=active_bus["time_hms"],
        )
    )

if not active_train.empty:
    fig.add_trace(
        go.Scattermapbox(
            lat=active_train["train_lat"],
            lon=active_train["train_lon"],
            marker=dict(size=14, color="#1f77b4", symbol="rail"),
            name="Train (current)",
            text=active_train["time_hms"],
        )
    )

# Passenger scatter
if not filtered_pass.empty:
    color_map = {"bus": "#ffbb78", "train": "#9edae5"}
    fig.add_trace(
        go.Scattermapbox(
            lat=filtered_pass["lat"],
            lon=filtered_pass["lon"],
            mode="markers",
            marker=dict(
                size=np.clip(18 - filtered_pass["accuracy_m"] / 8, 6, 16),
                color=[color_map.get(v, "#cccccc") for v in filtered_pass["vehicle_type"]],
                opacity=0.7,
            ),
            name="Passengers",
            text=[
                (
                    f"User: {row.user_id_hash}<br>"
                    f"Vehicle: {row.vehicle_type}<br>"
                    f"Trip: {row.trip_id_source}<br>"
                    f"Time: {row.time_hms}<br>"
                    f"Speed: {row.speed_mps:.1f} m/s"
                )
                if not math.isnan(row.speed_mps)
                else (
                    f"User: {row.user_id_hash}<br>"
                    f"Vehicle: {row.vehicle_type}<br>"
                    f"Trip: {row.trip_id_source}<br>"
                    f"Time: {row.time_hms}<br>"
                    "Speed: n/a"
                )
                for row in filtered_pass.itertuples()
            ],
            hoverinfo="text",
        )
    )

# Overlay geoservice estimates (filtered by window)
def add_estimate_trace(est_path: Path, name_prefix: str, color: str):
    if not est_path.exists():
        return
    est = pd.read_csv(est_path)
    if est.empty:
        return
    est_win = est[(est["t_bin"] >= low_bound) & (est["t_bin"] <= high_bound)].copy()
    if est_win.empty:
        return
    # group by vehicle_id to draw separate lines
    for vid, grp in est_win.groupby("vehicle_id"):
        fig.add_trace(
            go.Scattermapbox(
                lat=grp["lat"],
                lon=grp["lon"],
                mode="lines+markers",
                line=dict(color=color, width=3),
                marker=dict(size=6, color=color),
                name=f"{name_prefix} est #{vid}",
                hovertext=[f"t={t}\nusers={n}" for t, n in zip(grp["t_bin"], grp["n_users"])],
                hoverinfo="text",
            )
        )

if show_train_est:
    add_estimate_trace(BASE_DIR / "vehicles_estimate.csv", "Train", "#2ca02c")
if show_bus_est:
    add_estimate_trace(BASE_DIR / "vehicles_estimate_bus.csv", "Bus", "#d62728")

fig.update_layout(
    mapbox=dict(
        style="carto-positron",
        center=dict(lat=center_lat, lon=center_lon),
        zoom=12,
    ),
    margin=dict(l=10, r=10, t=30, b=10),
    legend=dict(orientation="h", yanchor="bottom", y=0.01),
)

st.plotly_chart(fig, use_container_width=True)

col1, col2, col3 = st.columns(3)
col1.metric("Selected time", time_to_label.get(selected_time, ""))
col2.metric("Passengers in window", len(filtered_pass))
if not filtered_pass.empty:
    median_speed = filtered_pass["speed_mps"].median(skipna=True)
    col3.metric("Median speed", f"{median_speed:.1f} m/s")
else:
    col3.metric("Median speed", "n/a")

st.subheader("Passenger records")
st.dataframe(
    filtered_pass[
        [
            "user_id_hash",
            "vehicle_type",
            "trip_id_source",
            "time_hms",
            "lat",
            "lon",
            "accuracy_m",
            "speed_mps",
            "bearing_deg",
            "provider",
        ]
    ].sort_values(["time_sec", "user_id_hash"]),
    use_container_width=True,
)

st.caption("Use the slider to scrub the timeline, adjust the window to visualise short slices or longer history.")
