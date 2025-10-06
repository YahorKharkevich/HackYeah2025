package org.bebraradar.controller;

import org.bebraradar.dto.RouteDto;
import org.bebraradar.dto.TripScheduleDto;
import org.bebraradar.service.TimetableService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/timetable")
public class TimetableQueryController {

    private final TimetableService timetableService;

    public TimetableQueryController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    // GET /timetable/routes?date=YYYY-MM-DD
    // GET /timetable/routes?weekday=Mon|Tue|...
    @GetMapping("/routes")
    public List<RouteDto> getRoutesForDay(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,
        @RequestParam(required = false) String weekday
    ) {
        if (date == null && (weekday == null || weekday.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide either date or weekday");
        }
        if (date != null) {
            return timetableService.getRoutesForDate(date);
        }
        try {
            return timetableService.getRoutesForWeekday(weekday);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    // GET /timetable/routes/{routeId}/trips?date=YYYY-MM-DD
    @GetMapping("/routes/{routeId}/trips")
    public List<TripScheduleDto> getRouteTripsForDate(
        @PathVariable String routeId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }
        return timetableService.getRouteScheduleForDate(routeId, date);
    }
}

