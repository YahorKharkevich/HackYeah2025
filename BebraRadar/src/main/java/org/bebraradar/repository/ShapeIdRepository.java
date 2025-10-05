package org.bebraradar.repository;

import org.bebraradar.entity.ShapeIdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "shape-ids")
public interface ShapeIdRepository extends JpaRepository<ShapeIdEntity, String> {
}
