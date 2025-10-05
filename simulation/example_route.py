# -*- coding: utf-8 -*-
import argparse
import json
import math
import sys
import zipfile
from typing import Dict, Tuple, Optional, List

import pandas as pd


def read_gtfs(zip_path: str) -> Dict[str, pd.DataFrame]:
    z = zipfile.ZipFile(zip_path)
    names = {n.lower().strip(): n for n in z.namelist()}

    def pick(name: str) -> Optional[str]:
        key = name.lower().strip()
        if key in names:
            return names[key]
        for k, v in names.items():
            if k.endswith("/" + key) or k.endswith(key):
                return v
        return None

    def rd(name: str) -> pd.DataFrame:
        real = pick(name)
        if not real:
            return pd.DataFrame()
        with z.open(real) as f:
            df = pd.read_csv(
                f,
                sep=None,
                engine="python",
                dtype=str,
                keep_default_na=False,
                encoding="utf-8-sig",
                comment="#",
                on_bad_lines="skip",
            )
        df.columns = [c.strip().lower().lstrip("\ufeff") for c in df.columns]
        return df

    return {
        "agency": rd("agency.txt"),
        "routes": rd("routes.txt"),
        "trips": rd("trips.txt"),
        "stop_times": rd("stop_times.txt"),
        "stops": rd("stops.txt"),
        "calendar": rd("calendar.txt"),
        "calendar_dates": rd("calendar_dates.txt"),
        "transfers": rd("transfers.txt"),
        "feed_info": rd("feed_info.txt"),
        "shapes": rd("shapes.txt"),
        "attributions": rd("attributions.txt"),
    }


def require_columns(df: pd.DataFrame, needed, table_name: str):
    missing = [c for c in needed if c not in df.columns]
    if missing:
        raise RuntimeError(f"В {table_name} нет нужных колонок: {missing}. Колонки={list(df.columns)}")


def hms_to_sec(hms: str) -> int:
    # допускаем HH >= 24 по GTFS
    if not isinstance(hms, str) or ":" not in hms:
        raise ValueError("bad hms")
    h, m, s = hms.split(":")
    return int(h) * 3600 + int(m) * 60 + int(s)


def sec_to_hms(sec: int) -> str:
    h = sec // 3600
    m = (sec % 3600) // 60
    s = sec % 60
    return f"{h:02d}:{m:02d}:{s:02d}"


def haversine_m(lat1, lon1, lat2, lon2) -> float:
    R = 6371000.0
    p1, p2 = math.radians(float(lat1)), math.radians(float(lat2))
    dphi = math.radians(float(lat2) - float(lat1))
    dl = math.radians(float(lon2) - float(lon1))
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


def get_time_pref(arrival: str, departure: str) -> Optional[str]:
    # берём arrival_time, если пуст — departure_time; если оба пустые, None
    a = (arrival or "").strip()
    d = (departure or "").strip()
    if a:
        return a
    if d:
        return d
    return None


def route_trip_meta(trips_df: pd.DataFrame, routes_df: pd.DataFrame, trip_id: str) -> Tuple[Dict, Dict]:
    t = trips_df[trips_df["trip_id"] == trip_id]
    if t.empty:
        return {}, {}
    t = t.iloc[0].to_dict()
    r = routes_df[routes_df["route_id"] == t.get("route_id")]
    r = r.iloc[0].to_dict() if not r.empty else {}
    return t, r


