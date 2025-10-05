package org.bebraradar.repository;

import org.bebraradar.entity.ExactTripAnomaly;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ExactTripAnomalyRepository extends JpaRepository<ExactTripAnomaly, Long> {

    List<ExactTripAnomaly> findByTimestampGreaterThanEqualOrderByTimestampDesc(OffsetDateTime since);
}
