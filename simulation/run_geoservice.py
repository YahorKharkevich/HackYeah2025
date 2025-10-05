import argparse
import sys
from pathlib import Path
import pandas as pd

# import cluster pipeline
sys.path.append(str(Path(__file__).resolve().parent.parent))  # repo root on path
import services.geoservice as geoservice  # noqa: E402

# import GTFS reader from simulation/example_route.py
SIM_DIR = Path(__file__).resolve().parent
sys.path.append(str(SIM_DIR))
from example_route import read_gtfs  # type: ignore  # noqa: E402


def find_shape_id(feed: dict, trip_id: str) -> str:
    trips = feed.get("trips", pd.DataFrame())
    if trips.empty:
        raise RuntimeError("trips.txt is empty in GTFS feed")
    row = trips[trips["trip_id"].astype(str) == str(trip_id)]
    if row.empty:
        raise RuntimeError(f"trip_id {trip_id} not found in GTFS trips")
    shape_id = row.iloc[0].get("shape_id", "")
    if not shape_id:
        raise RuntimeError(f"trip_id {trip_id} has no shape_id in trips.txt")
    return str(shape_id)

def fallback_shapes_from_stops(feed: dict, trip_id: str) -> pd.DataFrame:
    st = feed.get("stop_times", pd.DataFrame()).copy()
    stops = feed.get("stops", pd.DataFrame()).copy()
    if st.empty or stops.empty:
        return pd.DataFrame(columns=["shape_id","shape_pt_lat","shape_pt_lon","shape_pt_sequence","shape_dist_traveled"])
    st = st[st["trip_id"].astype(str) == str(trip_id)].copy()
    if st.empty:
        return pd.DataFrame(columns=["shape_id","shape_pt_lat","shape_pt_lon","shape_pt_sequence","shape_dist_traveled"])
    st["stop_sequence"] = pd.to_numeric(st["stop_sequence"], errors="coerce").astype(int)
    st = st.sort_values("stop_sequence")
    stops = stops[["stop_id","stop_lat","stop_lon"]].copy()
    stops["stop_id"] = stops["stop_id"].astype(str)
    st["stop_id"] = st["stop_id"].astype(str)
    st = st.merge(stops, on="stop_id", how="left")
    st = st.dropna(subset=["stop_lat","stop_lon"])  # safety
    shp_id = f"fallback_{trip_id}"
    out = pd.DataFrame({
        "shape_id": shp_id,
        "shape_pt_lat": st["stop_lat"].astype(float).values,
        "shape_pt_lon": st["stop_lon"].astype(float).values,
        "shape_pt_sequence": range(len(st)),
        "shape_dist_traveled": 0,
    })
    return out


def main():
    ap = argparse.ArgumentParser(description="Run geoservice clustering on synthetic passengers")
    ap.add_argument("--passengers", default=str(SIM_DIR / "synthetic_passengers.csv"))
    ap.add_argument("--vehicle", choices=["train", "bus"], default="train")
    ap.add_argument("--gtfs", default=str(SIM_DIR / "kml-ska-gtfs.zip"))
    ap.add_argument("--trip-id", default="2024_2025_813171")
    ap.add_argument("--shape-id", default="", help="Override shape_id (otherwise taken from trips.txt)")
    ap.add_argument("--bin-sec", type=int, default=10)
    ap.add_argument("--eps-m", type=float, default=80.0)
    ap.add_argument("--min-pts", type=int, default=3)
    ap.add_argument("--xtrack-max-m", type=float, default=80.0)
    ap.add_argument("--acc-max-m", type=float, default=80.0)
    ap.add_argument("--vmax-mps", type=float, default=30.0)
    ap.add_argument("--gate-m", type=float, default=200.0)
    ap.add_argument("--ttl-bins", type=int, default=6)
    ap.add_argument("--out-vehicles", default=str(SIM_DIR / "vehicles_estimate.csv"))
    ap.add_argument("--out-assign", default=str(SIM_DIR / "vehicle_assignments.csv"))
    args = ap.parse_args()

    points = pd.read_csv(args.passengers)
    # filter by vehicle type
    points = points[points["vehicle_type"] == args.vehicle].copy()
    if points.empty:
        raise SystemExit(f"No passenger rows for vehicle_type='{args.vehicle}' in {args.passengers}")

    # prepare schema expected by geoservice: columns user_id_hash, t, lat, lon, accuracy_m(optional)
    points = points.rename(columns={"time_sec": "t"})
    if "t" not in points.columns:
        raise SystemExit("Input must have time_sec column")

    # load GTFS shapes
    feed = read_gtfs(args.gtfs)
    shapes_df = feed.get("shapes", pd.DataFrame())
    shape_id = args.shape_id
    if not shape_id:
        try:
            shape_id = find_shape_id(feed, args.trip_id)
        except Exception:
            shape_id = ""
    # fallback if shapes empty or missing this shape
    if shapes_df.empty or shapes_df[shapes_df["shape_id"].astype(str) == shape_id].empty:
        fb = fallback_shapes_from_stops(feed, args.trip_id)
        if fb.empty:
            raise SystemExit("Cannot build fallback shape from stop_times/stops")
        shapes_df = fb
        shape_id = fb.iloc[0]["shape_id"]

    vehicles_df, assign_df = geoservice.cluster_users_into_vehicles(
        points_df=points,
        shapes_df=shapes_df,
        shape_id=shape_id,
        bin_sec=args.bin_sec,
        eps_m=args.eps_m,
        min_pts=args.min_pts,
        xtrack_max_m=args.xtrack_max_m,
        acc_max_m=args.acc_max_m,
        vmax_mps=args.vmax_mps,
        gate_m=args.gate_m,
        ttl_bins=args.ttl_bins,
    )

    vehicles_df.to_csv(args.out_vehicles, index=False)
    assign_df.to_csv(args.out_assign, index=False)
    print(f"Saved vehicles to {args.out_vehicles} ({len(vehicles_df)}) and assignments to {args.out_assign} ({len(assign_df)})")


if __name__ == "__main__":
    main()
