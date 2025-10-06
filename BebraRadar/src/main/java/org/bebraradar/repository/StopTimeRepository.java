package org.bebraradar.repository;

import org.bebraradar.entity.StopTime;
import org.bebraradar.entity.StopTimeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopTimeRepository extends JpaRepository<StopTime, StopTimeId> {

    java.util.List<StopTime> findByTrip_IdOrderById_StopSequenceAsc(Long tripId);
}
