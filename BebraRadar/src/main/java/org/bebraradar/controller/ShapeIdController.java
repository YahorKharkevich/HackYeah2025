package org.bebraradar.controller;

import org.bebraradar.dto.ShapeIdDto;
import org.bebraradar.entity.ShapeIdEntity;
import org.bebraradar.repository.ShapeIdRepository;
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
@RequestMapping("/shape-ids")
public class ShapeIdController {

    private final ShapeIdRepository shapeIdRepository;

    public ShapeIdController(ShapeIdRepository shapeIdRepository) {
        this.shapeIdRepository = shapeIdRepository;
    }

    @GetMapping
    public List<ShapeIdDto> getAll() {
        return shapeIdRepository.findAll().stream()
            .map(shape -> new ShapeIdDto(shape.getId()))
            .toList();
    }

    @GetMapping("/{id}")
    public ShapeIdDto getById(@PathVariable String id) {
        return shapeIdRepository.findById(id)
            .map(shape -> new ShapeIdDto(shape.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shape id not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShapeIdDto create(@RequestBody ShapeIdDto request) {
        if (request.id() == null || request.id().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shape id is required");
        }
        if (shapeIdRepository.existsById(request.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shape id already exists: " + request.id());
        }
        ShapeIdEntity entity = new ShapeIdEntity(request.id());
        return new ShapeIdDto(shapeIdRepository.save(entity).getId());
    }

    @PutMapping("/{id}")
    public ShapeIdDto replace(@PathVariable String id, @RequestBody ShapeIdDto request) {
        if (request.id() != null && !id.equals(request.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shape id mismatch");
        }
        ShapeIdEntity entity = shapeIdRepository.findById(id).orElse(null);
        if (entity == null) {
            entity = new ShapeIdEntity(id);
        }
        return new ShapeIdDto(shapeIdRepository.save(entity).getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        try {
            shapeIdRepository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shape id not found: " + id, ex);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shape id is referenced and cannot be removed", ex);
        }
    }
}
