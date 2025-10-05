package org.bebraradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "exact_trip_event_geo_location")
public class ExactTripEventGeoLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(name = "ts", nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "lat")
    private Double latitude;

    @Column(name = "lon")
    private Double longitude;

    @Column(name = "gps_accuracy_m")
    private Double gpsAccuracyMeters;

    @Column(name = "type")
    private String type;

    protected ExactTripEventGeoLocation() {
    }

    public ExactTripEventGeoLocation(Trip trip, UserAccount user, OffsetDateTime timestamp, Double latitude,
                                     Double longitude, Double gpsAccuracyMeters, String type) {
        this.trip = trip;
        this.user = user;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.gpsAccuracyMeters = gpsAccuracyMeters;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getGpsAccuracyMeters() {
        return gpsAccuracyMeters;
    }

    public void setGpsAccuracyMeters(Double gpsAccuracyMeters) {
        this.gpsAccuracyMeters = gpsAccuracyMeters;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
