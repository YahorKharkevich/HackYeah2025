package org.bebraradar.repository;

import org.bebraradar.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

    java.util.List<Trip> findByService_IdIn(java.util.Collection<String> serviceIds);

    java.util.List<Trip> findByRoute_IdAndService_IdIn(String routeId, java.util.Collection<String> serviceIds);
}
