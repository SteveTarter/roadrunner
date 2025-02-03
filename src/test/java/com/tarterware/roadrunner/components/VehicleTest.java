package com.tarterware.roadrunner.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tarterware.roadrunner.configs.NoOpSchedulerConfig;
import com.tarterware.roadrunner.configs.RedisConfig;
import com.tarterware.roadrunner.configs.SecurityConfig;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;
import com.tarterware.roadrunner.services.IsochroneService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import utils.TestUtils;

@SpringBootTest
@Import(NoOpSchedulerConfig.class)
class VehicleTest
{
    @MockitoBean
    private DirectionsService directionsService;

    @MockitoBean
    private GeocodingService geocodingService;

    @MockitoBean
    private IsochroneService isochroneService;

    @MockitoBean
    private SecurityConfig securityConfig;

    @MockitoBean
    private RedisConfig redisConfig;

    @MockitoBean
    private LettuceConnectionFactory redisStandAloneConnectionFactory;

    @MockitoBean
    private SecurityFilterChain filterChain;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private DirectionsService mockDirectionsService;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    private MeterRegistry meterRegistry;

    private VehicleManager vehicleManager;

    private Vehicle vehicle;

    private TripPlan mockTripPlan;

    private Directions mockDirections;

    @Autowired
    private Environment environment;

    @BeforeEach
    void setup() throws IOException
    {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Mock RedisTemplate behavior
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(anyString(), any(), anyDouble())).thenReturn(true);

        // Stub "ready" vehicles
        Set<TypedTuple<Object>> tuple = new HashSet<>();
        when(zSetOperations.rangeByScoreWithScores(any(), anyDouble(), anyDouble())).thenReturn(tuple);

        // Mock DirectionsService
        mockTripPlan = mock(TripPlan.class);
        mockDirections = TestUtils.loadMockDirections("src/test/resources/test_directions.json");

        when(mockDirectionsService.getDirectionsForTripPlan(any())).thenReturn(mockDirections);

        meterRegistry = new SimpleMeterRegistry();

        vehicleManager = new VehicleManager(directionsService, redisTemplate, meterRegistry, environment);

        // Create the vehicle
        vehicle = vehicleManager.createVehicle(mockTripPlan);

        // Ensure Redis returns the vehicle when asked by ID
        UUID vehicleId = vehicle.getId();
        when(valueOperations.get(VehicleManager.VEHICLE_PREFIX + vehicleId)).thenReturn(vehicle);

        // Stub "ready" vehicle IDs in Redis
        tuple.add(new DefaultTypedTuple<>(vehicleId.toString(), 1000.0));
        when(zSetOperations.rangeByScoreWithScores(any(), anyDouble(), anyDouble())).thenReturn(tuple);

        vehicle.setDirections(vehicleManager.getVehicleDirections(vehicleId));
        vehicle.setListLineSegmentData(vehicleManager.getLlineSegmentData(vehicleId));
    }

    @Test
    void testUpdate_reachesEndOfRoute()
    {
        // Simulate updates that move the vehicle to the end of the route
        double routeDistance = mockDirections.getRoutes().get(0).getDistance();
        vehicle.updateMetersOffset(routeDistance);

        try
        {
            Thread.sleep(500);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        vehicle.update();

        assertTrue(vehicle.isPositionLimited(), "Vehicle should be limited at the end of the route.");
        assertEquals(routeDistance, vehicle.getMetersOffset(), "Vehicle offset should match the route distance.");
    }

    @Test
    void testBearingAdjustment()
    {
        // Simulate a bearing adjustment
        double initialBearing = vehicle.getDegBearing();
        vehicle.updateMetersOffset(100.0); // Move slightly along the route
        vehicle.update();

        assertNotEquals(initialBearing, vehicle.getDegBearing(), "Bearing should be adjusted during update.");
    }

    @Test
    void testSpeedAdjustment()
    {
        vehicle.updateMetersOffset(0.0);

        try
        {
            Thread.sleep(500);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        double initialSpeed = vehicle.getMetersPerSecond();
        vehicle.update();

        assertNotEquals(initialSpeed, vehicle.getMetersPerSecond(), "Speed should change during updates.");
    }
}
