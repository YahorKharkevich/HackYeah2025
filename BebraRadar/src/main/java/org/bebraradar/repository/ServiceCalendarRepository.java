package org.bebraradar.repository;

import org.bebraradar.entity.ServiceCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceCalendarRepository extends JpaRepository<ServiceCalendar, String> {
}
