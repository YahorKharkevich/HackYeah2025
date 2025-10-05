# Сегмент 1 — Автобус (ALD)
#   Route: A9 
#   Type:  3  Color: #
#   Trip:  28446351_7953  Headsign: Wieliczka Centrum Dworzec
#   Из:    Limanowa Zygmunta Augusta (stop_id=889082)
#   В:     Wieliczka Centrum (stop_id=669722)
#   Время: 09:22:00 → 10:58:00

# Пересадка пешком
#   ~37 м (радиус поиска 1000 м), буфер 6 мин

# Сегмент 2 — Поезд (SKA)
#   Route:  SKA1
#   Type:  2  Color: #
#   Trip:  2024_2025_813171  Headsign: 
#   Из:    WIELICZKA RYNEK-KOPALNIA (stop_id=241960)
#   В:     KRAKÓW OLSZANICA (stop_id=258463)
#   Время: 11:11:00 → 11:50:36


from pathlib import Path

from example_route import read_gtfs

import pandas as pd

def hms_to_sec(hms: str) -> int:
    h, m, s = map(int, hms.split(":"))
    return h*3600 + m*60 + s

def sec_to_hms(sec: int) -> str:
    sign = "-" if sec < 0 else ""
    sec = abs(sec)
    return f"{sign}{sec//3600:02d}:{(sec%3600)//60:02d}:{sec%60:02d}"

def _stop_time_for_step(row, is_first, is_last):
    arr = str(row.get("arrival_time","") or "").strip()
    dep = str(row.get("departure_time","") or "").strip()
    if is_first:
        return dep or arr or None
    if is_last:
        return arr or dep or None
    return arr or dep or None


def _anchors_from_previous_trip(feed, current_trip_id, current_start_sec):
    trips_df = feed.get("trips", pd.DataFrame())
    stop_times_df = feed.get("stop_times", pd.DataFrame())
    stops_df = feed.get("stops", pd.DataFrame())
    if trips_df.empty or stop_times_df.empty or stops_df.empty:
        return None, []

    trips_df = trips_df.copy()
    trips_df["trip_id"] = trips_df["trip_id"].astype(str)
    current_trip_id = str(current_trip_id)
    trip_row = trips_df[trips_df["trip_id"] == current_trip_id]
    if trip_row.empty:
        return None, []

    route_id = str(trip_row.iloc[0].get("route_id", "")).strip()
    direction_id = str(trip_row.iloc[0].get("direction_id", "")).strip() if "direction_id" in trips_df.columns else ""
    service_id = str(trip_row.iloc[0].get("service_id", "")).strip()

    block_id = trip_row.iloc[0].get("block_id", "")
    block_id = str(block_id).strip()
    same_block = pd.DataFrame()
    if block_id and block_id.lower() != "nan" and "block_id" in trips_df.columns:
        same_block = trips_df[trips_df["block_id"].astype(str).str.strip() == block_id]

    if same_block.empty:
        same_block = trips_df[(trips_df["route_id"].astype(str).str.strip() == route_id)]
        if "direction_id" in trips_df.columns and direction_id not in ("", "nan"):
            same_block = same_block[same_block["direction_id"].astype(str).str.strip() == direction_id]
        if "service_id" in trips_df.columns and service_id not in ("", "nan"):
            same_service = trips_df[trips_df["service_id"].astype(str).str.strip() == service_id]
            if len(same_service) > 1:
                same_block = same_block[same_block["service_id"].astype(str).str.strip() == service_id]

    if same_block.empty:
        return None, []

    stop_times_df = stop_times_df.copy()
    stop_times_df["trip_id"] = stop_times_df["trip_id"].astype(str)
    stop_times_df["stop_sequence"] = pd.to_numeric(stop_times_df["stop_sequence"], errors="coerce")
    stop_times_df = stop_times_df.dropna(subset=["stop_sequence"])
    stop_times_df["stop_sequence"] = stop_times_df["stop_sequence"].astype(int)

    prev_trip_id = None
    prev_last_sec = -float("inf")
    for tid in same_block["trip_id"].tolist():
        if tid == current_trip_id:
            continue
        st_trip = stop_times_df[stop_times_df["trip_id"] == tid]
        if st_trip.empty:
            continue
        secs = []
        for col in ("arrival_time", "departure_time"):
            if col not in st_trip.columns:
                continue
            for v in st_trip[col].astype(str):
                v = (v or "").strip()
                if not v:
                    continue
                try:
                    secs.append(hms_to_sec(v))
                except Exception:
                    continue
        if not secs:
            continue
        trip_last = max(secs)
        if trip_last <= current_start_sec and trip_last > prev_last_sec:
            prev_last_sec = trip_last
            prev_trip_id = tid

    if not prev_trip_id:
        return None, []

    st_prev = stop_times_df[stop_times_df["trip_id"] == prev_trip_id].copy()
    if st_prev.empty:
        return None, []
    st_prev = st_prev.sort_values("stop_sequence")

    stops_small = stops_df[["stop_id", "stop_lat", "stop_lon"]].copy()
    stops_small["stop_id"] = stops_small["stop_id"].astype(str)
    st_prev["stop_id"] = st_prev["stop_id"].astype(str)
    st_prev = st_prev.merge(stops_small, on="stop_id", how="left")
    if st_prev.empty:
        return None, []

    first_seq = st_prev["stop_sequence"].iloc[0]
    last_seq = st_prev["stop_sequence"].iloc[-1]
    anchors = []
    for _, row in st_prev.iterrows():
        is_first = row["stop_sequence"] == first_seq
        is_last = row["stop_sequence"] == last_seq
        t_str = _stop_time_for_step(row, is_first, is_last)
        if not t_str:
            continue
        try:
            t_sec = hms_to_sec(t_str)
        except Exception:
            continue
        if t_sec > current_start_sec:
            break
        try:
            lat = float(row.get("stop_lat"))
            lon = float(row.get("stop_lon"))
        except (TypeError, ValueError):
            continue
        anchors.append({
            "t": t_sec,
            "lat": lat,
            "lon": lon,
            "trip_id": prev_trip_id,
        })

    return prev_trip_id, anchors

