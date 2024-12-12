package com.tarterware.roadrunner.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        Directions directions = null;
        try
        {
            directions = directionsService.getDirectionsForTripPlan(tripPlan);
        }
        catch(IllegalArgumentException ex)
        {
            return new ResponseEntity<Directions>(HttpStatus.BAD_REQUEST);
        }
        
        return new ResponseEntity<Directions>(directions, HttpStatus.OK);
    }
}
