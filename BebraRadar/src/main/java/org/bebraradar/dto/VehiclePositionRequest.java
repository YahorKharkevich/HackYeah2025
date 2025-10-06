package org.bebraradar.dto;

import java.time.OffsetDateTime;

public record VehiclePositionRequest(
    String vehicleNo,
    Long tripId,
    OffsetDateTime timestamp,
    OffsetDateTime lastStopTimestamp,
    Double latitude,
    Double longitude,
    Double speedMps,
    Double bearingDeg,
    Double gpsAccuracyMeters
) {}

