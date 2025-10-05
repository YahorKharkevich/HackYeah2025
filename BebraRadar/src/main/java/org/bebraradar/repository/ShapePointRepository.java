package org.bebraradar.repository;

import org.bebraradar.entity.ShapePoint;
import org.bebraradar.entity.ShapePointId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "shape-points")
public interface ShapePointRepository extends JpaRepository<ShapePoint, ShapePointId> {
}
