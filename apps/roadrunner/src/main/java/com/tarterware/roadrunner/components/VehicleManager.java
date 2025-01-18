package com.tarterware.roadrunner.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.models.mapbox.RouteLeg;
import com.tarterware.roadrunner.models.mapbox.RouteStep;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.utilities.TopologyUtilities;

/**
 * Manages a collection of Vehicles, their routes, and updates their positions
 * periodically. Vehicles are created based on trip plans and can have their
 * states updated periodically.
 * 
 * <p>
 * The {@code VehicleManager} class provides functionality to:
 * <ul>
 * <li>Process a trip plan to initialize route geometry.</li>
 * <li>Manage Vehicle state that is not serializable, such as LineString.
 * <li>Handle transitions between UTM zones dynamically during route
 * traversal.</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Features include:
 * <ul>
 * <li>Geospatial transformations between UTM and WGS84 coordinates.</li>
 * <li>Management of route segment data for efficient spatial calculations.</li>
 * </ul>
 */
@Component
public class VehicleManager
{
    // Template to use redis.
    private RedisTemplate<String, Object> redisTemplate;

    // Update period in milliseconds.
    private long msPeriod = 100;

    // Timer to schedule periodic updates.
    private Timer timer;

    // Set of Vehicle IDs.
    private Set<UUID> vehicleIdSet = Collections.synchronizedSet(new HashSet<UUID>());

    // Map of Vehicles by ID.
    private Map<UUID, Vehicle> vehicleMap = Collections.synchronizedMap(new HashMap<UUID, Vehicle>());

    // Map of Directions by Vehicle ID.
    private Map<UUID, Directions> directionsMap = Collections.synchronizedMap(new HashMap<UUID, Directions>());

    // LineSegmentData by Vehicle ID.
    private Map<UUID, List<LineSegmentData>> lineSegmentDataMap = Collections
            .synchronizedMap(new HashMap<UUID, List<LineSegmentData>>());

    // Service to retrieve directions for trip plans
    private DirectionsService directionsService;

    private static final Logger logger = LoggerFactory.getLogger(VehicleManager.class);

