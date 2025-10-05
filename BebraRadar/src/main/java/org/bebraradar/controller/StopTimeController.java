package org.bebraradar.controller;

import org.bebraradar.dto.StopTimeRequest;
import org.bebraradar.dto.StopTimeResponse;
import org.bebraradar.entity.StopTime;
import org.bebraradar.entity.StopTimeId;
import org.bebraradar.repository.StopTimeRepository;
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
@RequestMapping("/stop-times")
public class StopTimeController {

    private final StopTimeRepository stopTimeRepository;
    private final ReferenceResolver referenceResolver;

    public StopTimeController(StopTimeRepository stopTimeRepository, ReferenceResolver referenceResolver) {
        this.stopTimeRepository = stopTimeRepository;
        this.referenceResolver = referenceResolver;
    }

    @GetMapping
    public List<StopTimeResponse> getAll() {
        return stopTimeRepository.findAll().stream()
            .map(StopTimeController::toDto)
            .toList();
    }

    @GetMapping("/{tripId}/{stopSequence}")
    public StopTimeResponse getById(@PathVariable Long tripId, @PathVariable Integer stopSequence) {
        StopTimeId id = new StopTimeId(tripId, stopSequence);
        return stopTimeRepository.findById(id)
            .map(StopTimeController::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Stop time not found: " + tripId + "/" + stopSequence));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StopTimeResponse create(@RequestBody StopTimeRequest request) {
        validate(request);
        StopTimeId id = new StopTimeId(request.tripId(), request.stopSequence());
        if (stopTimeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stop time already exists for provided id");
        }
        StopTime stopTime = new StopTime(
            id,
            referenceResolver.requireTrip(request.tripId()),
            referenceResolver.requireStop(request.stopId()),
            request.arrivalTime(),
            request.departureTime()
        );
        return toDto(stopTimeRepository.save(stopTime));
    }

    @PutMapping("/{tripId}/{stopSequence}")
    public StopTimeResponse update(@PathVariable Long tripId,
                                   @PathVariable Integer stopSequence,
                                   @RequestBody StopTimeRequest request) {
        if (!tripId.equals(request.tripId()) || !stopSequence.equals(request.stopSequence())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stop time id mismatch");
        }
        StopTimeId id = new StopTimeId(tripId, stopSequence);
        StopTime stopTime = stopTimeRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Stop time not found: " + tripId + "/" + stopSequence));
        validate(request);
        stopTime.setTrip(referenceResolver.requireTrip(request.tripId()));
        stopTime.setStop(referenceResolver.requireStop(request.stopId()));
        stopTime.setArrivalTime(request.arrivalTime());
        stopTime.setDepartureTime(request.departureTime());
        return toDto(stopTimeRepository.save(stopTime));
    }

    @DeleteMapping("/{tripId}/{stopSequence}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long tripId, @PathVariable Integer stopSequence) {
        StopTimeId id = new StopTimeId(tripId, stopSequence);
        try {
            stopTimeRepository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Stop time not found: " + tripId + "/" + stopSequence, ex);
        }
    }

    private static StopTimeResponse toDto(StopTime stopTime) {
        return new StopTimeResponse(
            stopTime.getId().getTripId(),
            stopTime.getId().getStopSequence(),
            stopTime.getStop().getId(),
            stopTime.getArrivalTime(),
            stopTime.getDepartureTime()
        );
    }

    private static void validate(StopTimeRequest request) {
        if (request.tripId() == null || request.stopSequence() == null || request.stopId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip id, stop sequence and stop id are required");
        }
        if (request.arrivalTime() == null || request.departureTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arrival and departure time are required");
        }
    }
}
