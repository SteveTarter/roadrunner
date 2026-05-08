package com.tarterware.roadrunner.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tarterware.roadrunner.components.Vehicle;
import com.tarterware.roadrunner.components.VehicleManager;
import com.tarterware.roadrunner.models.Address;
import com.tarterware.roadrunner.models.CrissCrossPlan;
import com.tarterware.roadrunner.models.SimulationSession;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;
import com.tarterware.roadrunner.ports.SimulationRegistry;
import com.tarterware.roadrunner.security.UserPrincipal;
import com.tarterware.roadrunner.security.UserPrincipalFactory;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;
import com.tarterware.roadrunner.services.IdentityService;
import com.tarterware.roadrunner.services.VehicleUsageService;
import com.tarterware.roadrunner.utilities.TopologyUtilities;

/**
 * REST controller providing API endpoints for Roadrunner vehicle management,
 * simulation creation, and simulation-state retrieval.
 *
 * <p>
 * The {@code VehicleController} handles external requests to create individual
 * vehicles, generate bulk vehicle scenarios, retrieve real-time vehicle state,
 * retrieve route geometry, list simulation sessions, and reset the running
 * simulation. It acts as the HTTP boundary for the Roadrunner UI and delegates
 * vehicle lifecycle, state persistence, routing, geocoding, identity lookup,
 * and usage-limit enforcement to application services and ports.
 * </p>
 *
 * <p>
 * Vehicle creation endpoints require an authenticated JWT. The token is mapped
 * to a {@link UserPrincipal}, then checked by {@link VehicleUsageService}
 * before a vehicle is created. This allows the demo application to limit
 * non-privileged users while allowing configured superusers to bypass normal
 * daily limits.
 * </p>
 *
 * @see VehicleManager
 * @see ControllerVehicleStateStore
 * @see SimulationRegistry
 * @see VehicleUsageService
 * @see VehicleState
 */
@RestController
@RequestMapping("/api/vehicle")
public class VehicleController
{
    private final VehicleManager vehicleManager;

    private final ControllerVehicleStateStore vehicleStateStore;

    private final SimulationRegistry simulationRegistry;

    private final DirectionsService directionsService;

    private final GeocodingService geocodingService;

    private final IdentityService identityService;

    private final UserPrincipalFactory userPrincipalFactory;

    private final VehicleUsageService vehicleUsageService;

    private static final Logger log = LoggerFactory.getLogger(VehicleController.class);

    /**
     * Constructs a controller with the services and ports required to create,
     * manage, query, and reset vehicle simulations.
     *
     * @param vehicleManager       service responsible for vehicle lifecycle and
     *                             domain behavior
     * @param vehicleStateStore    port providing access to current persisted
     *                             vehicle states and active vehicle identifiers
     * @param simulationRegistry   port used to retrieve and reset recorded
     *                             simulation sessions
     * @param directionsService    service used to retrieve and reset cached route
     *                             directions
     * @param geocodingService     service used to reset cached geocoding data
     * @param identityService      service used to resolve authenticated Cognito
     *                             subjects to user-facing identity information
     * @param userPrincipalFactory factory that maps authenticated JWTs to
     *                             application-level user principals
     * @param vehicleUsageService  service that enforces per-user vehicle-start
     *                             limits
     */
    VehicleController(
            VehicleManager vehicleManager,
            ControllerVehicleStateStore vehicleStateStore,
            SimulationRegistry simulationRegistry,
            DirectionsService directionsService,
            GeocodingService geocodingService,
            IdentityService identityService,
            UserPrincipalFactory userPrincipalFactory,
            VehicleUsageService vehicleUsageService)
    {
        this.vehicleManager = vehicleManager;
        this.vehicleStateStore = vehicleStateStore;
        this.simulationRegistry = simulationRegistry;
        this.directionsService = directionsService;
        this.geocodingService = geocodingService;
        this.identityService = identityService;
        this.userPrincipalFactory = userPrincipalFactory;
        this.vehicleUsageService = vehicleUsageService;

        log.info("vehicleStateStore is {}", vehicleStateStore);
    }