def find_any_transfer(ald, ska, radius_m: int, krk_substr: str, buffer_sec: int, max_results: int = 1) -> List[Dict]:
    results = []
    try:
        max_results = int(max_results)
    except Exception:
        max_results = 1
    if max_results <= 0:
        max_results = 1
    spA = ald["stops"].copy()
    stA = ald["stop_times"].copy()
    trA = ald["trips"].copy()
    rtA = ald["routes"].copy()

    spS = ska["stops"].copy()
    stS = ska["stop_times"].copy()
    trS = ska["trips"].copy()
    rtS = ska["routes"].copy()

    # Проверки обязательных таблиц/полей
    require_columns(spA, ["stop_id", "stop_name", "stop_lat", "stop_lon"], "ALD stops.txt")
    require_columns(spS, ["stop_id", "stop_name", "stop_lat", "stop_lon"], "SKA stops.txt")
    require_columns(stA, ["trip_id", "stop_id", "arrival_time", "departure_time", "stop_sequence"], "ALD stop_times.txt")
    require_columns(stS, ["trip_id", "stop_id", "arrival_time", "departure_time", "stop_sequence"], "SKA stop_times.txt")
    require_columns(trA, ["trip_id", "route_id"], "ALD trips.txt")
    require_columns(trS, ["trip_id", "route_id"], "SKA trips.txt")
    require_columns(rtA, ["route_id"], "ALD routes.txt")
    require_columns(rtS, ["route_id"], "SKA routes.txt")

    spA["stop_lat"] = spA["stop_lat"].astype(float)
    spA["stop_lon"] = spA["stop_lon"].astype(float)
    spS["stop_lat"] = spS["stop_lat"].astype(float)
    spS["stop_lon"] = spS["stop_lon"].astype(float)

    nameA = dict(zip(spA["stop_id"], spA["stop_name"]))
    nameS = dict(zip(spS["stop_id"], spS["stop_name"]))

    # все "краковские" станции в железнодорожном фиде
    krk_mask = spS["stop_name"].astype(str).str.contains(krk_substr, case=False, regex=False)
    krk_stops = spS.loc[krk_mask, "stop_id"].tolist()
    if not krk_stops:
        raise RuntimeError(f"В железнодорожном GTFS нет станций с подстрокой '{krk_substr}'")

    # Перебираем все станции SKA как узлы пересадки
    for _, srow in spS.iterrows():
        s_id = srow["stop_id"]
        s_lat = srow["stop_lat"]
        s_lon = srow["stop_lon"]

        cand_bus_stops = spA.copy()
        cand_bus_stops["dist_m"] = cand_bus_stops.apply(
            lambda r: haversine_m(r["stop_lat"], r["stop_lon"], s_lat, s_lon), axis=1
        )
        nearby = cand_bus_stops[cand_bus_stops["dist_m"] <= radius_m].sort_values("dist_m")
        if nearby.empty:
            continue

        # Для каждой близкой автобусной остановки пробуем построить связку
        for _, b in nearby.iterrows():
            b_id = b["stop_id"]
            # Все появления этой автобусной остановки в stop_times
            st_at_transfer = stA[stA["stop_id"] == b_id]
            if st_at_transfer.empty:
                continue

            # Перебор автобусных рейсов, которые проходят через b_id
            for _, row_bus_at_t in st_at_transfer.iterrows():
                trip_id_bus = row_bus_at_t["trip_id"]
                times_bus_trip = stA[stA["trip_id"] == trip_id_bus].copy()
                if times_bus_trip.empty:
                    continue

                # Упорядочим по stop_sequence
                try:
                    times_bus_trip["stop_sequence"] = times_bus_trip["stop_sequence"].astype(int)
                except Exception:
                    continue
                times_bus_trip = times_bus_trip.sort_values("stop_sequence")

                # Берём "источник" рейса — первую значимую остановку (не равную точке пересадки, если возможно)
                origin_row = times_bus_trip.iloc[0]
                if origin_row["stop_id"] == b_id and len(times_bus_trip) >= 2:
                    origin_row = times_bus_trip.iloc[1]
                if origin_row["stop_id"] == b_id:
                    # рейс фактически начинается на точке пересадки — неинтересно
                    continue

                # Время прибытия автобуса на точку пересадки
                arr_bus_str = get_time_pref(row_bus_at_t.get("arrival_time", ""), row_bus_at_t.get("departure_time", ""))
                if not arr_bus_str:
                    continue
                try:
                    arr_bus_sec = hms_to_sec(arr_bus_str)
                except Exception:
                    continue

                # Поезда со станции s_id в сторону любой "краковской" остановки
                st_trans = stS[stS["stop_id"] == s_id]
                if st_trans.empty:
                    continue

                # Все поездки, которые из s_id идут дальше к любой krk_stop
                st_goal = stS[stS["stop_id"].isin(krk_stops)]
                if st_goal.empty:
                    continue

                cand_train = st_trans.merge(st_goal, on="trip_id", suffixes=("_tr", "_kr"))

                # Последовательность поезда должна возрастать (s_id -> krk_stop)
                try:
                    cand_train["stop_sequence_tr"] = cand_train["stop_sequence_tr"].astype(int)
                    cand_train["stop_sequence_kr"] = cand_train["stop_sequence_kr"].astype(int)
                except Exception:
                    continue

                cand_train = cand_train[cand_train["stop_sequence_kr"] > cand_train["stop_sequence_tr"]]

                # Отправление поезда с запасом после прибытия автобуса
                def ok_depart(t: str) -> bool:
                    t = (t or "").strip()
                    if not t:
                        return False
                    try:
                        return hms_to_sec(t) >= arr_bus_sec + buffer_sec
                    except Exception:
                        return False

                cand_train = cand_train[cand_train["departure_time_tr"].apply(ok_depart)]
                if cand_train.empty:
                    continue

                # Нашли — берём самый ранний по отправлению
                cand_train = cand_train.sort_values("departure_time_tr")
                best_train = cand_train.iloc[0]

                # Сохраняем результат
                found = {
                    "origin_stop_id": origin_row["stop_id"],
                    "origin_stop_name": nameA.get(origin_row["stop_id"], origin_row["stop_id"]),
                    "bus_transfer_stop_id": b_id,
                    "bus_transfer_stop_name": nameA.get(b_id, b_id),
                    "rail_transfer_stop_id": s_id,
                    "rail_transfer_stop_name": nameS.get(s_id, s_id),
                    "bus_trip_id": trip_id_bus,
                    "bus_departure_time": get_time_pref(origin_row.get("departure_time", ""),
                                                        origin_row.get("arrival_time", "")) or "",
                    "bus_arrival_time_transfer": arr_bus_str,
                    "train_trip_id": best_train["trip_id"],
                    "train_departure_time": best_train["departure_time_tr"],
                    "train_arrival_time": best_train["arrival_time_kr"],
                    "train_goal_stop_id": best_train["stop_id_kr"],
                    "train_goal_stop_name": nameS.get(best_train["stop_id_kr"], best_train["stop_id_kr"]),
                    "walk_m": int(round(b["dist_m"])),
                }

                # Доп. метаданные маршрутов
                bus_trip, bus_route = route_trip_meta(trA, rtA, found["bus_trip_id"])
                train_trip, train_route = route_trip_meta(trS, rtS, found["train_trip_id"])

                found["bus"] = {
                    "route_short_name": bus_route.get("route_short_name", ""),
                    "route_long_name": bus_route.get("route_long_name", ""),
                    "route_type": bus_route.get("route_type", ""),
                    "route_color": bus_route.get("route_color", ""),
                    "trip_headsign": bus_trip.get("trip_headsign", ""),
                    "service_id": bus_trip.get("service_id", ""),
                    "route_id": bus_route.get("route_id", bus_trip.get("route_id", "")),
                }
                found["train"] = {
                    "route_short_name": train_route.get("route_short_name", ""),
                    "route_long_name": train_route.get("route_long_name", ""),
                    "route_type": train_route.get("route_type", ""),
                    "route_color": train_route.get("route_color", ""),
                    "trip_headsign": train_trip.get("trip_headsign", ""),
                    "service_id": train_trip.get("service_id", ""),
                    "route_id": train_route.get("route_id", train_trip.get("route_id", "")),
                }

                results.append(found)
                if len(results) >= max_results:
                    return results

    if results:
        return results

    raise RuntimeError("Не удалось автоматически найти ни одной связки ALD→SKA. Увеличьте --radius или поменяйте --krk.")


