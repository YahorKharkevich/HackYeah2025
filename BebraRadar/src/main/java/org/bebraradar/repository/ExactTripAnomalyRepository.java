package org.bebraradar.repository;

import org.bebraradar.entity.ExactTripAnomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.OffsetDateTime;
import java.util.List;

@RepositoryRestResource(path = "anomalies")
public interface ExactTripAnomalyRepository extends JpaRepository<ExactTripAnomaly, Long> {

    List<ExactTripAnomaly> findByTimestampGreaterThanEqualOrderByTimestampDesc(OffsetDateTime since);
}