def make_stop_step_fn(feed, trip_id, from_stop_id, to_stop_id, keep_tail=False, prepend_block=False, prepend_until_sec=None):
    st = feed["stop_times"].copy()
    stops = feed["stops"][["stop_id","stop_lat","stop_lon"]].copy()
    st = st[st["trip_id"].astype(str) == str(trip_id)]
    st["stop_sequence"] = st["stop_sequence"].astype(int)
    st = st.sort_values("stop_sequence")

    seq_from = st.loc[st["stop_id"].astype(str)==str(from_stop_id), "stop_sequence"].iloc[0]
    seq_to   = st.loc[st["stop_id"].astype(str)==str(to_stop_id),   "stop_sequence"].iloc[0]
    if seq_to < seq_from:
        raise RuntimeError("to_stop раньше from_stop для этого trip")

    if keep_tail:
        seg = st[st["stop_sequence"]>=seq_from].copy()
    else:
        seg = st[(st["stop_sequence"]>=seq_from) & (st["stop_sequence"]<=seq_to)].copy()
    seg = seg.merge(stops.astype({"stop_id":str}), on="stop_id", how="left")

    anchors = []
    last_seq = seg["stop_sequence"].iloc[-1]
    for _, row in seg.iterrows():
        is_first = row["stop_sequence"]==seq_from
        is_last  = row["stop_sequence"]==last_seq
        t = _stop_time_for_step(row, is_first, is_last)
        if not t:
            continue
        anchors.append({
            "t": hms_to_sec(t),
            "lat": float(row["stop_lat"]),
            "lon": float(row["stop_lon"]),
            "trip_id": str(trip_id),
        })

    if prepend_block and anchors:
        prepend_limit = prepend_until_sec
        visited = set()
        current_trip = trip_id
        current_start = anchors[0]["t"]
        while True:
            if current_trip in visited:
                break
            visited.add(current_trip)
            prev_trip_id, prev = _anchors_from_previous_trip(feed, current_trip, current_start)
            if not prev:
                break
            anchors = prev + anchors
            if prepend_limit is not None and prev[0]["t"] <= prepend_limit:
                break
            current_trip = prev_trip_id
            current_start = prev[0]["t"]

    anchors = sorted(anchors, key=lambda x: x["t"])

    # если вдруг мало временных якорей, страхуемся
    if not anchors:
        raise RuntimeError("Нет валидных времён в stop_times для этого сегмента")

    def step_fn(tsec: int):
        if tsec <= anchors[0]["t"]:
            first = anchors[0]
            return first["lat"], first["lon"], first.get("trip_id")
        if tsec >= anchors[-1]["t"]:
            last = anchors[-1]
            return last["lat"], last["lon"], last.get("trip_id")
        lo, hi = 0, len(anchors)-1
        while lo < hi:
            mid = (lo+hi+1)//2
            if anchors[mid]["t"] <= tsec:
                lo = mid
            else:
                hi = mid-1
        cur = anchors[lo]
        nxt = anchors[lo+1]
        t0, t1 = cur["t"], nxt["t"]
        if t1 <= t0:
            return nxt["lat"], nxt["lon"], nxt.get("trip_id")
        ratio = (tsec - t0) / (t1 - t0)
        lat = cur["lat"] + (nxt["lat"] - cur["lat"]) * ratio
        lon = cur["lon"] + (nxt["lon"] - cur["lon"]) * ratio
        trip_src = cur.get("trip_id") if ratio < 1.0 else nxt.get("trip_id")
        return lat, lon, trip_src

    # вернём функцию и «рамки» (для удобства)
    return step_fn, anchors[0]["t"], anchors[-1]["t"]

