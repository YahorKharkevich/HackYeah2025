package org.bebraradar.controller;

import org.bebraradar.dto.TripRequest;
import org.bebraradar.dto.TripResponse;
import org.bebraradar.entity.ShapeIdEntity;
import org.bebraradar.entity.Trip;
import org.bebraradar.repository.TripRepository;
import org.bebraradar.service.ReferenceResolver;
import org.springframework.dao.DataIntegrityViolationException;
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
@RequestMapping("/trips")
public class TripController {

    private final TripRepository tripRepository;
    private final ReferenceResolver referenceResolver;

    public TripController(TripRepository tripRepository, ReferenceResolver referenceResolver) {
        this.tripRepository = tripRepository;
        this.referenceResolver = referenceResolver;
    }

    @GetMapping
    public List<TripResponse> getAll() {
        return tripRepository.findAll().stream()
            .map(TripController::toDto)
            .toList();
    }

    @GetMapping("/{id}")
    public TripResponse getById(@PathVariable Long id) {
        return tripRepository.findById(id)
            .map(TripController::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(@RequestBody TripRequest request) {
        validate(request);
        Trip trip = new Trip(
            referenceResolver.requireRoute(request.routeId()),
            request.startTime(),
            referenceResolver.requireCalendar(request.serviceId()),
            referenceResolver.resolveShapeNullable(request.shapeId())
        );
        return toDto(tripRepository.save(trip));
    }

    @PutMapping("/{id}")
    public TripResponse update(@PathVariable Long id, @RequestBody TripRequest request) {
        Trip trip = tripRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: " + id));
        apply(trip, request);
        return toDto(tripRepository.save(trip));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        try {
            tripRepository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: " + id, ex);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip is referenced and cannot be removed", ex);
        }
    }

    private void apply(Trip trip, TripRequest request) {
        validate(request);
        trip.setRoute(referenceResolver.requireRoute(request.routeId()));
        trip.setStartTime(request.startTime());
        trip.setService(referenceResolver.requireCalendar(request.serviceId()));
        ShapeIdEntity shape = referenceResolver.resolveShapeNullable(request.shapeId());
        trip.setShape(shape);
    }

    private static void validate(TripRequest request) {
        if (request.routeId() == null || request.routeId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route id is required");
        }
        if (request.startTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time is required");
        }
        if (request.serviceId() == null || request.serviceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service id is required");
        }
    }

    private static TripResponse toDto(Trip trip) {
        String shapeId = trip.getShape() == null ? null : trip.getShape().getId();
        return new TripResponse(
            trip.getId(),
            trip.getRoute().getId(),
            trip.getStartTime(),
            trip.getService().getId(),
            shapeId
        );
    }
}
