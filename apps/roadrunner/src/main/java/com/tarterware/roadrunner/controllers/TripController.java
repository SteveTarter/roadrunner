package com.tarterware.roadrunner.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tarterware.roadrunner.models.Address;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;

@RestController
@RequestMapping("/api/trips")
public class TripController
{
	@Autowired
	DirectionsService directionsService;
	
	@Autowired
	GeocodingService geocodingService;
	
	@PostMapping("/get-directions")
	ResponseEntity<Directions> getDirections(@RequestBody TripPlan tripPlan)
	{
		// Check to see a valid TripPlan has been provided before proceeding.
		if(tripPlan == null)
		{
			return new ResponseEntity<Directions>( HttpStatus.BAD_REQUEST);
		}
		
		if((tripPlan.getListStops() == null) || (tripPlan.getListStops().size() < 2))
		{
			return new ResponseEntity<Directions>( HttpStatus.BAD_REQUEST);
		}
		
		// Get the geodetic position of the given Address.
		for(Address address : tripPlan.getListStops()) 
		{
			geocodingService.setPositionFromAddress(address);
		}
		
		// Pass the list of addresses to obtain Directions to travel between them
		Directions directions = directionsService.getDirections(tripPlan.getListStops());
		
		return new ResponseEntity<Directions>(directions, HttpStatus.OK);
	}
}