def simulate_step_bus_train(feed_bus, bus_trip_id, bus_from_id, bus_to_id,
                            feed_train, train_trip_id, train_from_id, train_to_id,
                            window_start_hms="05:22:00", window_end_hms="06:58:00",
                            dt_sec=30, keep_tail=True):
    ws, we = hms_to_sec(window_start_hms), hms_to_sec(window_end_hms)

    bus_fn, _, _   = make_stop_step_fn(
        feed_bus,
        bus_trip_id,
        bus_from_id,
        bus_to_id,
        keep_tail=keep_tail,
        prepend_until_sec=ws if keep_tail else None,
    )
    train_fn, _, _ = make_stop_step_fn(
        feed_train,
        train_trip_id,
        train_from_id,
        train_to_id,
        prepend_block=True,
        prepend_until_sec=ws,
    )

    times = list(range(ws, we+1, dt_sec))
    rows = []
    for ts in times:
        b_lat, b_lon, b_trip_src = bus_fn(ts)
        t_lat, t_lon, t_trip_src = train_fn(ts)
        rows.append({
            "time_sec": ts,
            "time_hms": sec_to_hms(ts),
            "bus_lat": b_lat, "bus_lon": b_lon,
            "train_lat": t_lat, "train_lon": t_lon,
            "bus_trip_id_at_time": b_trip_src,
            "train_trip_id_at_time": t_trip_src,
        })
    return pd.DataFrame(rows)


if __name__ == "__main__":
    # Параметры из твоего итинерария
    bus_trip_id = "28446351_7953"
    bus_from_id = "889082"   # Limanowa Zygmunta Augusta
    bus_to_id   = "669722"   # Wieliczka Centrum

    train_trip_id = "2024_2025_813171"
    train_from_id = "241960" # WIELICZKA RYNEK-KOPALNIA
    train_to_id   = "258463" # KRAKÓW OLSZANICA

    base_dir = Path(__file__).resolve().parent

    ald = read_gtfs(str(base_dir / 'ald-gtfs.zip'))    
    ska = read_gtfs(str(base_dir / 'kml-ska-gtfs.zip'))    

    df = simulate_step_bus_train(
        ald,
        bus_trip_id,
        bus_from_id,   
        bus_to_id,     
        feed_train=ska,
        train_trip_id=train_trip_id,
        train_from_id=train_from_id, 
        train_to_id=train_to_id,   
        window_start_hms="09:22:00",
        window_end_hms="11:51:00",
        dt_sec=30
    )


    df.to_csv("simulated_route.csv")
    
    print(df.to_string(index=False))
    # При желании:
    # df.to_csv("timeline_bus_train_30s.csv", index=False)
