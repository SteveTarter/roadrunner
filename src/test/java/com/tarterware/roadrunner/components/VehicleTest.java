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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.services.DirectionsService;

import io.micrometer.core.instrument.MeterRegistry;
import utils.TestUtils;

class VehicleTest
{
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private MeterRegistry meterRegistry;

    private Vehicle vehicle;
    private VehicleManager vehicleManager;
    private DirectionsService mockDirectionsService;
    private TripPlan mockTripPlan;
    private Directions mockDirections;

    @BeforeEach
    void setup() throws IOException
    {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Mock behavior for RedisTemplate
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Mock ZSet behavior
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(anyString(), any(), anyDouble())).thenReturn(true);

        // Mock Set behavior
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add(anyString(), any())).thenReturn(1L);

        mockDirectionsService = mock(DirectionsService.class);

        mockTripPlan = mock(TripPlan.class);

        // Load mock Directions from the file
        mockDirections = TestUtils.loadMockDirections("src/test/resources/test_directions.json");

        // Stub the DirectionsService methods
        when(mockDirectionsService.getDirectionsForTripPlan(any())).thenReturn(mockDirections);

        // Create the VehicleManager instance
        vehicleManager = new VehicleManager(mockDirectionsService, redisTemplate, meterRegistry);

        vehicle = vehicleManager.createVehicle(mockTripPlan);
        UUID vehicleId = vehicle.getId();
        when(valueOperations.get("Vehicle:" + vehicleId)).thenReturn(vehicle);

        // vehicle = vehicleManager.getVehicle(vehicleId);
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
            Thread.sleep(100);
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
            Thread.sleep(100);
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
