import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, FFMpegWriter, PillowWriter

# пусть df уже существует (как у тебя выше)
# откинем строки без обеих координат (но оставим NaN для одной из точек — это ок)
df_anim = pd.read_csv("./simulated_route.csv")

# границы
all_lats = np.concatenate([
    df_anim["bus_lat"].dropna().values,
    df_anim["train_lat"].dropna().values
])
all_lons = np.concatenate([
    df_anim["bus_lon"].dropna().values,
    df_anim["train_lon"].dropna().values
])
lat_min, lat_max = float(all_lats.min()), float(all_lats.max())
lon_min, lon_max = float(all_lons.min()), float(all_lons.max())

# небольшой паддинг
pad_lat = (lat_max - lat_min) * 0.05 if lat_max > lat_min else 0.001
pad_lon = (lon_max - lon_min) * 0.05 if lon_max > lon_min else 0.001

fig, ax = plt.subplots(figsize=(6, 6))
ax.set_xlabel("Longitude")
ax.set_ylabel("Latitude")
ax.set_title("Bus & Train over time")
ax.set_xlim(lon_min - pad_lon, lon_max + pad_lon)
ax.set_ylim(lat_min - pad_lat, lat_max + pad_lat)
ax.set_aspect("equal", adjustable="box")

# объекты для точек и «хвостов»
bus_point,   = ax.plot([], [], marker="o", markersize=6, linestyle="None", label="Bus")
train_point, = ax.plot([], [], marker="s", markersize=6, linestyle="None", label="Train")
bus_tail,    = ax.plot([], [], linewidth=1, label="Bus path")
train_tail,  = ax.plot([], [], linewidth=1, label="Train path")
time_text = ax.text(0.02, 0.98, "", transform=ax.transAxes, va="top")

ax.legend(loc="lower right")

# подготовим массивы для удобства
bus_lat  = df_anim["bus_lat"].values.astype(float)
bus_lon  = df_anim["bus_lon"].values.astype(float)
train_lat = df_anim["train_lat"].values.astype(float)
train_lon = df_anim["train_lon"].values.astype(float)
time_hms = df_anim["time_hms"].astype(str).values

def init():
    bus_point.set_data([], [])
    train_point.set_data([], [])
    bus_tail.set_data([], [])
    train_tail.set_data([], [])
    time_text.set_text("")
    return bus_point, train_point, bus_tail, train_tail, time_text

def update(i):
    # текущие точки
    bx, by = bus_lon[i], bus_lat[i]
    tx, ty = train_lon[i], train_lat[i]

    # обновляем маркеры (если NaN — скрываем)
    if np.isfinite(bx) and np.isfinite(by):
        bus_point.set_data([bx], [by])
        bus_point.set_markevery(None)
        bus_point.set_visible(True)
    else:
        bus_point.set_visible(False)

    if np.isfinite(tx) and np.isfinite(ty):
        train_point.set_data([tx], [ty])
        train_point.set_markevery(None)
        train_point.set_visible(True)
    else:
        train_point.set_visible(False)

    # хвосты до текущего кадра
    if i > 0:
        bus_tail.set_data(df_anim["bus_lon"].iloc[:i+1].values,
                          df_anim["bus_lat"].iloc[:i+1].values)
        train_tail.set_data(df_anim["train_lon"].iloc[:i+1].values,
                            df_anim["train_lat"].iloc[:i+1].values)
    else:
        bus_tail.set_data([], [])
        train_tail.set_data([], [])

    time_text.set_text(time_hms[i])
    return bus_point, train_point, bus_tail, train_tail, time_text

anim = FuncAnimation(fig, update, frames=len(df_anim), init_func=init, interval=50, blit=True)

# сохранить как MP4 (нужен ffmpeg в системе)
try:
    anim.save("bus_train_animation.mp4", writer=FFMpegWriter(fps=20, bitrate=1800))
    print("Saved MP4 -> bus_train_animation.mp4")
except Exception as e:
    print("MP4 save failed:", e)

# и как GIF (медленнее, но без ffmpeg)
try:
    anim.save("bus_train_animation.gif", writer=PillowWriter(fps=12))
    print("Saved GIF -> bus_train_animation.gif")
except Exception as e:
    print("GIF save failed:", e)

plt.show()
