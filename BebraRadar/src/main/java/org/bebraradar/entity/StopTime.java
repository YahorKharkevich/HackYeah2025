package org.bebraradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "stop_times")
public class StopTime {

    @EmbeddedId
    private StopTimeId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tripId")
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", nullable = false)
    private Stop stop;

    @Column(name = "arrival_time", nullable = false)
    private Integer arrivalTime;

    @Column(name = "departure_time", nullable = false)
    private Integer departureTime;

    protected StopTime() {
    }

    public StopTime(StopTimeId id, Trip trip, Stop stop, Integer arrivalTime, Integer departureTime) {
        this.id = id;
        this.trip = trip;
        this.stop = stop;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
    }

    public StopTimeId getId() {
        return id;
    }

    public void setId(StopTimeId id) {
        this.id = id;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public Stop getStop() {
        return stop;
    }

    public void setStop(Stop stop) {
        this.stop = stop;
    }

    public Integer getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Integer arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Integer getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Integer departureTime) {
        this.departureTime = departureTime;
    }
}
