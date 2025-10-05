package org.bebraradar.dto;

import java.time.LocalDate;

public record ServiceCalendarDto(String id,
                                 boolean monday,
                                 boolean tuesday,
                                 boolean wednesday,
                                 boolean thursday,
                                 boolean friday,
                                 boolean saturday,
                                 boolean sunday,
                                 LocalDate startDate,
                                 LocalDate endDate) {
}
