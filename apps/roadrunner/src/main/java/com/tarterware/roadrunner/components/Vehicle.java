package com.tarterware.roadrunner.components;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Annotation;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.models.mapbox.RouteLeg;
import com.tarterware.roadrunner.models.mapbox.RouteStep;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.utilities.TopologyUtilities;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

//@Getter @Setter @RequiredArgsConstructor @ToString @EqualsAndHashCode

@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class Vehicle
{
	DirectionsService directionsService;
	
	@Getter
	UUID id;

	@Getter
	TripPlan tripPlan;
	
	@Getter
	double metersOffset;
	
	@Getter
	boolean positionLimited;
	
	@Getter
	boolean positionValid;
	
	@Getter
	double degLatitude;
	
	@Getter
	double degLongitude;
	
	@Getter
	double metersPerSecondDesired;
	
	@Getter
	double metersPerSecond;
	
	@Getter
	double mssAcceleration;
	
	Instant lastCalculationInstant;
	
	private Directions directions;
	private LineString utmLineString;
	private CoordinateTransform wgs84ToUtmCoordinatetransformer;
	private CoordinateTransform utmToWgs84Coordinatetransformer;
	private LengthIndexedLine lengthIndexedLine;
	private SpatialIndex spatialIndex;
	
	private static final Logger logger = LoggerFactory.getLogger(Vehicle.class);

	public Vehicle(DirectionsService directionsService)
	{
		super();
		
		id = UUID.randomUUID();
		this.directionsService = directionsService;
		this.mssAcceleration = 2.0;
	}
	
	public void setTripPlan(TripPlan tripPlan)
	{
		metersOffset = 0.0;

		if(tripPlan == null)
		{
			throw new IllegalArgumentException("TripPlan cannot be null!");
		}
		
		// Get the directions to follow the TripRequest.
		directions = directionsService.getDirectionsForTripPlan(tripPlan);
		
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

		this.tripPlan = tripPlan;

		lastCalculationInstant = Instant.now();
		
		// Finally, set the current location.
		setMetersOffset(0.0);
	}
	
	public void setMetersOffset(double metersOffset)
	{
		if(metersOffset == 0.0)
		{
			// Set the position to the start of the route.
			List<Double> startLocation = directions.getWaypoints().get(0).getLocation();
			positionLimited = false;
			positionValid = true;
			degLatitude = startLocation.get(1);
			degLongitude = startLocation.get(0);
			
			this.metersOffset = metersOffset;
			_determineDesiredSpeed();
			return;
		}
		if(metersOffset == directions.getRoutes().get(0).getDistance())
		{
			// Set the position to the end of the route.
			positionLimited = false;
			positionValid = true;
			int waypointCount = directions.getWaypoints().size();
			List<Double> endLocation = directions.getWaypoints().get(waypointCount - 1).getLocation();
			degLatitude = endLocation.get(1);
			degLongitude = endLocation.get(0);
			
			this.metersOffset = metersOffset;
			_determineDesiredSpeed();
			return;
		}
		
		// Now, check if the requested offset is within the route.
		if(metersOffset < 0.0)
		{
			// Set the position to the start of the route.
			List<Double> startLocation = directions.getWaypoints().get(0).getLocation();
			positionLimited = true;
			positionValid = false;
			degLatitude = startLocation.get(1);
			degLongitude = startLocation.get(0);
			
			this.metersOffset = metersOffset;
			_determineDesiredSpeed();
			return;
		}
		
		if(metersOffset > directions.getRoutes().get(0).getDistance())
		{
			// Set the position to the end of the route.
			int waypointCount = directions.getWaypoints().size();
			List<Double> endLocation = directions.getWaypoints().get(waypointCount - 1).getLocation();
			positionLimited = true;
			positionValid = false;
			degLatitude = endLocation.get(1);
			degLongitude = endLocation.get(0);
			
			this.metersOffset = metersOffset;
			_determineDesiredSpeed();
			return;
		}
		
		// Now find the geodetic coordinate to return.
		Coordinate utmPoint = lengthIndexedLine.extractPoint(metersOffset);
		ProjCoordinate projUtmPoint = TopologyUtilities.coordToProjCoord(utmPoint);
		ProjCoordinate projGeoPoint = new ProjCoordinate();
		utmToWgs84Coordinatetransformer.transform(projUtmPoint, projGeoPoint);
		
		positionLimited = false;
		positionValid = true;
		degLatitude = projGeoPoint.y;
		degLongitude = projGeoPoint.x;
		this.metersOffset = metersOffset;
		
		_determineDesiredSpeed();
	}
	
	private void _determineDesiredSpeed()
	{
		// Determine what metersPerSecondDesired should be.
		double totalDistance = 0.0;
		double speed = 0.0;
		boolean distanceReached = false;
		List<RouteLeg> listLegs = directions.getRoutes().get(0).getLegs();
		for(int legIndex = 0;  !distanceReached && (legIndex < listLegs.size());  ++legIndex)
		{
			Annotation annotation = listLegs.get(legIndex).getAnnotation();
			for(int a = 0;  !distanceReached && (a < annotation.getSpeed().size());  ++a)
			{
				speed = annotation.getSpeed().get(a);
				totalDistance += annotation.getDistance().get(a);
				if(totalDistance >= metersOffset)
				{
					distanceReached = true;
				}
			}
		}
		
		metersPerSecondDesired = speed;		
	}
	
	public void update()
	{
		Instant now = Instant.now();
		
		// Don't bother to calculate an updated position if at the end of the trip.
		if(positionLimited && (metersOffset > 0))
		{
			if(metersPerSecond > 0.0)
			{
				metersPerSecond = 0.0;
				logger.info("Vehicle " + id + " has arrived at its destination");
			}
		}
		else
		{
			// If the vehicle speed isn't at the desired speed yet, determine
			// if it needs to go faster or slower, and adjust speed accordingly.
			long msElapsed = now.toEpochMilli() - lastCalculationInstant.toEpochMilli();
			if(metersPerSecond != metersPerSecondDesired)
			{
				if(metersPerSecond < metersPerSecondDesired)
				{
					metersPerSecond += (msElapsed / 1000.0) * mssAcceleration;
					if(metersPerSecond > metersPerSecondDesired)
					{
						metersPerSecond = metersPerSecondDesired;
						logger.info("Accelerated to desired speed of " + metersPerSecond + " meters per second reached.");
					}
				}
				else
				{
					metersPerSecond -= (msElapsed / 1000.0) * mssAcceleration;
					if(metersPerSecond < metersPerSecondDesired)
					{
						metersPerSecond = metersPerSecondDesired;
						logger.info("Slowed to desired speed of " + metersPerSecond + " meters per second reached.");
					}
				}
			}
			
			// Determine how far the vehicle should have traveled in the elapsed time.
			double metersTraveled = (msElapsed / 1000.0) * metersPerSecond;
			setMetersOffset(metersOffset + metersTraveled);
		}
		
		lastCalculationInstant = now;
	}
}
