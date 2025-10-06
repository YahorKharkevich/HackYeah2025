package org.bebraradar.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TripScheduleDto(
    Long tripId,
    String routeId,
    OffsetDateTime startTime,
    List<TripStopDto> stops
) {}

