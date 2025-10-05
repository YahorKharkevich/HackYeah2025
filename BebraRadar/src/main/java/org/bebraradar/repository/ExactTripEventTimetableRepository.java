package org.bebraradar.repository;

import org.bebraradar.entity.ExactTripEventTimetable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ExactTripEventTimetableRepository extends JpaRepository<ExactTripEventTimetable, Long> {

    List<ExactTripEventTimetable> findByTimestampGreaterThanEqualOrderByTimestampDesc(OffsetDateTime since);
}
