package org.bebraradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shape_ids")
public class ShapeIdEntity {

    @Id
    @Column(name = "shape_id")
    private String id;

    protected ShapeIdEntity() {
    }

    public ShapeIdEntity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
