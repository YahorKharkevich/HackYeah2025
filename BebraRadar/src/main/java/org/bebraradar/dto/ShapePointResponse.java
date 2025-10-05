package org.bebraradar.dto;

public record ShapePointResponse(String shapeId,
                                 Integer sequence,
                                 Double latitude,
                                 Double longitude,
                                 Double distanceTraveled) {
}
