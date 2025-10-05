package org.bebraradar.dto;

import java.time.OffsetDateTime;

public record AnomalyResponse(Long id,
                              Long tripId,
                              Long userId,
                              OffsetDateTime timestamp,
                              Double latitude,
                              Double longitude,
                              Double gpsAccuracyMeters,
                              String type,
                              Double estimatedDelay) {
}