    /**
     * Creates a single new vehicle based on the provided trip plan.
     *
     * <p>
     * The authenticated JWT is converted into a {@link UserPrincipal} and checked
     * against the configured usage limits before the vehicle is created. If the
     * user has exceeded the daily limit, {@link VehicleUsageService} throws an
     * exception and no vehicle is created.
     * </p>
     *
     * <p>
     * When creation succeeds, the vehicle's initial state is saved to the
     * controller state store and the vehicle is marked as active.
     * </p>
     *
     * @param tripPlan plan containing stops and routing information for the new
     *                 vehicle
     * @param jwt      authenticated JWT from the bearer token supplied in the
     *                 {@code Authorization} header
     * @return response containing the initial {@link VehicleState} for the newly
     *         created vehicle
     */
    @PostMapping("/create-new")
    ResponseEntity<VehicleState> getNewVehicle(
            @RequestBody
            TripPlan tripPlan,
            @AuthenticationPrincipal
            Jwt jwt)
    {
        // First, ensure user is allowed to start a vehicle.
        UserPrincipal user = userPrincipalFactory.fromJwt(jwt);
        vehicleUsageService.assertCanStartVehicles(user);

        // Exception is thrown if we're not allowed
        String userEmail = identityService.getEmailBySub(jwt.getSubject());
        Vehicle vehicle = vehicleManager.createVehicle(tripPlan, userEmail);
        VehicleState vehicleState = vehicle.getVehicleState();
        vehicleStateStore.saveVehicle(vehicleState);
        vehicleStateStore.addActiveVehicle(vehicle.getId());

        return new ResponseEntity<VehicleState>(vehicleState, HttpStatus.OK);
    }

    /**
     * Generates a "Criss-Cross" simulation scenario with multiple vehicles moving
     * across a central coordinate.
     *
     * <p>
     * Vehicles are spawned around the perimeter of a circle and assigned trip plans
     * that travel through the center point to the opposite side. Each vehicle
     * creation is checked against the authenticated user's usage limit. If the user
     * reaches the limit partway through the request, the service throws an
     * exception and the request stops.
     * </p>
     *
     * @param crissCrossPlan configuration for the scenario, including center
     *                       coordinate, vehicle count, and radius
     * @param jwt            authenticated JWT from the bearer token supplied in the
     *                       {@code Authorization} header
     * @return response containing the list of initial {@link VehicleState}
     *         instances for the created vehicles
     */
    @PostMapping("/create-crisscross")
    ResponseEntity<List<VehicleState>> createCrissCrossVehicles(
            @RequestBody
            CrissCrossPlan crissCrossPlan,
            @AuthenticationPrincipal
            Jwt jwt)
    {
        UserPrincipal user = userPrincipalFactory.fromJwt(jwt);
        String userEmail = identityService.getEmailBySub(jwt.getSubject());

        List<VehicleState> listVehicleStates = new ArrayList<VehicleState>();

        // Create a Coordinate representing the center point.
        Coordinate centerCoordinate = new Coordinate(crissCrossPlan.getDegLongitude(), crissCrossPlan.getDegLatitude());

        // Determine the angular distance between the start points
        double degIncrement = 360.0 / crissCrossPlan.getVehicleCount();
        double degStartBearing = degIncrement / 2.0;
        for (int i = 0; i < crissCrossPlan.getVehicleCount(); ++i)
        {
            // Ensure user can start a vehicle. An exception will be thrown
            // if the user is limited.
            vehicleUsageService.assertCanStartVehicles(user);
            double degEndBearing = degStartBearing + 180.0;
            if (degEndBearing > 360.0)
            {
                degEndBearing -= 360.0;
            }

            Coordinate startCoordinate = TopologyUtilities.getCoordinateAtBearingAndRange(centerCoordinate,
                    crissCrossPlan.getKmRadius(), degStartBearing);
            Coordinate endCoordinate = TopologyUtilities.getCoordinateAtBearingAndRange(centerCoordinate,
                    crissCrossPlan.getKmRadius(), degEndBearing);

            Address startAddress = new Address();
            startAddress.setSource("NumericEntry");
            startAddress.setLatitude(startCoordinate.getY());
            startAddress.setLongitude(startCoordinate.getX());

            Address endAddress = new Address();
            endAddress.setSource("NumericEntry");
            endAddress.setLatitude(endCoordinate.getY());
            endAddress.setLongitude(endCoordinate.getX());

            TripPlan tripPlan = new TripPlan();
            List<Address> listStops = new ArrayList<Address>();
            listStops.add(startAddress);
            listStops.add(endAddress);
            tripPlan.setListStops(listStops);

            Vehicle vehicle = vehicleManager.createVehicle(tripPlan, userEmail);
            VehicleState vehicleState = vehicle.getVehicleState();

            listVehicleStates.add(vehicleState);

            degStartBearing += degIncrement;
        }

        return new ResponseEntity<List<VehicleState>>(listVehicleStates, HttpStatus.OK);
    }

