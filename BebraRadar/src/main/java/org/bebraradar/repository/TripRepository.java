package org.bebraradar.repository;

import org.bebraradar.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "trips")
public interface TripRepository extends JpaRepository<Trip, Long> {
}
