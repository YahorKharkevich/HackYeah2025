package org.bebraradar.repository;

import org.bebraradar.entity.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "stops")
public interface StopRepository extends JpaRepository<Stop, String> {
}