    /**
     * Retrieves the current state for a specific vehicle by its identifier.
     *
     * @param vehicleId UUID of the vehicle as a string
     * @return response containing the {@link VehicleState} when found, or
     *         {@code 404 Not Found} when no matching vehicle state exists
     */
    @GetMapping("/get-vehicle-state/{vehicleId}")
    ResponseEntity<VehicleState> getVehicleStateFor(
            @PathVariable
            String vehicleId)
    {
        VehicleState vehicleState = vehicleStateStore.getVehicle(UUID.fromString(vehicleId));
        if (vehicleState == null)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<VehicleState>(vehicleState, HttpStatus.OK);
    }

    /**
     * Retrieves the Mapbox directions and route geometry associated with a specific
     * vehicle.
     *
     * @param vehicleId UUID of the vehicle as a string
     * @return response containing the {@link Directions} when found, or
     *         {@code 404 Not Found} when no route data exists for the vehicle
     */
    @GetMapping("/get-vehicle-directions/{vehicleId}")
    ResponseEntity<Directions> getVehicleDirectionsFor(
            @PathVariable
            String vehicleId)
    {
        Directions directions = vehicleManager.getVehicleDirections(UUID.fromString(vehicleId), true);
        if (directions == null)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<Directions>(directions, HttpStatus.OK);
    }

    /**
     * Provides a paginated view of all active vehicle states in the simulation.
     *
     * <p>
     * The returned {@link PagedModel} contains only the requested slice of active
     * vehicles, along with pagination metadata derived from the current active
     * vehicle count.
     * </p>
     *
     * @param page     zero-based page index to retrieve
     * @param pageSize number of vehicle states to include in the page
     * @return response containing a paginated model of active vehicle states
     */
    @GetMapping("/get-all-vehicle-states")
    ResponseEntity<PagedModel<VehicleState>> getAllVehicleStates(
            @RequestParam(defaultValue = "0")
            int page,
            @RequestParam(defaultValue = "10")
            int pageSize)
    {
        // Get the vehicles for the current page
        Map<UUID, VehicleState> vehicleMap = getVehicleMap(page, pageSize);
        List<VehicleState> listVehicleStates = vehicleMap.values().stream()
                .collect(Collectors.toList());

        // Create a Page object
        Page<VehicleState> vehicleStatePage = new PageImpl<>(listVehicleStates, PageRequest.of(page, pageSize),
                vehicleStateStore.getActiveVehicleCount());

        // Create a PagedModel object
        PagedModel<VehicleState> pagedModel = PagedModel.of(vehicleStatePage.getContent(), new PagedModel.PageMetadata(
                vehicleStatePage.getSize(), vehicleStatePage.getNumber(), vehicleStatePage.getTotalElements()));

        return new ResponseEntity<>(pagedModel, HttpStatus.OK);
    }

