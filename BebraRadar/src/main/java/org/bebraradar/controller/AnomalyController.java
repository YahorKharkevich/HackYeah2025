package org.bebraradar.controller;

import org.bebraradar.dto.AnomalyRequest;
import org.bebraradar.dto.AnomalyResponse;
import org.bebraradar.entity.ExactTripAnomaly;
import org.bebraradar.repository.ExactTripAnomalyRepository;
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
@RequestMapping("/anomalies")
public class AnomalyController {

    private final ExactTripAnomalyRepository repository;
    private final ReferenceResolver referenceResolver;

    public AnomalyController(ExactTripAnomalyRepository repository, ReferenceResolver referenceResolver) {
        this.repository = repository;
        this.referenceResolver = referenceResolver;
    }

    @GetMapping
    public List<AnomalyResponse> getAll() {
        return repository.findAll().stream()
            .map(AnomalyController::toDto)
            .toList();
    }

    @GetMapping("/{id}")
    public AnomalyResponse getById(@PathVariable Long id) {
        return repository.findById(id)
            .map(AnomalyController::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anomaly not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnomalyResponse create(@RequestBody AnomalyRequest request) {
        validate(request);
        ExactTripAnomaly entity = new ExactTripAnomaly(
            referenceResolver.requireTrip(request.tripId()),
            referenceResolver.resolveUserNullable(request.userId()),
            request.timestamp(),
            request.latitude(),
            request.longitude(),
            request.gpsAccuracyMeters(),
            request.type(),
            request.estimatedDelay()
        );
        entity.setVehicle(referenceResolver.resolveVehicleNullable(request.vehicleNo()));
        return toDto(repository.save(entity));
    }

    @PutMapping("/{id}")
    public AnomalyResponse update(@PathVariable Long id, @RequestBody AnomalyRequest request) {
        ExactTripAnomaly entity = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anomaly not found: " + id));
        validate(request);
        entity.setTrip(referenceResolver.requireTrip(request.tripId()));
        entity.setUser(referenceResolver.resolveUserNullable(request.userId()));
        entity.setVehicle(referenceResolver.resolveVehicleNullable(request.vehicleNo()));
        entity.setTimestamp(request.timestamp());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setGpsAccuracyMeters(request.gpsAccuracyMeters());
        entity.setType(request.type());
        entity.setEstimatedDelay(request.estimatedDelay());
        return toDto(repository.save(entity));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        try {
            repository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Anomaly not found: " + id, ex);
        }
    }

    private static AnomalyResponse toDto(ExactTripAnomaly entity) {
        Long userId = entity.getUser() == null ? null : entity.getUser().getId();
        String vehicleNo = entity.getVehicle() == null ? null : entity.getVehicle().getVehicleNo();
        return new AnomalyResponse(
            entity.getId(),
            entity.getTrip().getId(),
            vehicleNo,
            userId,
            entity.getTimestamp(),
            entity.getLatitude(),
            entity.getLongitude(),
            entity.getGpsAccuracyMeters(),
            entity.getType(),
            entity.getEstimatedDelay()
        );
    }

    private static void validate(AnomalyRequest request) {
        if (request.tripId() == null || request.timestamp() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip id and timestamp are required");
        }
        if (request.estimatedDelay() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estimatedDelay is required");
        }
    }
}
