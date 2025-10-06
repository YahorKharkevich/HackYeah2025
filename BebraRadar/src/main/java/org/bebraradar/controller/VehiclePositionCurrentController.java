package org.bebraradar.controller;

import org.bebraradar.dto.VehiclePositionRequest;
import org.bebraradar.dto.VehiclePositionResponse;
import org.bebraradar.entity.VehiclePositionCurrent;
import org.bebraradar.repository.VehiclePositionCurrentRepository;
import org.bebraradar.service.ReferenceResolver;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/vehicle-positions")
public class VehiclePositionCurrentController {

    private final VehiclePositionCurrentRepository repository;
    private final ReferenceResolver resolver;

    public VehiclePositionCurrentController(VehiclePositionCurrentRepository repository, ReferenceResolver resolver) {
        this.repository = repository;
        this.resolver = resolver;
    }

    @GetMapping
    public List<VehiclePositionResponse> getAll() {
        return repository.findAll().stream().map(VehiclePositionCurrentController::toDto).toList();
    }

    @GetMapping("/{vehicleNo}")
    public VehiclePositionResponse getById(@PathVariable String vehicleNo) {
        return repository.findById(vehicleNo)
            .map(VehiclePositionCurrentController::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found: " + vehicleNo));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehiclePositionResponse create(@RequestBody VehiclePositionRequest request) {
        validate(request);
        if (repository.existsById(request.vehicleNo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle already exists: " + request.vehicleNo());
        }
        VehiclePositionCurrent entity = apply(new VehiclePositionCurrent(), request);
        return toDto(repository.save(entity));
    }

    @PutMapping("/{vehicleNo}")
    public VehiclePositionResponse replace(@PathVariable String vehicleNo, @RequestBody VehiclePositionRequest request) {
        if (request.vehicleNo() != null && !vehicleNo.equals(request.vehicleNo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle number mismatch");
        }
        VehiclePositionCurrent entity = repository.findById(vehicleNo).orElse(new VehiclePositionCurrent());
        entity.setVehicleNo(vehicleNo);
        entity = apply(entity, request);
        return toDto(repository.save(entity));
    }

    @DeleteMapping("/{vehicleNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String vehicleNo) {
        try {
            repository.deleteById(vehicleNo);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found: " + vehicleNo, ex);
        }
    }

    private VehiclePositionCurrent apply(VehiclePositionCurrent entity, VehiclePositionRequest request) {
        entity.setVehicleNo(request.vehicleNo());
        entity.setTrip(resolver.requireTrip(request.tripId()));
        entity.setTimestamp(request.timestamp());
        entity.setLastStopTimestamp(request.lastStopTimestamp());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setSpeedMps(request.speedMps());
        entity.setBearingDeg(request.bearingDeg());
        entity.setGpsAccuracyMeters(request.gpsAccuracyMeters());
        return entity;
    }

    private static void validate(VehiclePositionRequest request) {
        if (request.vehicleNo() == null || request.vehicleNo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vehicleNo is required");
        }
        if (request.tripId() == null || request.timestamp() == null || request.lastStopTimestamp() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tripId, timestamp, lastStopTimestamp are required");
        }
    }

    private static VehiclePositionResponse toDto(VehiclePositionCurrent entity) {
        return new VehiclePositionResponse(
            entity.getVehicleNo(),
            entity.getTrip().getId(),
            entity.getTimestamp(),
            entity.getLastStopTimestamp(),
            entity.getLatitude(),
            entity.getLongitude(),
            entity.getSpeedMps(),
            entity.getBearingDeg(),
            entity.getGpsAccuracyMeters()
        );
    }
}

