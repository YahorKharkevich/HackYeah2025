package org.bebraradar.dto;

public record ShapePointRequest(String shapeId,
                                Integer sequence,
                                Double latitude,
                                Double longitude,
                                Double distanceTraveled) {
}
