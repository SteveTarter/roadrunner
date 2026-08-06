package com.tarterware.roadrunner.controllers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tarterware.roadrunner.models.Bookmark;
import com.tarterware.roadrunner.models.BookmarkEntity;
import com.tarterware.roadrunner.models.SimulationSession;
import com.tarterware.roadrunner.models.SimulationSessionEntity;
import com.tarterware.roadrunner.models.VehicleRouteEntity;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.models.VehicleTelemetry;
import com.tarterware.roadrunner.ports.BookmarkEntityRepository;
import com.tarterware.roadrunner.ports.SimulationSessionEntityRepository;
import com.tarterware.roadrunner.ports.VehicleRouteRepository;
import com.tarterware.roadrunner.ports.VehicleTelemetryRepository;
import com.tarterware.roadrunner.security.UserPrincipal;
import com.tarterware.roadrunner.security.UserPrincipalFactory;

@RestController
@RequestMapping("/api")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "com.tarterware.roadrunner.persistence.postgis",
        name = "enabled",
        havingValue = "true"
)
public class DBPlaybackController
{
    private static final Logger log = LoggerFactory.getLogger(DBPlaybackController.class);
    private static final String UNSET_VALUE = "Unset";

    private final VehicleTelemetryRepository telemetryRepository;
    private final SimulationSessionEntityRepository sessionRepository;
    private final BookmarkEntityRepository bookmarkRepository;
    private final VehicleRouteRepository routeRepository;
    private final UserPrincipalFactory userPrincipalFactory;

    public DBPlaybackController(
            VehicleTelemetryRepository telemetryRepository,
            SimulationSessionEntityRepository sessionRepository,
            BookmarkEntityRepository bookmarkRepository,
            VehicleRouteRepository routeRepository,
            UserPrincipalFactory userPrincipalFactory)
    {
        this.telemetryRepository = telemetryRepository;
        this.sessionRepository = sessionRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.routeRepository = routeRepository;
        this.userPrincipalFactory = userPrincipalFactory;
        log.info("DBPlaybackController initialized and ready to serve PostGIS-backed mirrored REST requests.");
    }

    /* ---------------- Playback Endpoints ---------------- */

    @GetMapping("/db-playback/state")
    public ResponseEntity<PagedModel<VehicleState>> getVehicleStatesAtTimestamp(
            @RequestParam(defaultValue = UNSET_VALUE) String timestamp,
            @RequestParam(defaultValue = "5s") String windowPeriod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        Instant endTime;
        if (timestamp.equals(UNSET_VALUE))
        {
            endTime = Instant.now();
        }
        else
        {
            endTime = Instant.parse(timestamp);
        }

        Duration duration = DurationStyle.detect(windowPeriod).parse(windowPeriod);
        long msWindowPeriod = duration.toMillis();
        // Use a minimum of 10s window for live query to ensure we fetch recent active simulations
        if (timestamp.equals(UNSET_VALUE))
        {
            msWindowPeriod = Math.max(msWindowPeriod, 10000);
        }
        Instant startTime = endTime.minusMillis(msWindowPeriod);

        List<VehicleTelemetry> telemetryList = telemetryRepository.findLatestTelemetryWithinWindow(startTime, endTime);
        List<VehicleState> latestStates = telemetryList.stream()
                .map(this::mapToVehicleState)
                .toList();

        return buildPagedResponse(latestStates, page, pageSize);
    }

    @GetMapping("/db-playback/get-vehicle-state")
    public ResponseEntity<VehicleState> getVehicleStateFor(
            @RequestParam(defaultValue = UNSET_VALUE) String vehicleId,
            @RequestParam(defaultValue = UNSET_VALUE) String timestamp,
            @RequestParam(defaultValue = "5s") String windowPeriod)
    {
        Instant endTime;
        if (timestamp.equals(UNSET_VALUE))
        {
            endTime = Instant.now();
        }
        else
        {
            endTime = Instant.parse(timestamp);
        }

        Duration duration = DurationStyle.detect(windowPeriod).parse(windowPeriod);
        long msWindowPeriod = duration.toMillis();
        if (timestamp.equals(UNSET_VALUE))
        {
            msWindowPeriod = Math.max(msWindowPeriod, 10000);
        }
        Instant startTime = endTime.minusMillis(msWindowPeriod);

        List<VehicleTelemetry> telemetryList = telemetryRepository.findLatestTelemetryWithinWindow(startTime, endTime);
        Optional<VehicleState> state = telemetryList.stream()
                .filter(t -> t.getVehicleId().equals(vehicleId))
                .map(this::mapToVehicleState)
                .findFirst();

        if (state.isEmpty())
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(state.get(), HttpStatus.OK);
    }

