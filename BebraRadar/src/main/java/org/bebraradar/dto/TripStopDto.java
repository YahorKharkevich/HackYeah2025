package org.bebraradar.dto;

public record TripStopDto(
    Integer stopSequence,
    String stopId,
    String stopName,
    Integer arrivalTime,
    Integer departureTime
) {}

