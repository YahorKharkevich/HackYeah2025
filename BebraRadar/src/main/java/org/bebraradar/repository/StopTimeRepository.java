package org.bebraradar.repository;

import org.bebraradar.entity.StopTime;
import org.bebraradar.entity.StopTimeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "stop-times")
public interface StopTimeRepository extends JpaRepository<StopTime, StopTimeId> {
}
