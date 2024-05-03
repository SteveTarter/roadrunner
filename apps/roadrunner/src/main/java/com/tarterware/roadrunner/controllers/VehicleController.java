package com.tarterware.roadrunner.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tarterware.roadrunner.components.Vehicle;
import com.tarterware.roadrunner.components.VehicleManager;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.VehicleState;

@CrossOrigin(origins =
{ "http://localhost:3000", "https://roadrunner.info" })
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
		
		return vehicleState;
	}
}
