package org.bebraradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ShapePointId implements Serializable {

    @Column(name = "shape_id")
    private String shapeId;

    @Column(name = "shape_pt_sequence")
    private Integer sequence;

    protected ShapePointId() {
    }

    public ShapePointId(String shapeId, Integer sequence) {
        this.shapeId = shapeId;
        this.sequence = sequence;
    }

    public String getShapeId() {
        return shapeId;
    }

    public void setShapeId(String shapeId) {
        this.shapeId = shapeId;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShapePointId that)) {
            return false;
        }
        return Objects.equals(shapeId, that.shapeId)
            && Objects.equals(sequence, that.sequence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shapeId, sequence);
    }
}
