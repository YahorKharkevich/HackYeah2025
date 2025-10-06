package org.bebraradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "vehicle_positions_current")
public class VehiclePositionCurrent {

    @Id
    @Column(name = "vehicle_no")
    private String vehicleNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "ts", nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "last_stop_ts", nullable = false)
    private OffsetDateTime lastStopTimestamp;

    @Column(name = "lat")
    private Double latitude;

    @Column(name = "lon")
    private Double longitude;

    @Column(name = "speed_mps")
    private Double speedMps;

    @Column(name = "bearing_deg")
    private Double bearingDeg;

    @Column(name = "gps_accuracy_m")
    private Double gpsAccuracyMeters;

    public VehiclePositionCurrent() {}

    public VehiclePositionCurrent(String vehicleNo, Trip trip, OffsetDateTime timestamp, OffsetDateTime lastStopTimestamp,
                                  Double latitude, Double longitude, Double speedMps, Double bearingDeg, Double gpsAccuracyMeters) {
        this.vehicleNo = vehicleNo;
        this.trip = trip;
        this.timestamp = timestamp;
        this.lastStopTimestamp = lastStopTimestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedMps = speedMps;
        this.bearingDeg = bearingDeg;
        this.gpsAccuracyMeters = gpsAccuracyMeters;
    }

    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }

    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }

    public OffsetDateTime getLastStopTimestamp() { return lastStopTimestamp; }
    public void setLastStopTimestamp(OffsetDateTime lastStopTimestamp) { this.lastStopTimestamp = lastStopTimestamp; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getSpeedMps() { return speedMps; }
    public void setSpeedMps(Double speedMps) { this.speedMps = speedMps; }

    public Double getBearingDeg() { return bearingDeg; }
    public void setBearingDeg(Double bearingDeg) { this.bearingDeg = bearingDeg; }

    public Double getGpsAccuracyMeters() { return gpsAccuracyMeters; }
    public void setGpsAccuracyMeters(Double gpsAccuracyMeters) { this.gpsAccuracyMeters = gpsAccuracyMeters; }
}
