import math
import pandas as pd
import numpy as np

# ---------- гео-помощники ----------
def haversine_m(lat1, lon1, lat2, lon2):
    R = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2-lat1)
    dl = math.radians(lon2-lon1)
    a = math.sin(dphi/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*R*math.asin(math.sqrt(a))

def project_point_to_segment(px, py, ax, ay, bx, by):
    """
    Return values:
        distance_to_segment,
        t - projection parameter in [0...1]
        (projx, projy) - projection coordinates
    """
    # px,py — lon/lat точки; ax,ay,bx,by — сегмент lon/lat
    kx = 111320 * math.cos(math.radians(py))
    ky = 110540
    APx, APy = (px-ax)*kx, (py-ay)*ky
    ABx, ABy = (bx-ax)*kx, (by-ay)*ky
    ab2 = ABx*ABx + ABy*ABy
    if ab2 == 0:
        return haversine_m(py, px, ay, ax), 0.0, (ax, ay)
    t = max(0.0, min(1.0, (APx*ABx + APy*ABy) / ab2))
    projx = ax + (bx-ax)*t
    projy = ay + (by-ay)*t
    distance_to_segment = haversine_m(py, px, projy, projx)
    return distance_to_segment, t, (projx, projy)

def polyline_from_shapes(shapes_df, shape_id):
    """
    Return:
        pts - points of the polyline
        cum - length of each segment of polyline accumulated 
        (e.g. [0, 120, 205] segment 0->1 120, segment 1->2 85)
    """
    shp = shapes_df[shapes_df["shape_id"].astype(str)==str(shape_id)].copy()
    if shp.empty:
        raise RuntimeError(f"shape_id {shape_id} не найден")

    shp["shape_pt_sequence"] = pd.to_numeric(shp["shape_pt_sequence"], errors="coerce").astype(int)
    shp = shp.sort_values("shape_pt_sequence")

    # list of polyline points.
    pts = shp[["shape_pt_lat","shape_pt_lon"]].astype(float).to_numpy()
    cum = [0.0]
    for i in range(1, len(pts)):
        cum.append(cum[-1] + haversine_m(pts[i-1][0], pts[i-1][1], pts[i][0], pts[i][1]))
        
    return pts, np.array(cum, float)

def s_along_and_xtrack(pts, cum, lat, lon):
    """
        find out how far we ar along our polyline from a point
        Return:
            (distance along polyline, perpendicular distance to the segment)
    """
    best = (0.0, float("inf"))
    for i in range(len(pts)-1):
        ay, ax = pts[i][0], pts[i][1]
        by, bx = pts[i+1][0], pts[i+1][1]
        d, t, _ = project_point_to_segment(lon, lat, ax, ay, bx, by)
        along = cum[i] + t*(cum[i+1]-cum[i])
        if d < best[1]:
            best = (along, d)
    return best  # (s_along_m, xtrack_m)

def interp_along_polyline(pts, cum, s):
    """
    from distance along the polyline to lat/lon
    """
    if s <= 0: return float(pts[0][0]), float(pts[0][1])
    if s >= cum[-1]: return float(pts[-1][0]), float(pts[-1][1])
    i = int(np.searchsorted(cum, s, side="right")) - 1
    i = max(0, min(i, len(cum)-2))
    seg = cum[i+1]-cum[i]
    t = 0.0 if seg<=0 else (s-cum[i])/seg
    lat = pts[i][0] + (pts[i+1][0]-pts[i][0])*t
    lon = pts[i][1] + (pts[i+1][1]-pts[i][1])*t
    return float(lat), float(lon)

# ---------- 1D gap-кластеризация ----------
def clusters_1d(values, ids=None, eps=80.0, min_pts=3):
    """
    values: список s_along 
    ids: такие же по длине user_id 
    возвращает список кластеров [(center, members_idx)]
    """
    if not values:
        return []
    order = np.argsort(values)
    v = [values[i] for i in order]
    idxs = [None if ids is None else ids[i] for i in order]
    groups = []
    cur_v, cur_ids = [v[0]], [idxs[0]]
    for k in range(1, len(v)):
        if abs(v[k]-cur_v[-1]) <= eps:
            cur_v.append(v[k]); cur_ids.append(idxs[k])
        else:
            groups.append((np.median(cur_v), cur_ids.copy()))
            cur_v, cur_ids = [v[k]], [idxs[k]]
    groups.append((np.median(cur_v), cur_ids.copy()))
    # отбрасываем малые
    groups = [(c, g) for (c, g) in groups if sum(1 for x in g if x is not None) >= min_pts]
    return groups

# ---------- трекинг кластеров как «ТС» ----------
class MultiTracker1D:
    def __init__(self, vmax_mps=25.0, gate_m=150.0, ttl_bins=6):
        self.vmax = vmax_mps
        self.gate = gate_m
        self.ttl = ttl_bins
        self.tracks = {}   # id -> dict(last_s, last_t, v, alive, id)
        self.next_id = 1

    def step(self, t_bin, clusters):
        """
        clusters: список кортежей (center_s, member_user_ids)
        Возвращает сопоставление cluster -> track_id и обновляет self.tracks
        """
        # подготовка
        active = {tid: tr for tid, tr in self.tracks.items() if tr["alive"]>0}
        # построим пары (track, cluster) с костом = |предсказанное s - центр|
        pairs = []
        for tid, tr in active.items():
            dt = max(1e-3, t_bin - tr["last_t"])
            s_pred = tr["last_s"] + tr["v"]*dt
            for j, (c_s, members) in enumerate(clusters):
                cost = abs(c_s - s_pred)
                # ограничение: не быстрее vmax и не дальше gate
                if cost <= self.gate + self.vmax*dt:
                    pairs.append((cost, tid, j))
        pairs.sort(key=lambda x: x[0])
        assigned_tracks = set()
        assigned_clusters = set()
        assign = {}
        # жадный матчинг
        for cost, tid, j in pairs:
            if tid in assigned_tracks or j in assigned_clusters:
                continue
            assign[j] = tid
            assigned_tracks.add(tid)
            assigned_clusters.add(j)
        # новые треки для неприсвоенных кластеров
        for j in range(len(clusters)):
            if j not in assign:
                tid = self.next_id; self.next_id += 1
                assign[j] = tid
                self.tracks[tid] = {"last_s": clusters[j][0], "last_t": t_bin, "v": 0.0, "alive": self.ttl, "id": tid}
        # обновление присвоенных
        for j, tid in assign.items():
            c_s, members = clusters[j]
            tr = self.tracks[tid]
            dt = max(1e-3, t_bin - tr["last_t"])
            v = (c_s - tr["last_s"]) / dt
            tr["last_s"], tr["last_t"], tr["v"], tr["alive"] = c_s, t_bin, v, self.ttl
        # уменьшаем ttl у неприсвоенных (пропуски данных)
        for tid, tr in self.tracks.items():
            if assign and (tid not in assigned_tracks) and (tr["last_t"] < t_bin):
                tr["alive"] -= 1
        return assign

# ---------- основной пайплайн ----------
def cluster_users_into_vehicles(points_df, shapes_df, shape_id,
                                bin_sec=10, eps_m=80.0, min_pts=3,
                                xtrack_max_m=80.0, acc_max_m=80.0,
                                vmax_mps=25.0, gate_m=150.0, ttl_bins=6):
    """
    points_df: columns: user_id_hash, t (Unix сек или Timestamp UTC), lat, lon, accuracy_m?
    Return:
      vehicles_df: (t_bin, vehicle_id, s_m, lat, lon, n_users)
      assign_df:   (t_bin, user_id_hash, vehicle_id)
    """


    # polyline
    pts, cum = polyline_from_shapes(shapes_df, shape_id)

    df = points_df.copy()
    # time -> t_sec
    if np.issubdtype(df["t"].dtype, np.datetime64):
        df["t_sec"] = (df["t"].astype("int64") // 10**9).astype(int)
    else:
        df["t_sec"] = df["t"].astype(int)

    # map-matching и фильтр
    good_rows = []
    for _, r in df.iterrows():
        acc = float(r.get("accuracy_m", np.nan))
        if not np.isnan(acc) and acc > acc_max_m:
            continue
        
        s, d = s_along_and_xtrack(pts, cum, float(r["lat"]), float(r["lon"]))
        # check if distance to the route is less than treshold
        if d <= xtrack_max_m:
            good_rows.append({
                "user_id_hash": r["user_id_hash"],
                "t_sec": int(r["t_sec"]),
                "s_m": float(s)
            })

    if not good_rows:
        return (pd.DataFrame(columns=["t_bin","vehicle_id","s_m","lat","lon","n_users"]),
                pd.DataFrame(columns=["t_bin","user_id_hash","vehicle_id"]))

    G = pd.DataFrame(good_rows)
    G["t_bin"] = (G["t_sec"] // bin_sec) * bin_sec

    tracker = MultiTracker1D(vmax_mps=vmax_mps, gate_m=gate_m, ttl_bins=ttl_bins)
    veh_rows, asn_rows = [], []

    for t_bin, grp in G.groupby("t_bin"):
        values = grp["s_m"].tolist()
        ids = grp["user_id_hash"].tolist()
        # 1D кластеры в окне
        cl = clusters_1d(values, ids=ids, eps=eps_m, min_pts=min_pts)
        # сопоставляем с треками и получаем устойчивые vehicle_id
        assign = tracker.step(t_bin, cl)
        # записываем результаты
        for j, (center_s, members) in enumerate(cl):
            vid = assign[j]
            lat, lon = interp_along_polyline(pts, cum, center_s)
            veh_rows.append({
                "t_bin": int(t_bin),
                "vehicle_id": int(vid),
                "s_m": float(center_s),
                "lat": lat, "lon": lon,
                "n_users": int(sum(1 for u in members if u is not None))
            })
            for u in members:
                if u is None: 
                    continue
                asn_rows.append({"t_bin": int(t_bin), "user_id_hash": u, "vehicle_id": int(vid)})

    vehicles_df = pd.DataFrame(veh_rows).sort_values(["t_bin","vehicle_id"]).reset_index(drop=True)
    assign_df   = pd.DataFrame(asn_rows).sort_values(["t_bin","user_id_hash"]).reset_index(drop=True)
    return vehicles_df, assign_df
