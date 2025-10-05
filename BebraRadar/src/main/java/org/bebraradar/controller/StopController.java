package org.bebraradar.controller;

import org.bebraradar.dto.StopDto;
import org.bebraradar.entity.Stop;
import org.bebraradar.repository.StopRepository;
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
@RequestMapping("/stops")
public class StopController {

    private final StopRepository stopRepository;

    public StopController(StopRepository stopRepository) {
        this.stopRepository = stopRepository;
    }

    @GetMapping
    public List<StopDto> getAll() {
        return stopRepository.findAll().stream()
            .map(StopController::toDto)
            .toList();
    }

    @GetMapping("/{id}")
    public StopDto getById(@PathVariable String id) {
        return stopRepository.findById(id)
            .map(StopController::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StopDto create(@RequestBody StopDto request) {
        validateRequest(request);
        if (stopRepository.existsById(request.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stop already exists: " + request.id());
        }
        Stop stop = new Stop(request.id(), request.name(), request.latitude(), request.longitude());
        apply(stop, request);
        return toDto(stopRepository.save(stop));
    }

    @PutMapping("/{id}")
    public StopDto replace(@PathVariable String id, @RequestBody StopDto request) {
        if (request.id() != null && !id.equals(request.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stop id mismatch");
        }
        Stop stop = stopRepository.findById(id).orElse(null);
        if (stop == null) {
            Stop created = new Stop(id, request.name(), request.latitude(), request.longitude());
            apply(created, request);
            return toDto(stopRepository.save(created));
        }
        apply(stop, request);
        return toDto(stopRepository.save(stop));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        try {
            stopRepository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found: " + id, ex);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stop is referenced and cannot be removed", ex);
        }
    }

    private static StopDto toDto(Stop stop) {
        return new StopDto(stop.getId(), stop.getName(), stop.getLatitude(), stop.getLongitude());
    }

    private static void apply(Stop stop, StopDto request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stop name is required");
        }
        stop.setName(request.name());
        stop.setLatitude(request.latitude());
        stop.setLongitude(request.longitude());
    }

    private static void validateRequest(StopDto request) {
        if (request.id() == null || request.id().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stop id is required");
        }
    }
}
