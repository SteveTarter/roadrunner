package com.tarterware.roadrunner.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tarterware.roadrunner.models.PositionRequest;
import com.tarterware.roadrunner.models.PositionResponse;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;

@RestController
@RequestMapping("/api/position")
public class PositionController
{
	@Autowired
	DirectionsService directionsService;
	
	@Autowired
	GeocodingService geocodingService;
	
	@PostMapping("/get-position")
	ResponseEntity<PositionResponse> getPosition(@RequestBody PositionRequest positionRequest)
	{
		PositionResponse positionResponse = new PositionResponse();
		
		// If there isn't a positionRequest, shut it down now!
		if(positionRequest == null)
		{
			positionResponse.setMessage("PositionRequest body is empty!");
			return new ResponseEntity<PositionResponse>(positionResponse, HttpStatus.BAD_REQUEST);
		}
		
		// Get the directions to follow the TripRequest.
		Directions directions = null;
		try
		{
			directions = directionsService.getDirectionsForTripPlan(positionRequest.getTripPlan());
		}
		catch(IllegalArgumentException ex)
		{
			positionResponse.setMessage(ex.getMessage());
			return new ResponseEntity<PositionResponse>(positionResponse, HttpStatus.BAD_REQUEST);
		}
		
		// See if the requested meters distance is reasonable
		double metersTravel = positionRequest.getMetersTravel();
		
		// Handle the special cases at the endpoints
		if(metersTravel == 0.0)
		{
			// Set the position to the start of the route.
			List<Double> startLocation = directions.getWaypoints().get(0).getLocation();
			positionResponse.setLatitude(startLocation.get(1));
			positionResponse.setLongitude(startLocation.get(0));
			
			positionResponse.setValid(true);
			return new ResponseEntity<PositionResponse>(positionResponse, HttpStatus.OK);
		}
		if(metersTravel == directions.getRoutes().get(0).getDistance())
		{
			// Set the position to the end of the route.
			positionResponse.setPositionLimited(true);
			int waypointCount = directions.getWaypoints().size();
			List<Double> endLocation = directions.getWaypoints().get(waypointCount - 1).getLocation();
			positionResponse.setLatitude(endLocation.get(1));
			positionResponse.setLongitude(endLocation.get(0));
			
			positionResponse.setValid(true);
			return new ResponseEntity<PositionResponse>(positionResponse, HttpStatus.OK);
		}
		
		// Now, check if the requested offset is within the route.
		if(metersTravel < 0.0)
		{
			positionResponse.setMessage("metersTravel must be a positive number!");
			
			// Set the position to the start of the route.
			positionResponse.setPositionLimited(true);
			List<Double> startLocation = directions.getWaypoints().get(0).getLocation();
			positionResponse.setLatitude(startLocation.get(1));
			positionResponse.setLongitude(startLocation.get(0));
			
			return new ResponseEntity<PositionResponse>(positionResponse, HttpStatus.BAD_REQUEST);
		}
		
		if(metersTravel > directions.getRoutes().get(0).getDistance())
		{
			positionResponse.setMessage(
					"metersTravel must be less than route length of " + 
					directions.getRoutes().get(0).getDistance() +
					" meters!");
			
			// Set the position to the end of the route.
			positionResponse.setPositionLimited(true);
			int waypointCount = directions.getWaypoints().size();
			List<Double> startLocation = directions.getWaypoints().get(waypointCount - 1).getLocation();
			positionResponse.setLatitude(startLocation.get(1));
			positionResponse.setLongitude(startLocation.get(0));
			
			return new ResponseEntity<PositionResponse>(positionResponse, HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<PositionResponse>(positionResponse, HttpStatus.OK);
	}
}
