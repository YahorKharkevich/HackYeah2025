package com.bebraradar.model;

import java.io.Serializable;

public class RouteStop implements Serializable {

    private final String scheduleTime;
    private final String stationName;
    private final String meta;
    private final String statusText;
    private final boolean completed;
    private final boolean current;
    private final Double latitude;
    private final Double longitude;

    public RouteStop(String scheduleTime,
                     String stationName,
                     String meta,
                     String statusText,
                     boolean completed,
                     boolean current,
                     Double latitude,
                     Double longitude) {
        this.scheduleTime = scheduleTime;
        this.stationName = stationName;
        this.meta = meta;
        this.statusText = statusText;
        this.completed = completed;
        this.current = current;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getScheduleTime() {
        return scheduleTime;
    }

    public String getStationName() {
        return stationName;
    }

    public String getMeta() {
        return meta;
    }

    public String getStatusText() {
        return statusText;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isCurrent() {
        return current;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public double getLatitude() {
        return latitude != null ? latitude : 0d;
    }

    public double getLongitude() {
        return longitude != null ? longitude : 0d;
    }
}
