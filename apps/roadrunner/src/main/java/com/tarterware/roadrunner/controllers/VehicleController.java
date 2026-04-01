package com.tarterware.roadrunner.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
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
import com.tarterware.roadrunner.ports.VehicleStateStore;
import com.tarterware.roadrunner.utilities.TopologyUtilities;

@RestController
@RequestMapping("/api/vehicle")
public class VehicleController
{
    private VehicleManager vehicleManager;

    private VehicleStateStore vehicleStateStore;

    VehicleController(VehicleManager vehicleManager, VehicleStateStore vehicleStateStore)
    {
        this.vehicleManager = vehicleManager;
        this.vehicleStateStore = vehicleStateStore;
    }

    @PostMapping("/create-new")
    ResponseEntity<VehicleState> getNewVehicle(@RequestBody TripPlan tripPlan)
    {
        Vehicle vehicle = vehicleManager.createVehicle(tripPlan);
        VehicleState vehicleState = vehicle.getVehicleState();
        vehicleStateStore.saveVehicle(vehicleState);
        vehicleStateStore.addActiveVehicle(vehicle.getId());

        return new ResponseEntity<VehicleState>(vehicleState, HttpStatus.OK);
    }

    @PostMapping("/create-crisscross")
    ResponseEntity<List<VehicleState>> createCrissCrossVehicles(@RequestBody CrissCrossPlan crissCrossPlan)
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

    @GetMapping("/get-vehicle-state/{vehicleId}")
    ResponseEntity<VehicleState> getVehicleStateFor(@PathVariable String vehicleId)
    {
        VehicleState vehicleState = vehicleStateStore.getVehicle(UUID.fromString(vehicleId));
        if (vehicleState == null)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<VehicleState>(vehicleState, HttpStatus.OK);
    }

    @GetMapping("/get-vehicle-directions/{vehicleId}")
    ResponseEntity<Directions> getVehicleDirectionsFor(@PathVariable String vehicleId)
    {
        Directions directions = vehicleManager.getVehicleDirections(UUID.fromString(vehicleId), true);
        if (directions == null)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<Directions>(directions, HttpStatus.OK);
    }

    @GetMapping("/get-all-vehicle-states")
    ResponseEntity<PagedModel<VehicleState>> getAllVehicleStates(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize)
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

    @GetMapping("/reset-server")
    ResponseEntity<List<VehicleState>> resetServer()
    {
        vehicleManager.reset();

        return new ResponseEntity<List<VehicleState>>(new ArrayList<VehicleState>(), HttpStatus.OK);
    }

    /**
     * Gets a paginated map of Vehicles.
     *
     * @param page     page number to retrieve.
     * @param pageSize number of Vehicles in each page.
     * @return A Map of Vehicles by their UUIDs.
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
