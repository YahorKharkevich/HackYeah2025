package org.bebraradar.repository;

import org.bebraradar.entity.ShapePoint;
import org.bebraradar.entity.ShapePointId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShapePointRepository extends JpaRepository<ShapePoint, ShapePointId> {
}