    /**
     * Constructor to initialize the VehicleManager with a DirectionsService.
     *
     * @param directionsService The service to retrieve directions for trip plans.
     * @param redisTemplate     RedisTemplate for performing Redis operations, such
     *                          as storing and retrieving data. Keys are Strings,
     *                          and values are serialized Java objects..
     */
    public VehicleManager(DirectionsService directionsService, RedisTemplate<String, Object> redisTemplate)
    {
        super();

        this.directionsService = directionsService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Gets the map of all Vehicles.
     *
     * @return A synchronized map of Vehicles by their UUIDs.
     */
    public Map<UUID, Vehicle> getVehicleMap()
    {
        // Create a Map of Vehicles to return
        Map<UUID, Vehicle> vMap = new HashMap<UUID, Vehicle>();

        synchronized (vehicleIdSet)
        {
            for (UUID vehicleID : vehicleIdSet)
            {
                Vehicle vehicle = getVehicle(vehicleID);
                vMap.put(vehicleID, vehicle);
            }
        }

        return vMap;
    }

    /**
     * Retrieves a specific Vehicle by its UUID.
     *
     * @param uuid The UUID of the Vehicle.
     * @return The corresponding Vehicle, or null if not found.
     */
    public Vehicle getVehicle(UUID uuid)
    {
        String key = "Vehicle:" + uuid.toString();

        Vehicle vehicle = (Vehicle) redisTemplate.opsForValue().get(key);

        return vehicle;
    }

    private void setVehicle(Vehicle vehicle)
    {
        String key = "Vehicle:" + vehicle.getId().toString();

        redisTemplate.opsForValue().set(key, vehicle, 1, TimeUnit.MINUTES);
    }

    /**
     * Starts the VehicleManager, initiating periodic updates.
     */
    public void startup()
    {
        if (timer != null)
        {
            throw new IllegalStateException("VehicleManager has already started!");
        }

        Random random = new Random();
        long msDelay = (long) (msPeriod * random.nextDouble());
        logger.info("Starting VehicleManager with period of " + msPeriod + " ms and delay of " + msDelay + " ms.");

        timer = new Timer("VehicleManager Timer");
        timer.schedule(new UpdateTask(), msDelay, msPeriod);
    }

    /**
     * Stops the VehicleManager and clears all managed Vehicles.
     */
    public void shutdown()
    {
        if (timer == null)
        {
            throw new IllegalStateException("VehicleManager is not running!");
        }

        timer.cancel();
        timer = null;

        vehicleMap.clear();
    }

    /**
     * Checks if the VehicleManager is running.
     *
     * @return True if the manager is running, false otherwise.
     */
    public boolean isRunning()
    {
        return timer != null;
    }

    /**
     * Gets the update period in milliseconds.
     *
     * @return The update period.
     */
    public long getMsPeriod()
    {
        return msPeriod;
    }

    /**
     * Sets the update period. Restarts the manager if it is running.
     *
     * @param value The new update period in milliseconds.
     */
    public void setMsPeriod(long value)
    {
        boolean wasRunning = isRunning();
        if (wasRunning)
        {
            shutdown();
        }

        msPeriod = value;

        if (wasRunning)
        {
            startup();
        }
    }

    /**
     * Creates a new Vehicle based on a TripPlan.
     *
     * @param tripPlan The TripPlan containing the route and stops for the Vehicle.
     * @return The created Vehicle.
     */
    public Vehicle createVehicle(TripPlan tripPlan)
    {
        // Check for a valid trip plan.
        if (tripPlan == null)
        {
            throw new IllegalArgumentException("TripPlan cannot be null!");
        }

        Vehicle vehicle = new Vehicle();
        double metersOffset = 0.0;
        List<Coordinate> utmCoordList = new ArrayList<Coordinate>();
        List<LineSegmentData> listLineSegmentData = new ArrayList<>();
        LineSegmentData lineSegmentData = new LineSegmentData();
        lineSegmentData.setMetersOffset(metersOffset);

        LengthIndexedLine lengthIndexedLine;

        // Fetch directions for the trip plan.
        Directions directions = directionsService.getDirectionsForTripPlan(tripPlan);
        GeometryFactory geometryFactory = new GeometryFactory();

        // Initialize coordinate transformers.
        List<Double> startLocation = directions.getWaypoints().get(0).getLocation();
        ProjCoordinate geodeticCoordinate = new ProjCoordinate(startLocation.get(0), startLocation.get(1), 0.0);
        Coordinate coord = TopologyUtilities.projCoordToCoord(geodeticCoordinate);
        CoordinateTransform wgs84ToUtmCoordinatetransformer = TopologyUtilities
                .getWgs84ToUtmCoordinateTransformer(coord);
        CoordinateTransform utmToWgs84Coordinatetransformer = TopologyUtilities
                .getUtmToWgs84CoordinateTransformer(coord);
        lineSegmentData.setWgs84ToUtmCoordinatetransformer(wgs84ToUtmCoordinatetransformer);
        lineSegmentData.setUtmToWgs84Coordinatetransformer(utmToWgs84Coordinatetransformer);
        double lastLongitude = startLocation.get(0);

        // Iterate over route legs and steps to generate geometry and
        // update line segment data.
        List<RouteLeg> listLegs = directions.getRoutes().get(0).getLegs();
        for (RouteLeg leg : listLegs)
        {
            // Create a version of the leg in UTM coordinates
            for (RouteStep step : leg.getSteps())
            {
                // Test to see if the path has moved into another UTM region.
                // If so, new transformers need to be created.
                double newLongitude = step.getGeometry().getCoordinates().get(0).get(0);
                if (TopologyUtilities.isNewTransformerNeeded(lastLongitude, newLongitude))
                {
                    lastLongitude = newLongitude;

                    // Create a length indexed string for this segment and store it in the list
                    LineString utmLineString = geometryFactory
                            .createLineString(utmCoordList.toArray(new Coordinate[0]));
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

                // Now, loop through the coordinates of this Step, and convert them to local UTM
                // coordinates.
                ProjCoordinate projUtmCoord = new ProjCoordinate();
                for (List<Double> coordinate : step.getGeometry().getCoordinates())
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

        // Store the Directions and LineSegmentData locally in Maps
        directionsMap.put(vehicle.getId(), directions);
        lineSegmentDataMap.put(vehicle.getId(), listLineSegmentData);

        // Store the Vehicle.
        setVehicle(vehicle);

        // Finally, add it to the set of Vehicles.
        vehicleIdSet.add(vehicle.getId());

        return vehicle;
    }

    /**
     * Retrieves the Directions associated with a Vehicle.
     *
     * @param vehicleId The UUID of the Vehicle.
     * @return The Directions object for the Vehicle.
     */
    public Directions getVehicleDirections(UUID vehicleId)
    {
        return directionsMap.get(vehicleId);
    }

    /**
     * Retrieves the line segment data for a Vehicle.
     *
     * @param vehicleId The UUID of the Vehicle.
     * @return A list of LineSegmentData objects.
     */
    public List<LineSegmentData> getLlineSegmentData(UUID vehicleId)
    {
        return lineSegmentDataMap.get(vehicleId);
    }

    /**
     * TimerTask implementation to update the state of all Vehicles periodically.
     */
    class UpdateTask extends TimerTask
    {
        @Override
        public void run()
        {
            Set<UUID> deletionSet = new HashSet<UUID>();

            // Loop through each of the Vehicles and update them.
            for (UUID vehicleId : vehicleIdSet)
            {
                Vehicle vehicle = getVehicle(vehicleId);

                // Retrieve Directions and LineSegmentData for this Vehicle.
                vehicle.setDirections(getVehicleDirections(vehicleId));
                vehicle.setListLineSegmentData(lineSegmentDataMap.get(vehicleId));

                // Perform the update.
                boolean updated = vehicle.update();

                // If the vehicle didn't update, then it is finished. Add it to the deletion
                // Set.
                if (!updated)
                {
                    deletionSet.add(vehicleId);
                }
                setVehicle(vehicle);
            }

            // Remove any IDs in the deletionSet, if any.
            for (UUID vehicleID : deletionSet)
            {
                vehicleIdSet.remove(vehicleID);
                logger.info("Removing {} from active set of Vehicles.", vehicleID);
            }

            // Remove the Directions for deleted Vehicles from the directionsMap.
            synchronized (directionsMap)
            {
                for (UUID vehicleID : deletionSet)
                {
                    directionsMap.remove(vehicleID);
                }
            }

            // Remove the LineSegmentData for deleted Vehicles from the lineSegmentDataMap.
            synchronized (lineSegmentDataMap)
            {
                for (UUID vehicleID : deletionSet)
                {
                    lineSegmentDataMap.remove(vehicleID);
                }
            }
        }
    }
}
