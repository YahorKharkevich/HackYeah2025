package org.bebraradar.repository;

import org.bebraradar.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "routes")
public interface RouteRepository extends JpaRepository<Route, String> {
}
