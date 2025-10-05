package org.bebraradar.repository;

import org.bebraradar.entity.ExactTripEventTimetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.OffsetDateTime;
import java.util.List;

@RepositoryRestResource(path = "timetable-events")
public interface ExactTripEventTimetableRepository extends JpaRepository<ExactTripEventTimetable, Long> {

    List<ExactTripEventTimetable> findByTimestampGreaterThanEqualOrderByTimestampDesc(OffsetDateTime since);
}
