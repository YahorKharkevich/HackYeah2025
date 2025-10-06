package org.bebraradar.service;

import org.bebraradar.dto.RouteDto;
import org.bebraradar.dto.TripScheduleDto;
import org.bebraradar.dto.TripStopDto;
import org.bebraradar.entity.ServiceCalendar;
import org.bebraradar.entity.StopTime;
import org.bebraradar.entity.Trip;
import org.bebraradar.repository.ServiceCalendarRepository;
import org.bebraradar.repository.StopTimeRepository;
import org.bebraradar.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TimetableService {

    private final ServiceCalendarRepository calendarRepository;
    private final TripRepository tripRepository;
    private final StopTimeRepository stopTimeRepository;

    public TimetableService(ServiceCalendarRepository calendarRepository,
                            TripRepository tripRepository,
                            StopTimeRepository stopTimeRepository) {
        this.calendarRepository = calendarRepository;
        this.tripRepository = tripRepository;
        this.stopTimeRepository = stopTimeRepository;
    }

    public List<RouteDto> getRoutesForDate(LocalDate date) {
        List<String> activeServices = activeServiceIdsForDate(date);
        if (activeServices.isEmpty()) return List.of();
        List<Trip> trips = tripRepository.findByService_IdIn(activeServices);
        return trips.stream()
            .map(t -> t.getRoute().getId())
            .distinct()
            .sorted()
            .map(RouteDto::new)
            .toList();
    }

    public List<RouteDto> getRoutesForWeekday(String weekday) {
        DayOfWeek dow = parseWeekday(weekday);
        List<String> serviceIds = activeServiceIdsForWeekday(dow);
        if (serviceIds.isEmpty()) return List.of();
        return tripRepository.findByService_IdIn(serviceIds).stream()
            .map(t -> t.getRoute().getId())
            .distinct()
            .sorted()
            .map(RouteDto::new)
            .toList();
    }

    public List<TripScheduleDto> getRouteScheduleForDate(String routeId, LocalDate date) {
        List<String> activeServices = activeServiceIdsForDate(date);
        if (activeServices.isEmpty()) return List.of();
        List<Trip> trips = tripRepository.findByRoute_IdAndService_IdIn(routeId, activeServices);
        trips.sort(Comparator.comparing(Trip::getStartTime));
        return trips.stream().map(this::toTripScheduleDto).toList();
    }

    private TripScheduleDto toTripScheduleDto(Trip trip) {
        List<StopTime> stopTimes = stopTimeRepository.findByTrip_IdOrderById_StopSequenceAsc(trip.getId());
        List<TripStopDto> stops = new ArrayList<>(stopTimes.size());
        for (StopTime st : stopTimes) {
            stops.add(new TripStopDto(
                st.getId().getStopSequence(),
                st.getStop().getId(),
                st.getStop().getName(),
                st.getArrivalTime(),
                st.getDepartureTime()
            ));
        }
        return new TripScheduleDto(trip.getId(), trip.getRoute().getId(), trip.getStartTime(), stops);
    }

    private List<String> activeServiceIdsForDate(LocalDate date) {
        List<ServiceCalendar> candidates = calendarRepository
            .findAll()
            .stream()
            .filter(c -> !date.isBefore(c.getStartDate()) && !date.isAfter(c.getEndDate()))
            .toList();
        DayOfWeek dow = date.getDayOfWeek();
        return filterByDayOfWeek(candidates, dow).stream().map(ServiceCalendar::getId).toList();
    }

    private List<String> activeServiceIdsForWeekday(DayOfWeek dow) {
        List<ServiceCalendar> all = calendarRepository.findAll();
        return filterByDayOfWeek(all, dow).stream().map(ServiceCalendar::getId).toList();
    }

    private static List<ServiceCalendar> filterByDayOfWeek(Collection<ServiceCalendar> list, DayOfWeek dow) {
        return list.stream().filter(c -> switch (dow) {
            case MONDAY -> c.isMonday();
            case TUESDAY -> c.isTuesday();
            case WEDNESDAY -> c.isWednesday();
            case THURSDAY -> c.isThursday();
            case FRIDAY -> c.isFriday();
            case SATURDAY -> c.isSaturday();
            case SUNDAY -> c.isSunday();
        }).collect(Collectors.toList());
    }

    private static DayOfWeek parseWeekday(String weekday) {
        if (!StringUtils.hasText(weekday)) {
            throw new IllegalArgumentException("weekday is empty");
        }
        String w = weekday.trim().toUpperCase();
        return switch (w) {
            case "MON", "MONDAY", "ПН", "ПОНЕДЕЛЬНИК" -> DayOfWeek.MONDAY;
            case "TUE", "TUESDAY", "ВТ", "ВТОРНИК" -> DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY", "СР", "СРЕДА" -> DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY", "ЧТ", "ЧЕТВЕРГ" -> DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY", "ПТ", "ПЯТНИЦА" -> DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY", "СБ", "СУББОТА" -> DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY", "ВС", "ВОСКРЕСЕНЬЕ", "ВОСКРЕСЕНИЕ" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unknown weekday: " + weekday);
        };
    }
}