    /* ---------------- Simulation Sessions Endpoints ---------------- */

    @GetMapping("/db-vehicle/simulation-sessions")
    public ResponseEntity<PagedModel<SimulationSession>> getSimulationSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        List<SimulationSessionEntity> entities = new ArrayList<>();
        sessionRepository.findAll().forEach(entities::add);

        entities.sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));

        List<SimulationSession> allSessions = entities.stream()
                .map(entity -> {
                    SimulationSession session = new SimulationSession();
                    session.setId(entity.getVehicleId());
                    session.setUsername(entity.getUsername());
                    session.setColorCode(entity.getColorCode());
                    session.setStart(entity.getStartTime().toEpochMilli());
                    if (entity.getEndTime() != null)
                    {
                        session.setEnd(entity.getEndTime().toEpochMilli());
                    }
                    return session;
                })
                .toList();

        int size = allSessions.size();
        List<SimulationSession> pageList = new ArrayList<>();
        if (size > 0)
        {
            int start = page * pageSize;
            if (start > size)
            {
                throw new IllegalArgumentException(
                        "Page " + page + " of page size " + pageSize + " outside " + size + " bounds!");
            }
            int end = Math.min(start + pageSize, size);
            pageList = allSessions.subList(start, end);
        }

        Page<SimulationSession> sessionPage = new PageImpl<>(
                pageList,
                PageRequest.of(page, pageSize),
                size);

        return new ResponseEntity<>(
                PagedModel.of(
                        sessionPage.getContent(),
                        new PagedModel.PageMetadata(pageSize, page, size)),
                HttpStatus.OK);
    }

    @GetMapping("/db-vehicle/get-vehicle-session/{vehicleId}")
    public ResponseEntity<SimulationSession> getVehicleSimulationSession(
            @PathVariable String vehicleId)
    {
        Optional<SimulationSessionEntity> entityOpt = sessionRepository.findById(vehicleId);

        if (entityOpt.isEmpty())
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        SimulationSessionEntity entity = entityOpt.get();
        SimulationSession session = new SimulationSession();
        session.setId(entity.getVehicleId());
        session.setUsername(entity.getUsername());
        session.setColorCode(entity.getColorCode());
        session.setStart(entity.getStartTime().toEpochMilli());
        if (entity.getEndTime() != null)
        {
            session.setEnd(entity.getEndTime().toEpochMilli());
        }

        return new ResponseEntity<>(session, HttpStatus.OK);
    }

    @GetMapping("/db-vehicle/get-vehicle-directions/{vehicleId}")
    public ResponseEntity<Map<String, Object>> getVehicleDirectionsFor(
            @PathVariable String vehicleId)
    {
        Optional<VehicleRouteEntity> routeOpt = routeRepository.findById(vehicleId);
        if (routeOpt.isEmpty())
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        VehicleRouteEntity entity = routeOpt.get();

        // Extract Coordinate coordinates from JTS LineString route path
        List<double[]> coords = new ArrayList<>();
        for (Coordinate coord : entity.getRoutePath().getCoordinates())
        {
            coords.add(new double[] { coord.x, coord.y });
        }

        // Reconstruct Mapbox-compatible nested JSON response
        Map<String, Object> geometryMap = Map.of("coordinates", coords, "type", "LineString"); // standard GeoJSON representation
        Map<String, Object> stepMap = Map.of("geometry", geometryMap);
        List<Map<String, Object>> stepsList = List.of(stepMap);
        Map<String, Object> legMap = Map.of("steps", stepsList);
        List<Map<String, Object>> legsList = List.of(legMap);
        Map<String, Object> routeMap = Map.of("legs", legsList);
        List<Map<String, Object>> routesList = List.of(routeMap);
        Map<String, Object> responseMap = Map.of("routes", routesList);

        return new ResponseEntity<>(responseMap, HttpStatus.OK);
    }

    /* ---------------- Bookmarks Endpoints ---------------- */

    @PostMapping("/db-bookmarks")
    public ResponseEntity<Bookmark> createBookmark(
            @RequestBody Bookmark bookmark,
            @AuthenticationPrincipal Jwt jwt)
    {
        UserPrincipal user = userPrincipalFactory.fromJwt(jwt);
        if (!user.isSuperuser())
        {
            throw new AccessDeniedException("User " + user.email() + " must be superuser to create bookmarks!");
        }

        BookmarkEntity entity = new BookmarkEntity();
        entity.setVehicleId(bookmark.getVehicleId());
        entity.setUsername(user.email());
        entity.setTitle(bookmark.getTitle());
        entity.setDescription(bookmark.getDescription());
        entity.setStartTime(Instant.ofEpochMilli(bookmark.getStart()));
        entity.setCreatedAt(Instant.now());

        bookmarkRepository.save(entity);

        return new ResponseEntity<>(bookmark, HttpStatus.OK);
    }

    @PutMapping("/db-bookmarks")
    public ResponseEntity<Bookmark> updateBookmark(
            @RequestBody Bookmark bookmark,
            @AuthenticationPrincipal Jwt jwt)
    {
        UserPrincipal user = userPrincipalFactory.fromJwt(jwt);
        if (!user.isSuperuser())
        {
            throw new AccessDeniedException("User " + user.email() + " must be superuser to update bookmarks!");
        }

        BookmarkEntity entity = bookmarkRepository.findById(bookmark.getVehicleId())
                .orElseThrow(() -> new IllegalArgumentException("Bookmark not found for vehicle ID: " + bookmark.getVehicleId()));
        entity.setTitle(bookmark.getTitle());
        entity.setDescription(bookmark.getDescription());
        entity.setStartTime(Instant.ofEpochMilli(bookmark.getStart()));

        bookmarkRepository.save(entity);

        return new ResponseEntity<>(bookmark, HttpStatus.OK);
    }

    @DeleteMapping("/db-bookmarks/{vehicleId}")
    public ResponseEntity<Void> deleteBookmark(
            @PathVariable String vehicleId,
            @AuthenticationPrincipal Jwt jwt)
    {
        UserPrincipal user = userPrincipalFactory.fromJwt(jwt);
        if (!user.isSuperuser())
        {
            throw new AccessDeniedException("User " + user.email() + " must be superuser to delete bookmarks!");
        }

        bookmarkRepository.deleteById(vehicleId);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/db-bookmarks")
    public ResponseEntity<List<Bookmark>> getAllBookmarks()
    {
        List<BookmarkEntity> entities = new ArrayList<>();
        bookmarkRepository.findAll().forEach(entities::add);

        List<Bookmark> bookmarks = entities.stream()
                .map(entity -> {
                    Bookmark b = new Bookmark();
                    b.setVehicleId(entity.getVehicleId());
                    b.setStart(entity.getStartTime().toEpochMilli());
                    b.setTitle(entity.getTitle());
                    b.setDescription(entity.getDescription());
                    return b;
                })
                .toList();

        return new ResponseEntity<>(bookmarks, HttpStatus.OK);
    }

    @GetMapping("/db-bookmarks/{vehicleId}")
    public ResponseEntity<Bookmark> getSingleBookmark(@PathVariable String vehicleId)
    {
        BookmarkEntity entity = bookmarkRepository.findById(vehicleId)
                .orElse(null);

        if (entity == null)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Bookmark b = new Bookmark();
        b.setVehicleId(entity.getVehicleId());
        b.setStart(entity.getStartTime().toEpochMilli());
        b.setTitle(entity.getTitle());
        b.setDescription(entity.getDescription());

        return new ResponseEntity<>(b, HttpStatus.OK);
    }

    /* ---------------- Utility Methods ---------------- */

    private VehicleState mapToVehicleState(VehicleTelemetry telemetry)
    {
        VehicleState state = new VehicleState();
        state.setId(telemetry.getVehicleId());
        state.setPositionLimited(telemetry.isPositionLimited());
        state.setPositionValid(telemetry.isPositionValid());
        if (telemetry.getPosition() != null)
        {
            state.setDegLatitude(telemetry.getPosition().getY());
            state.setDegLongitude(telemetry.getPosition().getX());
        }
        state.setDegBearing(telemetry.getHeading());
        state.setMetersPerSecond(telemetry.getSpeed());
        state.setColorCode(telemetry.getColorCode());
        state.setManagerHost(telemetry.getManagerHost());
        state.setMsEpochLastRun(telemetry.getTimestamp().toEpochMilli());
        state.setNsLastExec(telemetry.getNsLastExec());
        return state;
    }

    private ResponseEntity<PagedModel<VehicleState>> buildPagedResponse(
            List<VehicleState> states,
            int page,
            int pageSize)
    {
        int listSize = states.size();
        int start = Math.min(page * pageSize, listSize);
        int end = Math.min(start + pageSize, listSize);
        List<VehicleState> pageContent = states.subList(start, end);

        Page<VehicleState> vehicleStatePage = new PageImpl<>(
                pageContent,
                PageRequest.of(page, pageSize),
                listSize);

        return new ResponseEntity<>(
                PagedModel.of(
                        vehicleStatePage.getContent(),
                        new PagedModel.PageMetadata(pageSize, page, listSize)),
                HttpStatus.OK);
    }
}
