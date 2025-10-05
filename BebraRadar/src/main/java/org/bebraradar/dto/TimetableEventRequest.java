package org.bebraradar.dto;

import java.time.OffsetDateTime;

public record TimetableEventRequest(Long tripId,
                                    Long userId,
                                    OffsetDateTime timestamp,
                                    Double latitude,
                                    Double longitude,
                                    Double gpsAccuracyMeters,
                                    String type,
                                    OffsetDateTime reportedTime) {
}
