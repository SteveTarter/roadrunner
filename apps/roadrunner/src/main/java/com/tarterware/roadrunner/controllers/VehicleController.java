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
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;
import com.tarterware.roadrunner.utilities.TopologyUtilities;

/**
 * REST controller providing API endpoints for vehicle management and state
 * retrieval.
 *
 * <p>
 * The {@code VehicleController} handles external requests to create new
 * simulation instances, generate bulk vehicle scenarios, and fetch real-time
 * telemetry. It acts as a primary user of the
 * {@link ControllerVehicleStateStore} port to provide a read-optimized view of
 * the simulation state to the UI.
 * </p>
 *
 * @see VehicleManager
 * @see ControllerVehicleStateStore
 * @see VehicleState
 */
@RestController
@RequestMapping("/api/vehicle")
public class VehicleController
{
    private VehicleManager vehicleManager;

    private ControllerVehicleStateStore vehicleStateStore;

    private static final Logger log = LoggerFactory.getLogger(VehicleController.class);

    /**
     * Constructs the controller with the required management and state store
     * components.
     *
     * @param vehicleManager    the service responsible for vehicle lifecycle and
     *                          domain logic
     * @param vehicleStateStore the port providing access to the current persisted
     *                          vehicle states
     */
    VehicleController(VehicleManager vehicleManager, ControllerVehicleStateStore vehicleStateStore)
    {
        this.vehicleManager = vehicleManager;
        this.vehicleStateStore = vehicleStateStore;
        log.info("vehicleStateStore is {}", vehicleStateStore);

    }

    /**
     * Creates a single new vehicle based on the provided trip plan.
     *
     * @param tripPlan the plan containing stops and routing information
     * @return a {@link ResponseEntity} containing the initial {@link VehicleState}
     */
    @PostMapping("/create-new")
    ResponseEntity<VehicleState> getNewVehicle(@RequestBody
    TripPlan tripPlan)
    {
        Vehicle vehicle = vehicleManager.createVehicle(tripPlan);
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
     * Vehicles are spawned at the perimeter of a circle and assigned trip plans
     * that pass through the center to the opposite side.
     * </p>
     *
     * @param crissCrossPlan configuration for the scenario, including vehicle count
     *                       and radius
     * @return a list of {@link VehicleState} objects for the created vehicles
     */
    @PostMapping("/create-crisscross")
    ResponseEntity<List<VehicleState>> createCrissCrossVehicles(@RequestBody
    CrissCrossPlan crissCrossPlan)
    {
        List<VehicleState> listVehicleStates = new ArrayList<VehicleState>();

        // Create a Coordinate representing the center point.
        Coordinate centerCoordinate = new Coordinate(crissCrossPlan.getDegLongitude(), crissCrossPlan.getDegLatitude());

        // Determine the angular distance between the start points
        double degIncrement = 360.0 / crissCrossPlan.getVehicleCount();
        double degStartBearing = degIncrement / 2.0;
        for (int i = 0; i < crissCrossPlan.getVehicleCount(); ++i)
        {
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

            Vehicle vehicle = vehicleManager.createVehicle(tripPlan);
            VehicleState vehicleState = vehicle.getVehicleState();

            listVehicleStates.add(vehicleState);

            degStartBearing += degIncrement;
        }

        return new ResponseEntity<List<VehicleState>>(listVehicleStates, HttpStatus.OK);
    }

    /**
     * Retrieves the current state for a specific vehicle by its ID.
     *
     * @param vehicleId the UUID of the vehicle as a string
     * @return the {@link VehicleState} if found, otherwise
     *         {@code HttpStatus.NOT_FOUND}
     */
    @GetMapping("/get-vehicle-state/{vehicleId}")
    ResponseEntity<VehicleState> getVehicleStateFor(@PathVariable
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
     * @param vehicleId the UUID of the vehicle as a string
     * @return the {@link Directions} if found, otherwise
     *         {@code HttpStatus.NOT_FOUND}
     */
    @GetMapping("/get-vehicle-directions/{vehicleId}")
    ResponseEntity<Directions> getVehicleDirectionsFor(@PathVariable
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
     * @param page     the zero-based page index to retrieve
     * @param pageSize the number of records per page
     * @return a {@link PagedModel} containing the requested slice of vehicle states
     */
    @GetMapping("/get-all-vehicle-states")
    ResponseEntity<PagedModel<VehicleState>> getAllVehicleStates(@RequestParam(defaultValue = "0")
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
     * Resets the entire simulation server, clearing all vehicles and trip plans.
     *
     * @return an empty list and {@code HttpStatus.OK}
     */
    @GetMapping("/reset-server")
    ResponseEntity<List<VehicleState>> resetServer()
    {
        vehicleManager.reset();
        vehicleStateStore.reset();

        return new ResponseEntity<List<VehicleState>>(new ArrayList<VehicleState>(), HttpStatus.OK);
    }

    /**
     * Helper method to fetch a paginated map of vehicle states from the state
     * store.
     *
     * @param page     zero-based page index
     * @param pageSize number of records to return
     * @return a map of vehicle UUIDs to their current {@link VehicleState}
     * @throws IllegalArgumentException if the requested page is out of bounds
     */
    public Map<UUID, VehicleState> getVehicleMap(int page, int pageSize)
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