def print_itinerary(found: Dict, radius_m: int, buffer_sec: int, order: Optional[int] = None, total: Optional[int] = None):
    if total and total > 1:
        print(f"\n=== Маршрут {order}/{total} с пересадкой ALD → SKA ===\n")
    else:
        print("\n=== Найден маршрут с пересадкой ALD → SKA ===\n")

    print("Сегмент 1 — Автобус (ALD)")
    print(f"  Route: {found['bus'].get('route_short_name','')} {found['bus'].get('route_long_name','')}")
    print(f"  Type:  {found['bus'].get('route_type','')}  Color: #{found['bus'].get('route_color','')}")
    print(f"  Trip:  {found['bus_trip_id']}  Headsign: {found['bus'].get('trip_headsign','')}")
    print(f"  Из:    {found['origin_stop_name']} (stop_id={found['origin_stop_id']})")
    print(f"  В:     {found['bus_transfer_stop_name']} (stop_id={found['bus_transfer_stop_id']})")
    print(f"  Время: {found['bus_departure_time']} → {found['bus_arrival_time_transfer']}")

    print("\nПересадка пешком")
    print(f"  ~{found['walk_m']} м (радиус поиска {radius_m} м), буфер {buffer_sec // 60} мин")

    print("\nСегмент 2 — Поезд (SKA)")
    print(f"  Route: {found['train'].get('route_short_name','')} {found['train'].get('route_long_name','')}")
    print(f"  Type:  {found['train'].get('route_type','')}  Color: #{found['train'].get('route_color','')}")
    print(f"  Trip:  {found['train_trip_id']}  Headsign: {found['train'].get('trip_headsign','')}")
    print(f"  Из:    {found['rail_transfer_stop_name']} (stop_id={found['rail_transfer_stop_id']})")
    print(f"  В:     {found['train_goal_stop_name']} (stop_id={found['train_goal_stop_id']})")
    print(f"  Время: {found['train_departure_time']} → {found['train_arrival_time']}\n")


