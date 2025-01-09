package com.tarterware.roadrunner.components;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LengthIndexedLine;
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a vehicle that navigates a predefined route, tracking its position, speed,
 * and bearing in real-time. The class supports geospatial transformations using UTM
 * and WGS84 coordinates and handles dynamic route adjustments and updates.
 * 
 * <p>The {@code Vehicle} class provides functionality to:
 * <ul>
 *   <li>Set a trip plan and initialize route geometry.</li>
 *   <li>Update the vehicle's position, speed, and bearing based on elapsed time.</li>
 *   <li>Handle transitions between UTM zones dynamically during route traversal.</li>
 *   <li>Log and manage the vehicle's movement along the route.</li>
 * </ul>
 * </p>
 * 
 * <p>Features include:
 * <ul>
 *   <li>Geospatial transformations between UTM and WGS84 coordinates.</li>
 *   <li>Dynamic bearing adjustments based on route geometry.</li>
 *   <li>Acceleration, deceleration, and turning constraints for realistic movement simulation.</li>
 *   <li>Management of route segment data for efficient spatial calculations.</li>
 * </ul>
 * </p>
 * 
 * <p>Example usage:
 * <pre>
 *     DirectionsService directionsService = new DirectionsService();
 *     Vehicle vehicle = new Vehicle(directionsService);
 *     
 *     TripPlan tripPlan = new TripPlan(...); // Define a trip plan
 *     vehicle.setTripPlan(tripPlan);         // Set the trip plan for the vehicle
 *     
 *     vehicle.update();                      // Update the vehicle's state
 * </pre>
 * </p>
 * 
 * @see com.tarterware.roadrunner.models.TripPlan
 * @see com.tarterware.roadrunner.services.DirectionsService
 * @see com.tarterware.roadrunner.utilities.TopologyUtilities
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class Vehicle
{
	/**
	 * Represents data associated with a line segment in a route, including its UTM transformations
	 * and offset information. This class encapsulates information necessary to manage and
	 * transform coordinates for a specific segment of a route.
	 * 
	 * <p>Each instance of this class corresponds to a line segment within a UTM zone. It includes:
	 * <ul>
	 *   <li>The cumulative offset in meters from the start of the route.</li>
	 *   <li>A {@link LengthIndexedLine} representing the geometry of the segment.</li>
	 *   <li>Coordinate transformation utilities for converting between geodetic (WGS84) and UTM coordinates.</li>
	 * </ul>
	 * </p>
	 * 
	 * <p>This data structure is used to manage the routing logic and spatial transformations
	 * required to track a vehicle's position along a route.</p>
	 * 
	 * @see LengthIndexedLine
	 * @see CoordinateTransform
	 */
    @Data
    class LineSegmentData
    {
        /**
         * The cumulative offset, in meters, from the start of the route to the start of this line segment.
         */
        private double metersOffset;

        /**
         * The line geometry of this segment, indexed by length for efficient spatial queries.
         */
        private LengthIndexedLine lengthIndexedLine;

        /**
         * The coordinate transformer for converting from geodetic (WGS84) coordinates to UTM coordinates.
         */
        private CoordinateTransform wgs84ToUtmCoordinatetransformer;

        /**
         * The coordinate transformer for converting from UTM coordinates to geodetic (WGS84) coordinates.
         */
        private CoordinateTransform utmToWgs84Coordinatetransformer;
    }

    DirectionsService directionsService;
    
 // Unique identifier for the vehicle instance.
    @Getter
    UUID id;

    // Represents the trip plan that this vehicle follows.
    @Getter
    TripPlan tripPlan;

    // Current offset along the route in meters.
    @Getter
    double metersOffset;

    // Indicates if the vehicle's position is constrained to the route's boundaries.
    @Getter
    boolean positionLimited;

    // Indicates if the vehicle's position is valid (e.g., within the defined route).
    @Getter
    boolean positionValid;

    // Current latitude of the vehicle in degrees.
    @Getter
    double degLatitude;

    // Current longitude of the vehicle in degrees.
    @Getter
    double degLongitude;

    // The desired speed of the vehicle in meters per second.
    @Getter
    double metersPerSecondDesired;

    // The current speed of the vehicle in meters per second.
    @Getter
    double metersPerSecond;

    // The maximum acceleration of the vehicle in meters per second squared.
    @Getter
    double mssAcceleration;

    // The current bearing (heading) of the vehicle in degrees (0-360).
    @Getter
    double degBearing;

    // The desired bearing (heading) of the vehicle in degrees (0-360).
    @Getter
    double degBearingDesired;

    // The maximum turning rate of the vehicle in degrees per second.
    @Getter
    double degsPerSecondTurn;

    // The color code (hex) representing the vehicle's visual appearance.
    @Getter
    String colorCode;

    // Directions object containing route details, waypoints, and legs for the trip.
    @Getter
    private Directions directions;

    // Timestamp of the last position update calculation.
    Instant lastCalculationInstant;

    // Last geodetic point in projected coordinates, used for bearing calculations.
    private ProjCoordinate lastProjGeoPoint;

    // A list of data about each line segment in the route, including geometry and UTM transformers.
    private List<LineSegmentData> listLineSegmentData = new ArrayList<>();

    // Logger instance for logging vehicle activity and debug information.
    private static final Logger logger = LoggerFactory.getLogger(Vehicle.class);
    
    /**
     * Constructs a new {@code Vehicle} instance with default settings and associates it 
     * with the specified {@link DirectionsService}.
     * 
     * <p>The constructor initializes the following:
     * <ul>
     *   <li>A unique identifier for the vehicle.</li>
     *   <li>Default acceleration and turning rates.</li>
     *   <li>A random pastel color for visual representation.</li>
     *   <li>Associates the vehicle with the provided {@link DirectionsService} 
     *       for fetching route directions.</li>
     * </ul>
     * </p>
     * 
     * @param directionsService The {@link DirectionsService} instance used to retrieve
     *                          directions for the vehicle's routes. Must not be {@code null}.
     * 
     * @throws IllegalArgumentException if {@code directionsService} is {@code null}.
     * 
     * <p>Example usage:
     * <pre>
     *     DirectionsService directionsService = new DirectionsService();
     *     Vehicle vehicle = new Vehicle(directionsService);
     * </pre>
     */
    public Vehicle(DirectionsService directionsService)
    {
        super();
        
        // Initialize the vehicle with a unique identifier.
        this.id = UUID.randomUUID();
        
        // Service used to fetch directions for trip plans.
        this.directionsService = directionsService;
        
        // Default acceleration (m/s^2) for the vehicle.
        this.mssAcceleration = 2.0;
        
        // Default turning rate (degrees per second).
        this.degsPerSecondTurn = 120.0;
        
     // Generate a random pastel color for the vehicle's visual representation.
        Random random = new Random();
        final float hue = random.nextFloat();
        final float saturation = 0.9f; // Saturation: 1.0 for brilliant, 0.0 for dull.
        final float luminance = 1.0f;  // Luminance: 1.0 for bright, 0.0 for dark.
        int hexColor = Color.getHSBColor(hue, saturation, luminance).getRGB();
        this.colorCode = String.format("#%06X", hexColor & 0xFFFFFF);
    }
    
    /**
     * Sets the trip plan for the vehicle and initializes the route geometry, segment data,
     * and coordinate transformations for the specified trip.
     * 
     * <p>This method processes the trip plan by:
     * <ul>
     *   <li>Retrieving directions based on the trip plan.</li>
     *   <li>Creating a list of {@link LineSegmentData} objects to manage route segments.</li>
     *   <li>Handling UTM zone transitions and ensuring proper coordinate transformations.</li>
     *   <li>Initializing the vehicle's position at the start of the route.</li>
     * </ul>
     * </p>
     * 
     * @param tripPlan The {@link TripPlan} object representing the desired route.
     *                 Must not be {@code null}.
     * @throws IllegalArgumentException if the {@code tripPlan} is {@code null}.
     * 
     * <p>Example usage:
     * <pre>
     *     TripPlan plan = new TripPlan(...);  // Define a trip plan
     *     vehicle.setTripPlan(plan);         // Assign the trip plan to the vehicle
     * </pre>
     */
    public void setTripPlan(TripPlan tripPlan)
    {
    	LengthIndexedLine lengthIndexedLine;
    	
        // Check for a valid trip plan.
        if(tripPlan == null)
        {
            throw new IllegalArgumentException("TripPlan cannot be null!");
        }
        
        metersOffset = 0.0;
        LineSegmentData lineSegmentData = new LineSegmentData();
        lineSegmentData.setMetersOffset(metersOffset);
        
        // Get the directions to follow the TripRequest.
        directions = directionsService.getDirectionsForTripPlan(tripPlan);
        
        // Create the appropriate coordinate transformers.
        GeometryFactory geometryFactory = new GeometryFactory();
        List<Double> startLocation = directions.getWaypoints().get(0).getLocation();
        ProjCoordinate geodeticCoordinate = new ProjCoordinate(startLocation.get(0), startLocation.get(1), 0.0);
        Coordinate coord = TopologyUtilities.projCoordToCoord(geodeticCoordinate);
        CoordinateTransform wgs84ToUtmCoordinatetransformer = TopologyUtilities.getWgs84ToUtmCoordinateTransformer(coord);
        CoordinateTransform utmToWgs84Coordinatetransformer = TopologyUtilities.getUtmToWgs84CoordinateTransformer(coord);
        lineSegmentData.setWgs84ToUtmCoordinatetransformer(wgs84ToUtmCoordinatetransformer);
        lineSegmentData.setUtmToWgs84Coordinatetransformer(utmToWgs84Coordinatetransformer);
        double lastLongitude = startLocation.get(0);
        List<Coordinate> utmCoordList = new ArrayList<Coordinate>();

        // Iterate over route legs and steps to generate geometry and update line segment data.
        List<RouteLeg> listLegs = directions.getRoutes().get(0).getLegs();
        for(RouteLeg leg : listLegs)
        {
            // Create a version of the leg in UTM coordinates
            for(RouteStep step : leg.getSteps())
            {
            	// Test to see if the path has moved into another UTM region.
            	// If so, new transformers need to be created.
            	double newLongitude = step.getGeometry().getCoordinates().get(0).get(0);
            	if(TopologyUtilities.isNewTransformerNeeded(lastLongitude, newLongitude))
            	{
            		lastLongitude = newLongitude;
            		
                    // Create a length indexed string for this segment and store it in the list
            		LineString utmLineString = geometryFactory.createLineString(utmCoordList.toArray(new Coordinate[0]));
                    lengthIndexedLine = new LengthIndexedLine(utmLineString);
                    lineSegmentData.setLengthIndexedLine(lengthIndexedLine);
                    listLineSegmentData.add(lineSegmentData);
                    
                    // Add the length of that line to the total offset
                    metersOffset += lengthIndexedLine.getEndIndex();

                    // Create a new LineSegmentData to record this UTM zone's path.
                    lineSegmentData = new LineSegmentData();
                    lineSegmentData.setMetersOffset(metersOffset);
                    utmCoordList = new ArrayList<Coordinate>();

                    // Create new transformers appropriate for this UTM zone.
                    startLocation = step.getGeometry().getCoordinates().get(0);
                    geodeticCoordinate = new ProjCoordinate(startLocation.get(0), startLocation.get(1), 0.0);
                    coord = TopologyUtilities.projCoordToCoord(geodeticCoordinate);
                    wgs84ToUtmCoordinatetransformer = TopologyUtilities.getWgs84ToUtmCoordinateTransformer(coord);
                    utmToWgs84Coordinatetransformer = TopologyUtilities.getUtmToWgs84CoordinateTransformer(coord);
                    lineSegmentData.setWgs84ToUtmCoordinatetransformer(wgs84ToUtmCoordinatetransformer);
                    lineSegmentData.setUtmToWgs84Coordinatetransformer(utmToWgs84Coordinatetransformer);
            	}
            	
                ProjCoordinate projUtmCoord = new ProjCoordinate();
                for(List<Double> coordinate : step.getGeometry().getCoordinates())
                {
                	geodeticCoordinate = new ProjCoordinate(coordinate.get(0), coordinate.get(1), 0.0);
                    wgs84ToUtmCoordinatetransformer.transform(geodeticCoordinate, projUtmCoord);
                    utmCoordList.add(TopologyUtilities.projCoordToCoord(projUtmCoord));
                }
            }
        }
        
        // Finalize the last line segment's data at the end of the trip.
        LineString utmLineString = geometryFactory.createLineString(utmCoordList.toArray(new Coordinate[0]));
        lengthIndexedLine = new LengthIndexedLine(utmLineString);
        lineSegmentData.setLengthIndexedLine(lengthIndexedLine);
        listLineSegmentData.add(lineSegmentData);

        this.tripPlan = tripPlan;

        lastCalculationInstant = Instant.now();
        
        // Finally, set the current location.
        setMetersOffset(0.0);
    }
    
    /**
     * Updates the vehicle's position along the route based on the specified offset in meters.
     * 
     * <p>This method adjusts the vehicle's geographic location, position validity, and
     * bearing based on the given offset. It handles various cases, such as:
     * <ul>
     *   <li>Setting the position to the start of the route if the offset is zero.</li>
     *   <li>Setting the position to the end of the route if the offset matches the total route distance.</li>
     *   <li>Handling out-of-bounds offsets by limiting the position to the nearest valid boundary.</li>
     *   <li>Determining the current position within the route's segments for valid offsets.</li>
     * </ul>
     * </p>
     * 
     * <p>The method also calculates the desired speed and updates the vehicle's desired bearing
     * based on the new position.</p>
     * 
     * @param metersOffset The distance offset along the route in meters. A value of 0 sets the
     *                     position to the start of the route, and a value equal to the route's
     *                     total distance sets the position to the end.
     * 
     * <p>Example usage:
     * <pre>
     *     vehicle.setMetersOffset(500.0); // Move the vehicle to 500 meters along the route.
     * </pre>
     */
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
        
        if(metersOffset >= directions.getRoutes().get(0).getDistance())
        {
            // Set the position to the end of the route.
            int waypointCount = directions.getWaypoints().size();
            List<Double> endLocation = directions.getWaypoints().get(waypointCount - 1).getLocation();
            positionLimited = true;
            positionValid = false;
            degLatitude = endLocation.get(1);
            degLongitude = endLocation.get(0);
            
            this.metersOffset = directions.getRoutes().get(0).getDistance();
            _determineDesiredSpeed();
            return;
        }
        
        // First, determine the appropriate LineSegmentData to use.
        LineSegmentData activeLineSegmentData = null;
        double localMetersOffset = metersOffset;
        boolean foundLineSegment = false;
        for(int i = 0 ;  !foundLineSegment && (i < listLineSegmentData.size());  ++i)
        {
        	LineSegmentData lineSegmentData = listLineSegmentData.get(i);
        	if(metersOffset >= lineSegmentData.getMetersOffset())
        	{
        		activeLineSegmentData = lineSegmentData;
        		localMetersOffset = metersOffset - lineSegmentData.getMetersOffset();
        	}
        	else
        	{
        		foundLineSegment = true;
        	}
        }
        
        // Now find the geodetic coordinate to return.
        Coordinate utmPoint = activeLineSegmentData.getLengthIndexedLine().extractPoint(localMetersOffset);
        ProjCoordinate projUtmPoint = TopologyUtilities.coordToProjCoord(utmPoint);
        ProjCoordinate projGeoPoint = new ProjCoordinate();
        activeLineSegmentData.getUtmToWgs84Coordinatetransformer().transform(projUtmPoint, projGeoPoint);
        
        if((lastProjGeoPoint != null) && 
           !((projGeoPoint.x == lastProjGeoPoint.x) && (projGeoPoint.y == lastProjGeoPoint.y)))
        {
            degBearingDesired = _getBearingBetween(lastProjGeoPoint, projGeoPoint);
        }
        lastProjGeoPoint = projGeoPoint;
        
        positionLimited = false;
        positionValid = true;
        degLatitude = projGeoPoint.y;
        degLongitude = projGeoPoint.x;
        this.metersOffset = metersOffset;
        
        _determineDesiredSpeed();
    }
    
    /**
     * Updates the vehicle's state based on the elapsed time since the last calculation.
     * 
     * <p>This method performs the following operations:
     * <ul>
     *   <li>Adjusts the vehicle's speed to approach the desired speed, respecting the
     *       acceleration or deceleration limits.</li>
     *   <li>Calculates the distance the vehicle has traveled during the elapsed time
     *       and updates its position along the route.</li>
     *   <li>Adjusts the vehicle's bearing (heading) toward the desired bearing, respecting
     *       the maximum turning rate.</li>
     *   <li>Logs changes in the vehicle's speed and state (e.g., reaching the destination).</li>
     * </ul>
     * </p>
     * 
     * <p>The method ensures the vehicle's state remains consistent with the route, handling
     * cases such as reaching the end of the route or being limited by route boundaries.</p>
     * 
     * <p>If the vehicle is at the end of the route, its speed is set to zero, and further updates
     * will not move the vehicle.</p>
     * 
     * <p>Example usage:
     * <pre>
     *     vehicle.update(); // Updates the vehicle's position and state.
     * </pre>
     * 
     * @see #setMetersOffset(double)
     * @see #shortestAngleDifference(double, double)
     */
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
                        logger.info("Vehicle " + id + " accelerated to " + metersPerSecond + " meters per second.");
                    }
                }
                else
                {
                    metersPerSecond -= (msElapsed / 1000.0) * mssAcceleration;
                    if(metersPerSecond < metersPerSecondDesired)
                    {
                        metersPerSecond = metersPerSecondDesired;
                        logger.info("Vehicle " + id + " slowed to " + metersPerSecond + " meters per second.");
                    }
                }
            }
            
            // Determine how far the vehicle should have traveled in the elapsed time.
            double metersTraveled = (msElapsed / 1000.0) * metersPerSecond;
            setMetersOffset(metersOffset + metersTraveled);
            
            // If the vehicle bearing isn't at the desired bearing, adjust
            // the bearing with a rate limiter.
            degBearing = normalize(degBearing);
            degBearingDesired = normalize(degBearingDesired);
            
            // Calculate the shortest angle difference [-180, 180]
            double degAngleDiff = shortestAngleDifference(degBearing, degBearingDesired);

            if(degAngleDiff != 0.0)
            {
            	// Calculate the maximum turn allowed
            	double degMaxTurn = degsPerSecondTurn * (msElapsed / 1000.0);
                degBearing += Math.signum(degAngleDiff) * Math.min(Math.abs(degAngleDiff), degMaxTurn);
                degBearing = normalize(degBearing);
            }
        }
        
        lastCalculationInstant = now;
    }
    
    private double _getBearingBetween(ProjCoordinate projGeoPoint1, ProjCoordinate projGeoPoint2)
    {
        double longitude1 = projGeoPoint1.x;
        double longitude2 = projGeoPoint2.x;
        double latitude1 = Math.toRadians(projGeoPoint1.y);
        double latitude2 = Math.toRadians(projGeoPoint2.y);
        double longDiff = Math.toRadians(longitude2 - longitude1);
        double y = Math.sin(longDiff) * Math.cos(latitude2);
        double x = Math.cos(latitude1) * Math.sin(latitude2) - Math.sin(latitude1) * Math.cos(latitude2) * Math.cos(longDiff);

        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
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
    
    // Normalize an angle to the range [0, 360)
    private static double normalize(double angle)
    {
        angle = angle % 360;
        if (angle < 0)
        {
            angle += 360;
        }
        return angle;
    }

    // Compute the shortest angle difference in the range [-180, 180]
    private static double shortestAngleDifference(double current, double target) 
    {
        double diff = (target - current + 360) % 360; // Difference between bearings, normalized
        if (diff > 180) diff -= 360; // Adjust to range [-180, 180]
        return diff;
    }
}
