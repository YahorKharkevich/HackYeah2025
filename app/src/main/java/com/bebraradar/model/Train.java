package com.bebraradar.model;

import java.io.Serializable;

public class Train implements Serializable {

    private final String name;
    private final String number;
    private final String departureDate;
    private final String departureTime;
    private final String departureStation;
    private final String arrivalDate;
    private final String arrivalTime;
    private final String arrivalStation;
    private final boolean runsDaily;
    private final String duration;

    public Train(String name,
                 String number,
                 String departureDate,
                 String departureTime,
                 String departureStation,
                 String arrivalDate,
                 String arrivalTime,
                 String arrivalStation,
                 boolean runsDaily,
                 String duration) {
        this.name = name;
        this.number = number;
        this.departureDate = departureDate;
        this.departureTime = departureTime;
        this.departureStation = departureStation;
        this.arrivalDate = arrivalDate;
        this.arrivalTime = arrivalTime;
        this.arrivalStation = arrivalStation;
        this.runsDaily = runsDaily;
        this.duration = duration;
    }

    public String getName() {
        return name;
    }

    public String getNumber() {
        return number;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public String getDepartureStation() {
        return departureStation;
    }

    public String getArrivalDate() {
        return arrivalDate;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public String getArrivalStation() {
        return arrivalStation;
    }

    public boolean isRunsDaily() {
        return runsDaily;
    }

    public String getDuration() {
        return duration;
    }
}
