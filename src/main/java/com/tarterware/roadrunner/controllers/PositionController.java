package com.tarterware.roadrunner.controllers;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.linearref.LocationIndexedLine;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tarterware.roadrunner.models.PositionRequest;
import com.tarterware.roadrunner.models.PositionResponse;
import com.tarterware.roadrunner.models.mapbox.Annotation;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.models.mapbox.RouteLeg;
import com.tarterware.roadrunner.models.mapbox.RouteStep;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;
import com.tarterware.roadrunner.utilities.TopologyUtilities;

@RestController
@RequestMapping("/api/position")
public class PositionController
{
	@Autowired
	DirectionsService directionsService;
	
	@Autowired
	GeocodingService geocodingService;
	
	private LineString utmLineString;
	private CoordinateTransform wgs84ToUtmCoordinatetransformer;
	private CoordinateTransform utmToWgs84Coordinatetransformer;
	private LengthIndexedLine lengthIndexedLine;
	private SpatialIndex spatialIndex;

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
		
		// The position lies on the route, so create the the JTS version.
		
		// Create the appropriate coordinate transformers.
		GeometryFactory geometryFactory = new GeometryFactory();
		List<Double> startLocation = directions.getWaypoints().get(0).getLocation();
		ProjCoordinate geodeticCoordinate = new ProjCoordinate(startLocation.get(0), startLocation.get(1), 0.0);
		Coordinate coord = TopologyUtilities.projCoordToCoord(geodeticCoordinate);
		wgs84ToUtmCoordinatetransformer = TopologyUtilities.getWgs84ToUtmCoordinateTransformer(coord);
		utmToWgs84Coordinatetransformer = TopologyUtilities.getUtmToWgs84CoordinateTransformer(coord);
		
		List<Coordinate> utmCoordList = new ArrayList<Coordinate>();

		// Now, loop through the legs of the route and create the JTS versions
		List<RouteLeg> listLegs = directions.getRoutes().get(0).getLegs();
		for(RouteLeg leg : listLegs)
		{
			// Create a version of the leg in UTM coordinates
			for(RouteStep step : leg.getSteps())
			{
				ProjCoordinate projUtmCoord = new ProjCoordinate();
				for(List<Double> coordinate : step.getGeometry().getCoordinates())
				{
					geodeticCoordinate = new ProjCoordinate(coordinate.get(0), coordinate.get(1), 0.0);
					wgs84ToUtmCoordinatetransformer.transform(geodeticCoordinate, projUtmCoord);
					utmCoordList.add(TopologyUtilities.projCoordToCoord(projUtmCoord));
				}
			}
		}
		utmLineString = geometryFactory.createLineString(utmCoordList.toArray(new Coordinate[0]));
		
		// Create a spatial index, then stuff the envelope of the UTM line string in it.
		spatialIndex = new STRtree();
		Envelope envelope = utmLineString.getEnvelopeInternal();
		spatialIndex.insert(envelope, new LocationIndexedLine(utmLineString));
		
		// Create a length indexed string, and determine what the milepost scale factor should be.
		lengthIndexedLine = new LengthIndexedLine(utmLineString);
		
		// Now that we've created the length indexed line, find the geodetic coordinate to return.
		Coordinate utmPoint = lengthIndexedLine.extractPoint(metersTravel);
		ProjCoordinate projUtmPoint = TopologyUtilities.coordToProjCoord(utmPoint);
		ProjCoordinate projGeoPoint = new ProjCoordinate();
		utmToWgs84Coordinatetransformer.transform(projUtmPoint, projGeoPoint);
		
		positionResponse.setValid(true);
		positionResponse.setLatitude(projGeoPoint.y);
		positionResponse.setLongitude(projGeoPoint.x);

		// Determine what the speed should be.
		double totalDistance = 0.0;
		double metersPerSecond = 0.0;
		boolean distanceReached = false;
		for(int legIndex = 0;  !distanceReached && (legIndex < listLegs.size());  ++legIndex)
		{
			Annotation annotation = listLegs.get(legIndex).getAnnotation();
			for(int a = 0;  !distanceReached && (a < annotation.getSpeed().size());  ++a)
			{
				metersPerSecond = annotation.getSpeed().get(a);
				totalDistance += annotation.getDistance().get(a);
				if(totalDistance >= metersTravel)
				{
					distanceReached = true;
				}
			}
		}
		positionResponse.setMetersPerSecond(metersPerSecond);
		
		return new ResponseEntity<PositionResponse>(positionResponse, HttpStatus.OK);
	}
}
