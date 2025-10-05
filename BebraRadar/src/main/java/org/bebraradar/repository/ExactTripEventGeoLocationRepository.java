package org.bebraradar.repository;

import org.bebraradar.entity.ExactTripEventGeoLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.OffsetDateTime;
import java.util.List;

@RepositoryRestResource(path = "geo-events")
public interface ExactTripEventGeoLocationRepository extends JpaRepository<ExactTripEventGeoLocation, Long> {

    List<ExactTripEventGeoLocation> findByTimestampGreaterThanEqualOrderByTimestampDesc(OffsetDateTime since);
}
