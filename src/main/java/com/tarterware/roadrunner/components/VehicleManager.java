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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.models.mapbox.RouteLeg;
import com.tarterware.roadrunner.models.mapbox.RouteStep;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.utilities.JitterStatisticsCollector;
import com.tarterware.roadrunner.utilities.TopologyUtilities;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

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

    // Target interval between vehicle updates.
    @Value("${com.tarterware.roadrunner.vehicle-update-period:250ms}")
    private String msUpdatePeriodString;

    // Target interval between polling for vehicles that need updating. Should be
    // lower than update period.
    @Value("${com.tarterware.roadrunner.vehicle-polling-period:100ms}")
    private String msPollingPeriodString;

    @Value("${com.tarterware.roadrunner.jitter-stat-capacity:200}")
    private int jitterStatCapacity;

    // Polling period in milliseconds.
    private long msPollingPeriod;

    // Update period in milliseconds.
    private long msUpdatePeriod;

    // Hostname where this manager is running
    private String hostName;

    // Dedicated thread pool to handle the Directions processing
    private final ExecutorService directionsExecutor = Executors.newFixedThreadPool(10);

    // Map of Directions by Vehicle ID.
    private final ConcurrentMap<UUID, Directions> directionsMap = new ConcurrentHashMap<UUID, Directions>();

    // LineSegmentData by Vehicle ID.
    private final ConcurrentMap<UUID, List<LineSegmentData>> lineSegmentDataMap = new ConcurrentHashMap<UUID, List<LineSegmentData>>();

    // Thread‑safe map to keep track of vehicles currently loading directions.
    private final ConcurrentMap<UUID, Future<Directions>> directionsLoadingMap = new ConcurrentHashMap<>();

    private CopyOnWriteArrayList<String> activeIdsList = new CopyOnWriteArrayList<String>();

    // Service to retrieve directions for trip plans
    private DirectionsService directionsService;

    // Class to provide jitter statistics over last few runs of update loop
    private JitterStatisticsCollector statisticsCollector;

    // Thread-safe implementation of Number used to access the latestExecutionTime
    // and the other exposed statistics.
    private final AtomicReference<Double> msStdDevJitterTime = new AtomicReference<>(0.0);
    private final AtomicReference<Double> msMinimumJitterTime = new AtomicReference<>(0.0);
    private final AtomicReference<Double> msMaximumJitterTime = new AtomicReference<>(0.0);
    private final AtomicReference<Double> msMeanJitterTime = new AtomicReference<>(0.0);

    public static final int SECS_VEHICLE_TIMEOUT = 30;

    public static final String ENDPOINT_STD_DEV_JITTER_TIME = "roadrunner.std-dev.jitter.time.milliseconds";
    public static final String ENDPOINT_MINIMUM_JITTER_TIME = "roadrunner.minimum.jitter.time.milliseconds";
    public static final String ENDPOINT_MAXIMUM_JITTER_TIME = "roadrunner.maximum.jitter.time.milliseconds";
    public static final String ENDPOINT_MEAN_JITTER_TIME = "roadrunner.mean.jitter.time.milliseconds";

    public static final String ACTIVE_VEHICLE_REGISTRY = "ActiveVehicleRegistry";

    public static final String VEHICLE_UPDATE_LOCK_SET = "VehicleUpdateLockSet";

    public static final String VEHICLE_UPDATE_QUEUE_ZSET = "VehicleUpdateQueue";

    public static final String TRIP_PLAN_KEY = "TripPlan";

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
        meterRegistry.gauge(ENDPOINT_STD_DEV_JITTER_TIME, msStdDevJitterTime, AtomicReference::get);
        meterRegistry.gauge(ENDPOINT_MINIMUM_JITTER_TIME, msMinimumJitterTime, AtomicReference::get);
        meterRegistry.gauge(ENDPOINT_MAXIMUM_JITTER_TIME, msMaximumJitterTime, AtomicReference::get);
        meterRegistry.gauge(ENDPOINT_MEAN_JITTER_TIME, msMeanJitterTime, AtomicReference::get);
    }

    @PostConstruct
    public void init()
    {
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
        Duration duration = DurationStyle.detect(msPollingPeriodString).parse(msPollingPeriodString);
        msPollingPeriod = duration.toMillis();

        duration = DurationStyle.detect(msUpdatePeriodString).parse(msUpdatePeriodString);
        msUpdatePeriod = duration.toMillis();

        // Create the statistics collector to aid in publishing metrics.
        this.statisticsCollector = new JitterStatisticsCollector(jitterStatCapacity);

        logger.info("VehicleManager starting with polling period of {} ms; update Period {} ms.", msPollingPeriod,
                msUpdatePeriod);
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
     * @return A Map of Vehicles by their UUIDs.
     */
    public Map<UUID, Vehicle> getVehicleMap(int page, int pageSize)
    {
        // Create a Map of Vehicles to return
        Map<UUID, Vehicle> vMap = new HashMap<>();

        int size = activeIdsList.size();
        if (size == 0)
        {
            return vMap;
        }

        int index = page * pageSize;
        if (index > size)
        {
            throw new IllegalArgumentException("Page " + page + " of page size " + pageSize + " outside bounds!");
        }

        // Now, loop through the cursor, filling up to "pageSize" vehicleId elements.
        int recordsToFill = pageSize;
        List<String> idList = new ArrayList<>();
        while ((recordsToFill > 0) && (index < size))
        {
            String vehicleId = activeIdsList.get(index++);
            idList.add(vehicleId);
            recordsToFill--;
        }

        // Convert each vehicle ID to its full key
        List<String> keys = idList.stream().map(id -> getVehicleKey(UUID.fromString(id))).collect(Collectors.toList());

        // Fetch multiple Vehicle objects using opsForValue()
        List<Object> vehicleObjects = redisTemplate.opsForValue().multiGet(keys);

        // Process the fetched Vehicle objects
        for (int i = 0; i < idList.size(); i++)
        {
            Object obj = vehicleObjects.get(i);
            String id = idList.get(i);
            if (obj == null)
            {
                // If the vehicle is null, perform redis cleanup.
                logger.info("Vehicle ID {} no longer exists. Removing from queue.", id);
                redisTemplate.opsForZSet().remove(VEHICLE_UPDATE_QUEUE_ZSET, id);
                redisTemplate.opsForSet().remove(ACTIVE_VEHICLE_REGISTRY, id);
                redisTemplate.opsForSet().remove(VEHICLE_UPDATE_LOCK_SET, id);
            }
            else
            {
                Vehicle vehicle = (Vehicle) obj;
                if (vehicle.getId() != null)
                {
                    vMap.put(vehicle.getId(), vehicle);
                }
            }
        }

        return vMap;
    }

    /**
     * Resets the simulation by clearing all vehicle data and related information
     * from Redis. This method deletes the following keys and their associated
     * values:
     * <ul>
     * <li>{@link #VEHICLE_UPDATE_QUEUE_ZSET}: The sorted set containing vehicles to
     * be updated.</li>
     * <li>{@link #ACTIVE_VEHICLE_REGISTRY}: The set of active vehicle IDs.</li>
     * <li>{@link #TRIP_PLAN_KEY}: The key storing trip plan information.</li>
     * <li>{@link #VEHICLE_UPDATE_LOCK_SET}: The set used for locking vehicles
     * during updates.</li>
     * <li>All keys starting with "vehicle:": These keys store individual vehicle
     * data.</li>
     * </ul>
     * This method is typically used to clean up the Redis database before starting
     * a new simulation or to remove all existing vehicle data.
     */
    public void reset()
    {
        logger.info("Resetting the Redis variables");

        redisTemplate.delete(VEHICLE_UPDATE_QUEUE_ZSET);
        redisTemplate.delete(ACTIVE_VEHICLE_REGISTRY);
        redisTemplate.delete(TRIP_PLAN_KEY);
        redisTemplate.delete(VEHICLE_UPDATE_LOCK_SET);

        // Delete all of the Vehicle data.
        redisTemplate.delete("vehicle:*");
    }

    /**
     * Retrieves a specific Vehicle by its UUID.
     *
     * @param uuid The UUID of the Vehicle.
     * @return The corresponding Vehicle, or null if not found.
     */
    public Vehicle getVehicle(UUID uuid)
    {
        String vehicleKey = getVehicleKey(uuid);

        Vehicle vehicle = (Vehicle) redisTemplate.opsForValue().get(vehicleKey);

        return vehicle;
    }

    /**
     * Returns the Redis key used to store a Vehicle object. The key is formatted as
     * "vehicle:{vehicleId}".
     *
     * @param vehicleId The UUID of the Vehicle.
     * @return The formatted Redis key for the Vehicle.
     */
    public String getVehicleKey(UUID vehicleId)
    {
        return String.format("vehicle:%s", vehicleId.toString());
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
        String hashKey = vehicle.getId().toString();
        redisTemplate.opsForHash().put(TRIP_PLAN_KEY, hashKey, tripPlan);

        processTripPlanData(vehicle.getId(), tripPlan);

        // Update the last calculated time, since processTripPlandata can take a
        // while...
        vehicle.setLastCalculationEpochMillis(Instant.now().toEpochMilli());

        // Add a marker that this manager calculated the vehicle state.
        vehicle.setManagerHost(hostName);

        String vehicleKey = getVehicleKey(vehicle.getId());
        redisTemplate.opsForValue().set(vehicleKey, vehicle);

        // Add it to the set of Vehicles to be updated.
        redisTemplate.opsForZSet().add(VEHICLE_UPDATE_QUEUE_ZSET, hashKey, vehicle.lastCalculationEpochMillis);

        // Add the vehicle to the Active Vehicle Registry
        redisTemplate.opsForSet().add(ACTIVE_VEHICLE_REGISTRY, vehicle.getId().toString());

        logger.info("Created vehicle ID {}", vehicle.getId());

        return vehicle;
    }

    /**
     * Processes a TripPlan to generate route geometry and line segment data for a
     * Vehicle. This method performs the following steps: *
     * <ol>
     * <li>Fetches directions for the trip plan using the DirectionsService.</li>
     * <li>Initializes coordinate transformers for converting between WGS84 and UTM
     * coordinates.</li>
     * <li>Iterates over route legs and steps to extract coordinate data.</li>
     * <li>Converts WGS84 coordinates to UTM coordinates and handles transitions
     * between UTM zones.</li>
     * <li>Generates line segments for each UTM zone and stores them as
     * LineSegmentData objects.</li>
     * <li>Stores the Directions and LineSegmentData in the respective maps for
     * later use.</li>
     * </ol>
     *
     * @param vehicleId The UUID of the Vehicle.
     * @param tripPlan  The TripPlan containing route and stop information.
     */
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
     * @param vehicleId     The UUID of the Vehicle.
     * @param waitForResult True if the caller wants to wait for the result of the
     *                      DirectionsService call; false otherwise.
     * @return The Directions object for the Vehicle.
     */
    public Directions getVehicleDirections(UUID vehicleId, boolean waitForResult)
    {
        // Try to get Directions from the already computed map.
        Directions directions = directionsMap.get(vehicleId);
        if (directions != null)
        {
            return directions;
        }

        // Attempt to start an asynchronous load if not already in progress.
        Future<Directions> future = directionsLoadingMap.computeIfAbsent(vehicleId,
                id -> directionsExecutor.submit(() ->
                {
                    // Retrieve the TripPlan from Redis (or cache) for this vehicle.
                    String hashKey = id.toString();
                    TripPlan tripPlan = (TripPlan) redisTemplate.opsForHash().get(TRIP_PLAN_KEY, hashKey);
                    if (tripPlan == null)
                    {
                        throw new IllegalStateException("TripPlan not found for Vehicle ID: " + id);
                    }

                    // Process the trip plan to build Directions and LineSegmentData.
                    processTripPlanData(id, tripPlan);
                    return directionsMap.get(id);
                }));

        // If caller said not to wait, return null to signal Vehicle is not ready to
        // process on this instance.
        if (!waitForResult)
        {
            return null;
        }

        try
        {
            // Wait for the asynchronous load to complete.
            directions = future.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new RuntimeException("Failed to load directions for Vehicle ID " + vehicleId, e);
        }
        finally
        {
            // Remove the future from the loading map so future requests can trigger a
            // reload if needed.
            directionsLoadingMap.remove(vehicleId);
        }

        return directions;
    }

    /**
     * Retrieves the line segment data for a Vehicle.
     *
     * @param vehicleId The UUID of the Vehicle.
     * @return A list of LineSegmentData objects.
     */
    public List<LineSegmentData> getLineSegmentData(UUID vehicleId)
    {
        return lineSegmentDataMap.get(vehicleId);
    }

    long lastNsManagerStartTime = System.nanoTime();

    /**
     * Periodically updates the positions of all active vehicles. This method
     * retrieves a set of vehicles that are ready to be updated based on their last
     * update time and the configured update period. For each vehicle, it performs
     * the following steps: *
     * <ol>
     * <li>Retrieves the vehicle's directions and line segment data.</li>
     * <li>Calculates the time since the last update and updates the vehicle's
     * position if necessary.</li>
     * <li>Records jitter statistics, which measure the deviation from the expected
     * update interval.</li>
     * <li>Updates the vehicle's state in Redis, including its last execution time
     * and the manager host that performed the update.</li>
     * <li>Checks if the vehicle has timed out and marks it for deletion if
     * necessary.</li>
     * </ol>
     * After processing all vehicles, the method cleans up deleted vehicles and
     * updates the jitter statistics for monitoring performance.
     */
    @Scheduled(fixedRateString = "${com.tarterware.roadrunner.vehicle-polling-period}")
    public void updateVehicles()
    {
        long currentEpochMillis = Instant.now().toEpochMilli();
        long msEpochTimeoutTime = currentEpochMillis - (1000 * SECS_VEHICLE_TIMEOUT);

        Set<UUID> deletionSet = new HashSet<>();

        long vehicleCount = redisTemplate.opsForSet().size(ACTIVE_VEHICLE_REGISTRY);
        Set<Object> readyVehicles = fetchReadyVehicles(currentEpochMillis);
        int readyVehicleCount = 0;
        if (readyVehicles != null)
        {
            int index = 0;
            readyVehicleCount = readyVehicles.size();

            for (Object vehicleIdObj : readyVehicles)
            {
                long nsVehicleStartTime = System.nanoTime();
                UUID vehicleId = UUID.fromString((String) vehicleIdObj);

                // If directions haven't finished generating, it will return null.
                Directions vehicleDirections = getVehicleDirections(vehicleId, false);

                if (vehicleDirections != null)
                {
                    // Attempt to add this vehicle ID to the update lock set. If the add fails,
                    // another instance is updating this vehicle, and so should be skipped.
                    if (redisTemplate.opsForSet().add(VEHICLE_UPDATE_LOCK_SET, vehicleId) > 0)
                    {
                        String vehicleKey = getVehicleKey(vehicleId);

                        try
                        {
                            Vehicle vehicle = (Vehicle) redisTemplate.opsForValue().get(vehicleKey);
                            if (vehicle != null)
                            {
                                long msJitter = 0;
                                boolean updated = false;

                                vehicle.setDirections(vehicleDirections);
                                vehicle.setListLineSegmentData(lineSegmentDataMap.get(vehicleId));

                                // Calculate and record the jitter for this vehicle.
                                long msSinceLastRun = Instant.now().toEpochMilli()
                                        - vehicle.getLastCalculationEpochMillis();

                                if (msSinceLastRun > msPollingPeriod)
                                {
                                    updated = vehicle.update();

                                    // If the vehicle was updated, update it's statistics.
                                    if (updated)
                                    {
                                        msJitter = msSinceLastRun - msUpdatePeriod;
                                    }
                                }
                                statisticsCollector.recordMeasurement(msJitter);

                                if (msJitter > 400)
                                {
                                    logger.info("{}/{}; {} ms; Vehicle {}", //
                                            index, readyVehicleCount, msJitter, vehicleId);
                                }

                                // Update vehicle execution time and store vehicle state
                                vehicle.setLastNsExecutionTime(System.nanoTime() - nsVehicleStartTime);

                                // Mark that this manager calculated the vehicle state.
                                vehicle.setManagerHost(hostName);

                                redisTemplate.opsForValue().set(vehicleKey, vehicle);
                                redisTemplate.opsForZSet().add(VEHICLE_UPDATE_QUEUE_ZSET, vehicleId,
                                        vehicle.getLastCalculationEpochMillis());

                                // If the vehicle was not updated, see if it has reached timeout.
                                if (!updated)
                                {
                                    if (vehicle.getLastCalculationEpochMillis() < msEpochTimeoutTime)
                                    {
                                        deletionSet.add(vehicleId);
                                        logger.info("Deleting vehicle ID {}", vehicle.getId());
                                    }
                                }
                            }
                        }
                        catch (Exception e)
                        {
                            logger.error("Unexpected Exception updating vehicle {}", vehicleId, e);
                        }
                        finally
                        {
                            redisTemplate.opsForSet().remove(VEHICLE_UPDATE_LOCK_SET, vehicleId);
                        }
                    }
                }
                index++;
            }
        }

        // Clean up the data of the vehicles that have been found to be deleted
        cleanupDeletedVehicles(deletionSet);

        // Take care of corner case: with no vehicles, the stats never update.
        // Add some jitter-free numbers so the averages will trend down.
        if (vehicleCount == 0)
        {
            statisticsCollector.recordMeasurement(0);
        }

        // Update the statistics endpoint variables.
        double msMinimumJitterTime = statisticsCollector.getMin();
        double msMaximumJitterTime = statisticsCollector.getMax();
        double msMeanJitterTime = statisticsCollector.getMean();
        double msStdDevJitterTime = statisticsCollector.getStandardDeviation();
        this.msMinimumJitterTime.set(msMinimumJitterTime);
        this.msMaximumJitterTime.set(msMaximumJitterTime);
        this.msMeanJitterTime.set(msMeanJitterTime);
        this.msStdDevJitterTime.set(msStdDevJitterTime);

        // Log this information at debug level.
        logger.atDebug().setMessage("Cnt:{}; Rdy:{}; Ave:{}; Std:{}; Min:{}; Max:{}")
                .addArgument(String.format("%3d", vehicleCount)) // Cnt
                .addArgument(String.format("%3d", readyVehicleCount)) // Rdy
                .addArgument(String.format("%.3f", msMeanJitterTime)) // Ave
                .addArgument(String.format("%.3f", msStdDevJitterTime)) // Std
                .addArgument(String.format("%.3f", msMinimumJitterTime)) // Min
                .addArgument(String.format("%.3f", msMaximumJitterTime)) // Max
                .log();
    }

    /**
     * Fetches a set of vehicles that are ready to be updated based on the current
     * time and the configured update period.
     *
     * @param currentEpochMillis The current time in milliseconds since the epoch.
     * @return A set of vehicle IDs (as Objects) that are ready for update.
     */
    private Set<Object> fetchReadyVehicles(long currentEpochMillis)
    {
        return redisTemplate.opsForZSet().rangeByScore(VEHICLE_UPDATE_QUEUE_ZSET, 0,
                currentEpochMillis - msUpdatePeriod + msPollingPeriod);
    }

    /**
     * Cleans up deleted vehicles by removing their data from Redis and local
     * caches. This method performs the following actions: *
     * <ul>
     * <li>Removes the vehicle IDs from the {@link #ACTIVE_VEHICLE_REGISTRY}
     * set.</li>
     * <li>Removes the vehicle's directions and line segment data from the local
     * maps.</li>
     * <li>Removes the vehicle's state from Redis, including its entry in the
     * {@link #VEHICLE_UPDATE_QUEUE_ZSET} and the {@link #ACTIVE_VEHICLE_REGISTRY}
     * set.</li>
     * </ul>
     * * @param deletionSet The set of vehicle IDs to be deleted.
     */
    private void cleanupDeletedVehicles(Set<UUID> deletionSet)
    {
        // Remove from Active Vehicle Registry
        redisTemplate.opsForSet().remove(ACTIVE_VEHICLE_REGISTRY, deletionSet);

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
            String hashKey = vehicleID.toString();
            redisTemplate.opsForZSet().remove(VEHICLE_UPDATE_QUEUE_ZSET, hashKey);
            redisTemplate.opsForSet().remove(ACTIVE_VEHICLE_REGISTRY, hashKey);

            redisTemplate.delete(getVehicleKey(vehicleID));
        });
    }

    /**
     * Periodically reconciles the local caches ({@link #directionsMap} and
     * {@link #lineSegmentDataMap}) with the active vehicle list in Redis. This
     * method ensures that the caches do not contain data for vehicles that have
     * been removed from the active vehicle registry. It also updates the
     * {@link #statisticsCollector} to ensure that it has enough capacity to store
     * jitter statistics for the current number of active vehicles.
     */
    @Scheduled(fixedRate = 60000) // every minute
    public void reconcileLocalCache()
    {
        // For directionsMap, create a list of IDs to remove
        List<UUID> removedFromDirections = new ArrayList<>();

        // Iterate over a copy of the key set to avoid ConcurrentModificationException
        Set<UUID> directionsKeys = new HashSet<>(directionsMap.keySet());
        for (UUID vehicleId : directionsKeys)
        {
            if (!activeIdsList.contains(vehicleId.toString()))
            {
                removedFromDirections.add(vehicleId);
                directionsMap.remove(vehicleId);
            }
        }
        if (!removedFromDirections.isEmpty())
        {
            logger.info("Reconciled directionsMap: Removed vehicles: {}", removedFromDirections);
        }

        // Do the same for lineSegmentDataMap
        List<UUID> removedFromLineSegmentData = new ArrayList<>();
        Set<UUID> lineSegmentKeys = new HashSet<>(lineSegmentDataMap.keySet());
        for (UUID vehicleId : lineSegmentKeys)
        {
            if (!activeIdsList.contains(vehicleId.toString()))
            {
                removedFromLineSegmentData.add(vehicleId);
                lineSegmentDataMap.remove(vehicleId);
            }
        }
        if (!removedFromLineSegmentData.isEmpty())
        {
            logger.info("Reconciled lineSegmentDataMap: Removed vehicles: {}", removedFromLineSegmentData);
        }

        // Update the JitterStatisticsCollector so that the 10 last statistics for each
        // vehicle will be recorded.
        int vehicleCount = (int) (10 * redisTemplate.opsForSet().size(ACTIVE_VEHICLE_REGISTRY));
        if (vehicleCount < 10)
        {
            vehicleCount = 10;
        }

        if (vehicleCount != statisticsCollector.getCount())
        {
            logger.info("Reconfiguring statistics collector to have {} slots", vehicleCount);

            statisticsCollector = new JitterStatisticsCollector(statisticsCollector, vehicleCount);
        }
    }

    /**
     * Periodically retrieves the current list of active vehicles from Redis and
     * updates the local cache. This method fetches the members of the
     * {@link #ACTIVE_VEHICLE_REGISTRY} set from Redis, converts them to a list of
     * strings, and updates the {@link #activeIdsList} with the new list. The
     * {@link #activeIdsList} is a thread-safe list that provides a snapshot of the
     * active vehicles at a specific point in time, allowing other methods to safely
     * iterate over the list without encountering ConcurrentModificationExceptions.
     */
    @Scheduled(fixedRate = 1000) // every second
    public void getCurrentVehicleList()
    {
        // Grab the members of the active Vehicle Set and convert it to a Java List
        Set<Object> idSet = redisTemplate.opsForSet().members(ACTIVE_VEHICLE_REGISTRY);
        List<String> idsList = idSet.stream().map(Object::toString).collect(Collectors.toList());

        // Update the shared instance variable. Note that CopyOnWriteArrayList,
        // iterators operate on a snapshot of the list at the time the iterator was
        // created, so the other threads shouldn't throw exceptions.
        activeIdsList.clear();
        activeIdsList.addAll(idsList);
    }
}