def main():
    p = argparse.ArgumentParser(description="Найти ЛЮБОЙ маршрут с пересадкой ALD(автобус) → SKA(поезд) до Кракова по GTFS.")
    p.add_argument("--ald", required=True, help="Путь к GTFS ZIP автобусного ALD (например, ald-gtfs.zip)")
    p.add_argument("--ska", required=True, help="Путь к GTFS ZIP железнодорожного SKA (например, kml-ska-gtfs.zip)")
    p.add_argument("--radius", type=int, default=1000, help="Радиус поиска автобусной остановки возле станции SKA, м (по умолчанию 1000)")
    p.add_argument("--krk", default="Kraków", help="Подстрока названия целевой станции в Кракове (по умолчанию 'Kraków')")
    p.add_argument("--buffer-min", type=int, default=6, help="Буфер пересадки, минут (по умолчанию 6)")
    p.add_argument("--limit", type=int, default=1, help="Сколько вариантов маршрута вывести (по умолчанию 1)")
    p.add_argument("--json", default="", help="Сохранить результат в JSON-файл")
    args = p.parse_args()

    try:
        ald = read_gtfs(args.ald)
        ska = read_gtfs(args.ska)
    except Exception as e:
        print(f"Не удалось прочитать GTFS: {e}", file=sys.stderr)
        sys.exit(2)

    try:
        results = find_any_transfer(
            ald=ald,
            ska=ska,
            radius_m=args.radius,
            krk_substr=args.krk,
            buffer_sec=args.buffer_min * 60,
            max_results=args.limit,
        )
    except Exception as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)

    for idx, itinerary in enumerate(results, start=1):
        print_itinerary(itinerary, args.radius, args.buffer_min * 60, order=idx, total=len(results))

    if args.json:
        payload = results[0] if len(results) == 1 else results
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        print(f"Маршруты сохранены в {args.json}")


if __name__ == "__main__":
    main()
