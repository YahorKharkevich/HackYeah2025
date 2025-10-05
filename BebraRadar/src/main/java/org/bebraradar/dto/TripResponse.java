package org.bebraradar.dto;

import java.time.OffsetDateTime;

public record TripResponse(Long id, String routeId, OffsetDateTime startTime, String vehicleNumber, String shapeId) {
}
