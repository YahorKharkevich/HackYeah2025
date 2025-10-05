package org.bebraradar.controller;

import org.bebraradar.dto.TimetableEventRequest;
import org.bebraradar.dto.TimetableEventResponse;
import org.bebraradar.entity.ExactTripEventTimetable;
import org.bebraradar.repository.ExactTripEventTimetableRepository;
import org.bebraradar.service.ReferenceResolver;
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
@RequestMapping("/timetable-events")
public class TimetableEventController {

    private final ExactTripEventTimetableRepository repository;
    private final ReferenceResolver referenceResolver;

    public TimetableEventController(ExactTripEventTimetableRepository repository,
                                    ReferenceResolver referenceResolver) {
        this.repository = repository;
        this.referenceResolver = referenceResolver;
    }

    @GetMapping
    public List<TimetableEventResponse> getAll() {
        return repository.findAll().stream()
            .map(TimetableEventController::toDto)
            .toList();
    }

    @GetMapping("/{id}")
    public TimetableEventResponse getById(@PathVariable Long id) {
        return repository.findById(id)
            .map(TimetableEventController::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timetable event not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimetableEventResponse create(@RequestBody TimetableEventRequest request) {
        validate(request);
        ExactTripEventTimetable entity = new ExactTripEventTimetable(
            referenceResolver.requireTrip(request.tripId()),
            referenceResolver.resolveUserNullable(request.userId()),
            request.timestamp(),
            request.latitude(),
            request.longitude(),
            request.gpsAccuracyMeters(),
            request.type(),
            request.reportedTime()
        );
        return toDto(repository.save(entity));
    }

    @PutMapping("/{id}")
    public TimetableEventResponse update(@PathVariable Long id, @RequestBody TimetableEventRequest request) {
        ExactTripEventTimetable entity = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timetable event not found: " + id));
        validate(request);
        entity.setTrip(referenceResolver.requireTrip(request.tripId()));
        entity.setUser(referenceResolver.resolveUserNullable(request.userId()));
        entity.setTimestamp(request.timestamp());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setGpsAccuracyMeters(request.gpsAccuracyMeters());
        entity.setType(request.type());
        entity.setReportedTime(request.reportedTime());
        return toDto(repository.save(entity));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        try {
            repository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Timetable event not found: " + id, ex);
        }
    }

    private static TimetableEventResponse toDto(ExactTripEventTimetable entity) {
        Long userId = entity.getUser() == null ? null : entity.getUser().getId();
        return new TimetableEventResponse(
            entity.getId(),
            entity.getTrip().getId(),
            userId,
            entity.getTimestamp(),
            entity.getLatitude(),
            entity.getLongitude(),
            entity.getGpsAccuracyMeters(),
            entity.getType(),
            entity.getReportedTime()
        );
    }

    private static void validate(TimetableEventRequest request) {
        if (request.tripId() == null || request.timestamp() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip id and timestamp are required");
        }
        if (request.reportedTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reportedTime is required");
        }
    }
}
