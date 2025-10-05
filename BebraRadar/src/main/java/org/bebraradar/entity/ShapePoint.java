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
@Table(name = "shapes")
public class ShapePoint {

    @EmbeddedId
    private ShapePointId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("shapeId")
    @JoinColumn(name = "shape_id", nullable = false)
    private ShapeIdEntity shape;

    @Column(name = "shape_pt_lat", nullable = false)
    private Double latitude;

    @Column(name = "shape_pt_lon", nullable = false)
    private Double longitude;

    @Column(name = "shape_dist_traveled")
    private Double distanceTraveled;

    protected ShapePoint() {
    }

    public ShapePoint(ShapePointId id, ShapeIdEntity shape, Double latitude, Double longitude, Double distanceTraveled) {
        this.id = id;
        this.shape = shape;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceTraveled = distanceTraveled;
    }

    public ShapePointId getId() {
        return id;
    }

    public void setId(ShapePointId id) {
        this.id = id;
    }

    public ShapeIdEntity getShape() {
        return shape;
    }

    public void setShape(ShapeIdEntity shape) {
        this.shape = shape;
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

    public Double getDistanceTraveled() {
        return distanceTraveled;
    }

    public void setDistanceTraveled(Double distanceTraveled) {
        this.distanceTraveled = distanceTraveled;
    }
}
