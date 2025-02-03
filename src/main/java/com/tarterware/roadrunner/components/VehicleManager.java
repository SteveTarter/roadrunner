package com.tarterware.roadrunner.components;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.models.mapbox.RouteLeg;
import com.tarterware.roadrunner.models.mapbox.RouteStep;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.utilities.TopologyUtilities;

import io.micrometer.core.instrument.MeterRegistry;

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

    // TODO - wire this up to an applications-property (not working atm)
    // @Value("${com.tarterware.roadrunner.updateperiod}")
    private String msPeriodString = "100";

    // Update period in milliseconds.
    private long msPeriod;

    // Hostname where this manager is running
    private String hostName;

    // Map of Directions by Vehicle ID.
    private final ConcurrentMap<UUID, Directions> directionsMap = new ConcurrentHashMap<UUID, Directions>();

    // LineSegmentData by Vehicle ID.
    private final ConcurrentMap<UUID, List<LineSegmentData>> lineSegmentDataMap = new ConcurrentHashMap<UUID, List<LineSegmentData>>();

    // Service to retrieve directions for trip plans
    private DirectionsService directionsService;

    // Thread-safe implementation of Number used to access the latestExecutionTime.
    private final AtomicReference<Double> msLatestExecutionTime = new AtomicReference<>(0.0);

    public static final int SECS_VEHICLE_TIMEOUT = 30;

    public static final String METRICS_ENDPOINT = "roadrunner.latest.execution.time.milliseconds";

    public static final String VEHICLE_PREFIX = "Vehicle:";

    public static final String ACTIVE_VEHICLE_REGISTRY = "ActiveVehicleRegistry";

    public static final String VEHICLE_QUEUE = "VehicleQueue";

    public static final String TRIP_PLAN_PREFIX = "TripPlan:";

    private static final Logger logger = LoggerFactory.getLogger(VehicleManager.class);

    /**
     * Constructor to initialize the VehicleManager with a DirectionsService.
     *
     * @param directionsService The service to retrieve directions for trip plans.
     * @param redisTemplate     RedisTemplate for performing Redis operations, such
     *                          as storing and retrieving data. Keys are Strings,
     *                          and values are serialized Java objects.
     * @param meterRegistry     Creates and manages application's set of meters.
     * @param environment       Interface representing the environment in which the
     *                          current application is running.
     */
    public VehicleManager(DirectionsService directionsService, RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry, Environment environment)
    {
        super();

        this.directionsService = directionsService;
        this.redisTemplate = redisTemplate;

        // Register a gauge to expose the latest execution time.
        meterRegistry.gauge(METRICS_ENDPOINT, msLatestExecutionTime, AtomicReference::get);

        // Try to determine the hostname of the machine that is running this instance of
        // Spring Boot. If unable to do so, set it to "UNKNOWN".
        try
        {
            InetAddress inetAddress = InetAddress.getLocalHost();
            this.hostName = inetAddress.getHostName();
        }
        catch (UnknownHostException e)
        {
            logger.error("Unable to determine hostName!  Setting manager host to UNKNOWN");
            this.hostName = "UNKNOWN";
        }
        // DurationStyle.detect() determines the style (e.g., SIMPLE, ISO-8601) based on
        // the string
        Duration duration = DurationStyle.detect(msPeriodString).parse(msPeriodString);
        msPeriod = duration.toMillis();
    }

    /**
     * Gets the count of all Vehicles.
     *
     * @return The number of active Vehicles.
     */
    public int getVehicleCount()
    {
        return redisTemplate.opsForSet().size(ACTIVE_VEHICLE_REGISTRY).intValue();
    }

    /**
     * Gets a paginated map of Vehicles.
     *
     * @param page     page number to retrieve.
     * @param pageSize number of Vehicles in each page.
     * @return A synchronized map of Vehicles by their UUIDs.
     */
    public Map<UUID, Vehicle> getVehicleMap(int page, int pageSize)
    {
        // Create a Map of Vehicles to return
        Map<UUID, Vehicle> vMap = new HashMap<UUID, Vehicle>();

        // Use SSCAN to iterate over the elements of the set
        ScanOptions scanOptions = ScanOptions.scanOptions().count(pageSize).build();
        Cursor<Object> cursor = redisTemplate.opsForSet().scan(ACTIVE_VEHICLE_REGISTRY, scanOptions);

        // Skip over records until we get to the target window.
        int recordsToSkip = page * pageSize;
        while ((recordsToSkip > 0) && cursor.hasNext())
        {
            cursor.next();
            --recordsToSkip;
        }

        // Now, loop through the cursor, filling up to "pageSize" elements.
        int recordsToFill = pageSize;
        while ((recordsToFill > 0) && cursor.hasNext())
        {
            String vehicleId = cursor.next().toString();
            Vehicle vehicle = getVehicle(UUID.fromString(vehicleId));
            if (vehicle != null)
            {
                --recordsToFill;
                vMap.put(UUID.fromString(vehicleId), vehicle);
            }
            else
            {
                logger.info("Vehicle ID {} no longer exists. Removing from queue.", vehicleId);
                redisTemplate.opsForZSet().remove(VEHICLE_QUEUE, vehicleId);
                redisTemplate.opsForSet().remove(ACTIVE_VEHICLE_REGISTRY, vehicleId);
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
        String key = getVehicleKey(uuid);

        Vehicle vehicle = (Vehicle) redisTemplate.opsForValue().get(key);

        return vehicle;
    }

    private void setVehicle(Vehicle vehicle)
    {
        String key = getVehicleKey(vehicle.getId());

        // Add a marker that this manager calculated the vehicle state.
        vehicle.setManagerHost(hostName);

        redisTemplate.opsForValue().set(key, vehicle, 1, TimeUnit.HOURS);

        // Add it to the set of Vehicles to be updated.
        redisTemplate.opsForZSet().add(VEHICLE_QUEUE, vehicle.getId().toString(), vehicle.lastCalculationEpochMillis);
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

        // Put the TripPlan out there so that other VehicleManagers can create the
        // Directions and LineSegmentData needed to drive a Vehicle.
        String key = TRIP_PLAN_PREFIX + vehicle.getId();
        redisTemplate.opsForValue().set(key, tripPlan, 100, TimeUnit.HOURS);

        processTripPlanData(vehicle.getId(), tripPlan);

        // Store the Vehicle.
        setVehicle(vehicle);

        // Add the vehicle to the Active Vehicle Registry
        redisTemplate.opsForSet().add(ACTIVE_VEHICLE_REGISTRY, vehicle.getId().toString());

        logger.info("Created vehicle ID {}", vehicle.getId());

        return vehicle;
    }

    public void processTripPlanData(UUID vehicleId, TripPlan tripPlan)
    {
        // Check for a valid trip plan.
        if (tripPlan == null)
        {
            throw new IllegalArgumentException("TripPlan cannot be null!");
        }

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
        directionsMap.put(vehicleId, directions);
        lineSegmentDataMap.put(vehicleId, listLineSegmentData);
    }

    /**
     * Retrieves the Directions associated with a Vehicle.
     *
     * @param vehicleId The UUID of the Vehicle.
     * @return The Directions object for the Vehicle.
     */
    public Directions getVehicleDirections(UUID vehicleId)
    {
        Directions directions = directionsMap.get(vehicleId);

        // If the Directions are null, then retrieve the TripPlan for this
        // Vehicle, and generate the Directions and LineSegmentData.
        if (directions == null)
        {
            String key = TRIP_PLAN_PREFIX + vehicleId;
            TripPlan tripPlan = (TripPlan) redisTemplate.opsForValue().get(key);

            processTripPlanData(vehicleId, tripPlan);

            directions = directionsMap.get(vehicleId);

            if (directions == null)
            {
                throw new IllegalStateException("Unable to recreate Directions for Vehicle ID" + vehicleId);
            }
        }

        return directions;
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

    private String getVehicleKey(UUID vehicleId)
    {
        return VEHICLE_PREFIX + vehicleId.toString();
    }

    @Scheduled(fixedRateString = "100")
    public void updateVehicles()
    {
        long nsManagerStartTime = System.nanoTime();
        long currentEpochMillis = Instant.now().toEpochMilli();
        long msEpochTimeoutTime = currentEpochMillis - (1000 * SECS_VEHICLE_TIMEOUT);

        Set<UUID> deletionSet = new HashSet<>();

        try
        {
            Set<ZSetOperations.TypedTuple<Object>> readyVehicles = fetchReadyVehicles(currentEpochMillis);
            if (readyVehicles != null)
            {
                for (ZSetOperations.TypedTuple<Object> tuple : readyVehicles)
                {
                    processVehicle(tuple, deletionSet, msEpochTimeoutTime);
                }
            }

            cleanupDeletedVehicles(deletionSet);

            double msExecutionTime = (System.nanoTime() - nsManagerStartTime) / 1_000_000.0;
            msLatestExecutionTime.set(msExecutionTime);
        }
        catch (Exception ex)
        {
            logger.error("Exception encountered during vehicle update", ex);
        }
    }

    private Set<ZSetOperations.TypedTuple<Object>> fetchReadyVehicles(long currentEpochMillis)
    {
        return redisTemplate.opsForZSet().rangeByScoreWithScores(VEHICLE_QUEUE, 0, currentEpochMillis - msPeriod);
    }

    private void processVehicle(ZSetOperations.TypedTuple<Object> tuple, Set<UUID> deletionSet, long msEpochTimeoutTime)
    {
        long nsVehicleStartTime = System.nanoTime();
        UUID vehicleId = UUID.fromString((String) tuple.getValue());

        // Atomically remove from the queue
        redisTemplate.opsForZSet().remove(VEHICLE_QUEUE, vehicleId.toString());

        Vehicle vehicle = getVehicle(vehicleId);
        if (vehicle != null)
        {
            // Retrieve additional data
            vehicle.setDirections(getVehicleDirections(vehicleId));
            vehicle.setListLineSegmentData(lineSegmentDataMap.get(vehicleId));

            boolean updated = vehicle.update();
            if (updated)
            {
                vehicle.setLastNsExecutionTime(System.nanoTime() - nsVehicleStartTime);
                setVehicle(vehicle);
            }
            else
            {
                if (vehicle.getLastCalculationEpochMillis() < msEpochTimeoutTime)
                {
                    deletionSet.add(vehicleId);
                    logger.info("Deleting vehicle ID {}", vehicle.getId());
                }
                else
                {
                    setVehicle(vehicle);
                }
            }
        }
    }

    private void cleanupDeletedVehicles(Set<UUID> deletionSet)
    {
        // Remove from Active Vehicle Registry
        for (UUID vehicleID : deletionSet)
        {
            redisTemplate.opsForSet().remove(ACTIVE_VEHICLE_REGISTRY, vehicleID.toString());
        }

        // Remove from directionsMap and lineSegmentDataMap (assuming thread-safe maps
        // or appropriate synchronization)
        deletionSet.forEach(vehicleID ->
        {
            directionsMap.remove(vehicleID);
            lineSegmentDataMap.remove(vehicleID);
        });

        // Remove the vehicle states from Redis
        deletionSet.forEach(vehicleID ->
        {
            String key = getVehicleKey(vehicleID);
            redisTemplate.opsForValue().getAndDelete(key);
        });
    }
}
