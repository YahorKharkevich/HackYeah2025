package org.bebraradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StopTimeId implements Serializable {

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "stop_sequence")
    private Integer stopSequence;

    protected StopTimeId() {
    }

    public StopTimeId(Long tripId, Integer stopSequence) {
        this.tripId = tripId;
        this.stopSequence = stopSequence;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Integer getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(Integer stopSequence) {
        this.stopSequence = stopSequence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StopTimeId that)) {
            return false;
        }
        return Objects.equals(tripId, that.tripId)
            && Objects.equals(stopSequence, that.stopSequence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tripId, stopSequence);
    }
}
