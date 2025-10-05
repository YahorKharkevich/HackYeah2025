package org.bebraradar.controller;

import org.bebraradar.dto.RouteDto;
import org.bebraradar.entity.Route;
import org.bebraradar.repository.RouteRepository;
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
@RequestMapping("/routes")
public class RouteController {

    private final RouteRepository routeRepository;

    public RouteController(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    @GetMapping
    public List<RouteDto> getAll() {
        return routeRepository.findAll().stream()
            .map(RouteController::toDto)
            .toList();
    }

    @GetMapping("/{id}")
    public RouteDto getById(@PathVariable String id) {
        return routeRepository.findById(id)
            .map(RouteController::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RouteDto create(@RequestBody RouteDto request) {
        if (request.id() == null || request.id().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route id is required");
        }
        if (routeRepository.existsById(request.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Route already exists: " + request.id());
        }
        Route route = new Route(request.id());
        return toDto(routeRepository.save(route));
    }

    @PutMapping("/{id}")
    public RouteDto replace(@PathVariable String id, @RequestBody RouteDto request) {
        if (request.id() != null && !id.equals(request.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route id mismatch");
        }
        Route route = routeRepository.findById(id).orElse(null);
        if (route == null) {
            route = new Route(id);
        }
        return toDto(routeRepository.save(route));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        try {
            routeRepository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found: " + id, ex);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Route is referenced and cannot be removed", ex);
        }
    }

    private static RouteDto toDto(Route route) {
        return new RouteDto(route.getId());
    }
}
