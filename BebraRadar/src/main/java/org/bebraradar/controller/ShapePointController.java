package org.bebraradar.controller;

import org.bebraradar.dto.ShapePointRequest;
import org.bebraradar.dto.ShapePointResponse;
import org.bebraradar.entity.ShapePoint;
import org.bebraradar.entity.ShapePointId;
import org.bebraradar.repository.ShapePointRepository;
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
@RequestMapping("/shape-points")
public class ShapePointController {

    private final ShapePointRepository shapePointRepository;
    private final ReferenceResolver referenceResolver;

    public ShapePointController(ShapePointRepository shapePointRepository, ReferenceResolver referenceResolver) {
        this.shapePointRepository = shapePointRepository;
        this.referenceResolver = referenceResolver;
    }

    @GetMapping
    public List<ShapePointResponse> getAll() {
        return shapePointRepository.findAll().stream()
            .map(ShapePointController::toDto)
            .toList();
    }

    @GetMapping("/{shapeId}/{sequence}")
    public ShapePointResponse getById(@PathVariable String shapeId, @PathVariable Integer sequence) {
        ShapePointId id = new ShapePointId(shapeId, sequence);
        return shapePointRepository.findById(id)
            .map(ShapePointController::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Shape point not found: " + shapeId + "/" + sequence));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShapePointResponse create(@RequestBody ShapePointRequest request) {
        validate(request);
        ShapePointId id = new ShapePointId(request.shapeId(), request.sequence());
        if (shapePointRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shape point already exists for provided id");
        }
        ShapePoint entity = new ShapePoint(
            id,
            referenceResolver.requireShape(request.shapeId()),
            request.latitude(),
            request.longitude(),
            request.distanceTraveled()
        );
        return toDto(shapePointRepository.save(entity));
    }

    @PutMapping("/{shapeId}/{sequence}")
    public ShapePointResponse update(@PathVariable String shapeId,
                                     @PathVariable Integer sequence,
                                     @RequestBody ShapePointRequest request) {
        if (!shapeId.equals(request.shapeId()) || !sequence.equals(request.sequence())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shape point id mismatch");
        }
        ShapePointId id = new ShapePointId(shapeId, sequence);
        ShapePoint entity = shapePointRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Shape point not found: " + shapeId + "/" + sequence));
        validate(request);
        entity.setShape(referenceResolver.requireShape(request.shapeId()));
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setDistanceTraveled(request.distanceTraveled());
        return toDto(shapePointRepository.save(entity));
    }

    @DeleteMapping("/{shapeId}/{sequence}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String shapeId, @PathVariable Integer sequence) {
        ShapePointId id = new ShapePointId(shapeId, sequence);
        try {
            shapePointRepository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Shape point not found: " + shapeId + "/" + sequence, ex);
        }
    }

    private static ShapePointResponse toDto(ShapePoint entity) {
        return new ShapePointResponse(
            entity.getId().getShapeId(),
            entity.getId().getSequence(),
            entity.getLatitude(),
            entity.getLongitude(),
            entity.getDistanceTraveled()
        );
    }

    private static void validate(ShapePointRequest request) {
        if (request.shapeId() == null || request.shapeId().isBlank() || request.sequence() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shape id and sequence are required");
        }
        if (request.latitude() == null || request.longitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude and longitude are required");
        }
    }
}
