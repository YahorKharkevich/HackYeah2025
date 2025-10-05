package org.bebraradar;

import org.bebraradar.dto.AnomalyResponse;
import org.bebraradar.dto.GeoEventResponse;
import org.bebraradar.dto.TimetableEventResponse;
import org.bebraradar.repository.ExactTripAnomalyRepository;
import org.bebraradar.repository.ExactTripEventGeoLocationRepository;
import org.bebraradar.repository.ExactTripEventTimetableRepository;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventsController {

    private static final Sort TIMESTAMP_DESC = Sort.by(Sort.Direction.DESC, "timestamp");

    private final ExactTripEventGeoLocationRepository geoLocationRepository;
    private final ExactTripEventTimetableRepository timetableRepository;
    private final ExactTripAnomalyRepository anomalyRepository;

    public EventsController(ExactTripEventGeoLocationRepository geoLocationRepository,
                            ExactTripEventTimetableRepository timetableRepository,
                            ExactTripAnomalyRepository anomalyRepository) {
        this.geoLocationRepository = geoLocationRepository;
        this.timetableRepository = timetableRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @GetMapping("/{type}")
    public ResponseEntity<?> getEvents(@PathVariable String type,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                       OffsetDateTime since) {
        return switch (type.toLowerCase()) {
            case "geolocation" -> ResponseEntity.ok(fetchGeoEvents(since));
            case "timetable" -> ResponseEntity.ok(fetchTimetableEvents(since));
            case "anomaly" -> ResponseEntity.ok(fetchAnomalies(since));
            default -> ResponseEntity.notFound().build();
        };
    }

    private List<GeoEventResponse> fetchGeoEvents(OffsetDateTime since) {
        return (since == null
            ? geoLocationRepository.findAll(TIMESTAMP_DESC)
            : geoLocationRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(since))
            .stream()
            .map(event -> new GeoEventResponse(
                event.getId(),
                event.getTrip().getId(),
                event.getUser() == null ? null : event.getUser().getId(),
                event.getTimestamp(),
                event.getLatitude(),
                event.getLongitude(),
                event.getGpsAccuracyMeters(),
                event.getType()))
            .toList();
    }

    private List<TimetableEventResponse> fetchTimetableEvents(OffsetDateTime since) {
        return (since == null
            ? timetableRepository.findAll(TIMESTAMP_DESC)
            : timetableRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(since))
            .stream()
            .map(event -> new TimetableEventResponse(
                event.getId(),
                event.getTrip().getId(),
                event.getUser() == null ? null : event.getUser().getId(),
                event.getTimestamp(),
                event.getLatitude(),
                event.getLongitude(),
                event.getGpsAccuracyMeters(),
                event.getType(),
                event.getReportedTime()))
            .toList();
    }

    private List<AnomalyResponse> fetchAnomalies(OffsetDateTime since) {
        return (since == null
            ? anomalyRepository.findAll(TIMESTAMP_DESC)
            : anomalyRepository.findByTimestampGreaterThanEqualOrderByTimestampDesc(since))
            .stream()
            .map(event -> new AnomalyResponse(
                event.getId(),
                event.getTrip().getId(),
                event.getUser() == null ? null : event.getUser().getId(),
                event.getTimestamp(),
                event.getLatitude(),
                event.getLongitude(),
                event.getGpsAccuracyMeters(),
                event.getType(),
                event.getEstimatedDelay()))
            .toList();
    }
}
