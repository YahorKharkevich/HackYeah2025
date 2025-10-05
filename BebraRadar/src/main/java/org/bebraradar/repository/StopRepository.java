package org.bebraradar.repository;

import org.bebraradar.entity.Stop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopRepository extends JpaRepository<Stop, String> {
}