    /**
     * Provides a paginated view of recorded simulation sessions.
     *
     * <p>
     * Simulation sessions are retrieved from the configured
     * {@link SimulationRegistry}. The method creates a page slice in memory and
     * wraps it in a {@link PagedModel} for use by the frontend table.
     * </p>
     *
     * @param page     zero-based page index to retrieve
     * @param pageSize number of simulation sessions to include in the page
     * @return response containing a paginated model of simulation sessions
     * @throws IllegalArgumentException if the requested page starts outside the
     *                                  available session list
     */
    @GetMapping("/simulation-sessions")
    ResponseEntity<PagedModel<SimulationSession>> getSimulationSessions(
            @RequestParam(defaultValue = "0")
            int page,
            @RequestParam(defaultValue = "10")
            int pageSize)
    {
        List<SimulationSession> allSessions = simulationRegistry.getAllSessions();

        List<SimulationSession> pageList = new ArrayList<SimulationSession>();
        int size = allSessions.size();

        if (size > 0)
        {
            int start = page * pageSize;
            if (start > size)
            {
                throw new IllegalArgumentException(
                        "Page " + page + " of page size " + pageSize + " outside " + size + " bounds!");
            }
            int end = start + pageSize;
            if (end > size)
            {
                end = size;
            }
            pageList = allSessions.subList(start, end);
        }

        // Create a Page object
        Page<SimulationSession> simSessionsPage = new PageImpl<>(
                pageList,
                PageRequest.of(page, pageSize),
                size);

        // Create a PagedModel object
        PagedModel<SimulationSession> pagedModel = PagedModel.of(simSessionsPage.getContent(),
                new PagedModel.PageMetadata(
                        simSessionsPage.getSize(), simSessionsPage.getNumber(), simSessionsPage.getTotalElements()));

        return new ResponseEntity<>(pagedModel, HttpStatus.OK);

    }

    /**
     * Resets the simulation server and clears active runtime state.
     *
     * <p>
     * This endpoint resets the vehicle manager, vehicle state store, cached
     * directions, cached geocoding data, and simulation registry. It is intended
     * for demo or administrative use.
     * </p>
     *
     * @return response containing an empty list and {@code 200 OK}
     */
    @GetMapping("/reset-server")
    ResponseEntity<List<VehicleState>> resetServer()
    {
        vehicleManager.reset();
        vehicleStateStore.reset();
        directionsService.reset();
        geocodingService.reset();
        simulationRegistry.reset();

        return new ResponseEntity<List<VehicleState>>(new ArrayList<VehicleState>(), HttpStatus.OK);
    }

    /**
     * Fetches a paginated map of active vehicle states from the state store.
     *
     * <p>
     * The method reads the current active vehicle identifiers, selects the
     * requested slice, and retrieves the corresponding vehicle states in a single
     * state-store call.
     * </p>
     *
     * @param page     zero-based page index
     * @param pageSize number of records to return
     * @return map of vehicle UUIDs to their current {@link VehicleState}
     * @throws IllegalArgumentException if the requested page starts outside the
     *                                  active vehicle list
     */
    public Map<UUID, VehicleState> getVehicleMap(
            int page,
            int pageSize)
    {
        // Create a Map of Vehicles to return
        Map<UUID, VehicleState> vMap = new HashMap<>();

        int size = (int) this.vehicleStateStore.getActiveVehicleCount();
        if (size == 0)
        {
            return vMap;
        }

        int index = page * pageSize;
        if (index > size)
        {
            throw new IllegalArgumentException(
                    "Page " + page + " of page size " + pageSize + " outside " + size + " bounds!");
        }

        // Now, loop through the cursor, filling up to "pageSize" vehicleId elements.
        int recordsToFill = pageSize;
        List<UUID> idList = new ArrayList<>();
        List<UUID> activeIdsList = this.vehicleStateStore.getActiveVehicleIds().stream().toList();
        while ((recordsToFill > 0) && (index < size))
        {
            UUID vehicleId = activeIdsList.get(index++);
            idList.add(vehicleId);
            recordsToFill--;
        }

        return this.vehicleStateStore.getVehicles(idList);
    }

}
