package com.tarterware.roadrunner.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.locationtech.jts.geom.Coordinate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.tarterware.roadrunner.components.Vehicle;
import com.tarterware.roadrunner.components.VehicleManager;
import com.tarterware.roadrunner.models.Address;
import com.tarterware.roadrunner.models.CrissCrossPlan;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.utilities.TopologyUtilities;

@CrossOrigin(origins =
{ "http://localhost:3000", "https://roadrunner-view.tarterware.info", "https://roadrunner-view.tarterware.com" })
@RestController
@RequestMapping("/api/vehicle")
public class VehicleController
{
    @Autowired
    VehicleManager vehicleManager;

    @PostMapping("/create-new")
    ResponseEntity<VehicleState> getNewVehicle(@RequestBody TripPlan tripPlan)
    {
        UUID vehicleId = vehicleManager.createVehicle(tripPlan);
        VehicleState vehicleState = createVehicleStateFor(vehicleId);
        
        if(vehicleManager.isRunning() == false)
        {
            vehicleManager.startup();
        }
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
        for(int i = 0;  i < crissCrossPlan.getVehicleCount();  ++i)
        {
            double degEndBearing = degStartBearing + 180.0;
            if(degEndBearing > 360.0)
            {
                degEndBearing -= 360.0;
            }
            
            Coordinate startCoordinate = TopologyUtilities.getCoordinateAtBearingAndRange(centerCoordinate, crissCrossPlan.getKmRadius(), degStartBearing);
            Coordinate endCoordinate = TopologyUtilities.getCoordinateAtBearingAndRange(centerCoordinate, crissCrossPlan.getKmRadius(), degEndBearing);
            
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
            
            UUID vehicleId = vehicleManager.createVehicle(tripPlan);
            VehicleState vehicleState = createVehicleStateFor(vehicleId);
            
            listVehicleStates.add(vehicleState);
            
            degStartBearing += degIncrement;
        }
        
        if(vehicleManager.isRunning() == false)
        {
            vehicleManager.startup();
        }
        
        return new ResponseEntity<List<VehicleState>>(listVehicleStates, HttpStatus.OK);
    }
    
    @GetMapping("/get-vehicle-state/{vehicleId}")
    ResponseEntity<VehicleState> getVehicleStateFor(@PathVariable String vehicleId)
    {
        Vehicle vehicle = vehicleManager.getVehicle(UUID.fromString(vehicleId));
        if(vehicle == null)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        VehicleState vehicleState = createVehicleStateFor(vehicle.getId());
        
        return new ResponseEntity<VehicleState>(vehicleState, HttpStatus.OK);
    }
    
    @GetMapping("/get-vehicle-directions/{vehicleId}")
    ResponseEntity<Directions> getVehicleDirectionsFor(@PathVariable String vehicleId)
    {
        Vehicle vehicle = vehicleManager.getVehicle(UUID.fromString(vehicleId));
        if(vehicle == null)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        
        return new ResponseEntity<Directions>(vehicle.getDirections(), HttpStatus.OK);
    }
    
    @GetMapping("/get-all-vehicle-states")
    ResponseEntity<List<VehicleState>> getAllVehicleStates()
    {
        List<VehicleState> listVehicleStates = new ArrayList<VehicleState>();
        Map<UUID, Vehicle> vehicleMap = vehicleManager.getVehicleMap();
        
        for(Vehicle vehicle : vehicleMap.values())
        {
            VehicleState vehicleState = createVehicleStateFor(vehicle.getId());
            listVehicleStates.add(vehicleState);
        }
        
        return new ResponseEntity<List<VehicleState>>(listVehicleStates, HttpStatus.OK);
    }
    
    private VehicleState createVehicleStateFor(UUID vehicleId)
    {
        Vehicle vehicle = vehicleManager.getVehicle(vehicleId);
        
        if(vehicle == null)
        {
            throw new IllegalArgumentException("Unable to find vehicle with ID " + vehicleId);
        }
        
        VehicleState vehicleState = new VehicleState();
        vehicleState.setId(vehicle.getId());
        vehicleState.setDegLatitude(vehicle.getDegLatitude());
        vehicleState.setDegLongitude(vehicle.getDegLongitude());
        vehicleState.setMetersOffset(vehicle.getMetersOffset());
        vehicleState.setMetersPerSecond(vehicle.getMetersPerSecond());
        vehicleState.setMetersPerSecondDesired(vehicle.getMetersPerSecondDesired());
        vehicleState.setMssAcceleration(vehicle.getMssAcceleration());
        vehicleState.setPositionLimited(vehicle.isPositionLimited());
        vehicleState.setPositionValid(vehicle.isPositionValid());
        vehicleState.setDegBearing(vehicle.getDegBearing());
        vehicleState.setColorCode(vehicle.getColorCode());
        
        return vehicleState;
    }
    
    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        return ResponseEntity.ok().build();
    }
}
