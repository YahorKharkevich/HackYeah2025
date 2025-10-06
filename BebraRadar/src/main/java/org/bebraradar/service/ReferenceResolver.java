package org.bebraradar.service;

import org.bebraradar.entity.Route;
import org.bebraradar.entity.ShapeIdEntity;
import org.bebraradar.entity.Stop;
import org.bebraradar.entity.VehiclePositionCurrent;
import org.bebraradar.entity.Trip;
import org.bebraradar.entity.UserAccount;
import org.bebraradar.entity.ServiceCalendar;
import org.bebraradar.repository.RouteRepository;
import org.bebraradar.repository.ShapeIdRepository;
import org.bebraradar.repository.StopRepository;
import org.bebraradar.repository.TripRepository;
import org.bebraradar.repository.UserAccountRepository;
import org.bebraradar.repository.ServiceCalendarRepository;
import org.bebraradar.repository.VehiclePositionCurrentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReferenceResolver {

    private final RouteRepository routeRepository;
    private final ShapeIdRepository shapeIdRepository;
    private final TripRepository tripRepository;
    private final StopRepository stopRepository;
    private final UserAccountRepository userAccountRepository;
    private final ServiceCalendarRepository calendarRepository;
    private final VehiclePositionCurrentRepository vehicleRepository;

    public ReferenceResolver(RouteRepository routeRepository,
                             ShapeIdRepository shapeIdRepository,
                             TripRepository tripRepository,
                             StopRepository stopRepository,
                             UserAccountRepository userAccountRepository,
                             ServiceCalendarRepository calendarRepository,
                             VehiclePositionCurrentRepository vehicleRepository) {
        this.routeRepository = routeRepository;
        this.shapeIdRepository = shapeIdRepository;
        this.tripRepository = tripRepository;
        this.stopRepository = stopRepository;
        this.userAccountRepository = userAccountRepository;
        this.calendarRepository = calendarRepository;
        this.vehicleRepository = vehicleRepository;
    }

    public Route requireRoute(String id) {
        return routeRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found: " + id));
    }

    public ShapeIdEntity requireShape(String id) {
        return shapeIdRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shape not found: " + id));
    }

    public ShapeIdEntity resolveShapeNullable(String id) {
        if (id == null) {
            return null;
        }
        return shapeIdRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shape not found: " + id));
    }

    public Trip requireTrip(Long id) {
        return tripRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: " + id));
    }

    public Stop requireStop(String id) {
        return stopRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found: " + id));
    }

    public UserAccount requireUser(Long id) {
        return userAccountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }

    public UserAccount resolveUserNullable(Long id) {
        if (id == null) {
            return null;
        }
        return requireUser(id);
    }

    public ServiceCalendar requireCalendar(String id) {
        return calendarRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Calendar not found: " + id));
    }

    public VehiclePositionCurrent resolveVehicleNullable(String vehicleNo) {
        if (vehicleNo == null || vehicleNo.isBlank()) {
            return null;
        }
        return vehicleRepository.findById(vehicleNo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found: " + vehicleNo));
    }
}
