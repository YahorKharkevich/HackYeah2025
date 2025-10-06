package org.bebraradar.dto;

import java.time.OffsetDateTime;

public record TripRequest(String routeId, OffsetDateTime startTime, String serviceId, String shapeId) {
}
