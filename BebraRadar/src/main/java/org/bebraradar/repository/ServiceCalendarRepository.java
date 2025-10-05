package org.bebraradar.repository;

import org.bebraradar.entity.ServiceCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "calendars")
public interface ServiceCalendarRepository extends JpaRepository<ServiceCalendar, String> {
}
