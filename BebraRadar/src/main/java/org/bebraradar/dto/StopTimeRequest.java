package org.bebraradar.dto;

public record StopTimeRequest(Long tripId, Integer stopSequence, String stopId, Integer arrivalTime,
                              Integer departureTime) {
}
