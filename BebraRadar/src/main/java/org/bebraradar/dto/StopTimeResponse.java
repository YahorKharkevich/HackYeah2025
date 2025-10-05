package org.bebraradar.dto;

public record StopTimeResponse(Long tripId, Integer stopSequence, String stopId, Integer arrivalTime,
                               Integer departureTime) {
}
