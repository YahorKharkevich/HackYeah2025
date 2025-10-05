package org.bebraradar.repository;

import org.bebraradar.entity.ExactTripEventGeoLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ExactTripEventGeoLocationRepository extends JpaRepository<ExactTripEventGeoLocation, Long> {

    List<ExactTripEventGeoLocation> findByTimestampGreaterThanEqualOrderByTimestampDesc(OffsetDateTime since);
}
