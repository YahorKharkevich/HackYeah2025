package org.bebraradar.controller;

import org.bebraradar.dto.ServiceCalendarDto;
import org.bebraradar.entity.ServiceCalendar;
import org.bebraradar.repository.ServiceCalendarRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/calendars")
public class ServiceCalendarController {

    private final ServiceCalendarRepository serviceCalendarRepository;

    public ServiceCalendarController(ServiceCalendarRepository serviceCalendarRepository) {
        this.serviceCalendarRepository = serviceCalendarRepository;
    }

    @GetMapping
    public List<ServiceCalendarDto> getAll() {
        return serviceCalendarRepository.findAll().stream()
            .map(ServiceCalendarController::toDto)
            .toList();
    }

    @GetMapping("/{id}")
    public ServiceCalendarDto getById(@PathVariable String id) {
        return serviceCalendarRepository.findById(id)
            .map(ServiceCalendarController::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service calendar not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceCalendarDto create(@RequestBody ServiceCalendarDto request) {
        if (request.id() == null || request.id().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service id is required");
        }
        if (serviceCalendarRepository.existsById(request.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Service calendar already exists: " + request.id());
        }
        ServiceCalendar calendar = new ServiceCalendar(
            request.id(),
            request.monday(),
            request.tuesday(),
            request.wednesday(),
            request.thursday(),
            request.friday(),
            request.saturday(),
            request.sunday(),
            request.startDate(),
            request.endDate()
        );
        return toDto(serviceCalendarRepository.save(calendar));
    }

    @PutMapping("/{id}")
    public ServiceCalendarDto replace(@PathVariable String id, @RequestBody ServiceCalendarDto request) {
        if (request.id() != null && !id.equals(request.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service id mismatch");
        }
        ServiceCalendar calendar = serviceCalendarRepository.findById(id).orElse(null);
        if (calendar == null) {
            ServiceCalendar created = new ServiceCalendar(
                id,
                request.monday(),
                request.tuesday(),
                request.wednesday(),
                request.thursday(),
                request.friday(),
                request.saturday(),
                request.sunday(),
                request.startDate(),
                request.endDate()
            );
            return toDto(serviceCalendarRepository.save(created));
        }
        apply(calendar, request);
        return toDto(serviceCalendarRepository.save(calendar));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        try {
            serviceCalendarRepository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service calendar not found: " + id, ex);
        }
    }

    private static ServiceCalendarDto toDto(ServiceCalendar entity) {
        return new ServiceCalendarDto(
            entity.getId(),
            entity.isMonday(),
            entity.isTuesday(),
            entity.isWednesday(),
            entity.isThursday(),
            entity.isFriday(),
            entity.isSaturday(),
            entity.isSunday(),
            entity.getStartDate(),
            entity.getEndDate()
        );
    }

    private static void apply(ServiceCalendar entity, ServiceCalendarDto request) {
        if (request.startDate() == null || request.endDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start and end dates are required");
        }
        entity.setMonday(request.monday());
        entity.setTuesday(request.tuesday());
        entity.setWednesday(request.wednesday());
        entity.setThursday(request.thursday());
        entity.setFriday(request.friday());
        entity.setSaturday(request.saturday());
        entity.setSunday(request.sunday());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
    }
}
