package com.tarterware.roadrunner.components;

import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.services.DirectionsService;

import utils.TestUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleTest {

    private Vehicle vehicle;
    private DirectionsService mockDirectionsService;
    private TripPlan mockTripPlan;
    private Directions mockDirections;

    @BeforeEach
    void setup() throws IOException {
        mockDirectionsService = mock(DirectionsService.class);

        mockTripPlan = mock(TripPlan.class);

        // Load mock Directions from the file
        mockDirections = TestUtils.loadMockDirections("src/test/resources/test_directions.json");

        // Stub the DirectionsService methods
        when(mockDirectionsService.getDirectionsForTripPlan(any())).thenReturn(mockDirections);

        // Create the Vehicle instance
        vehicle = new Vehicle(mockDirectionsService);
    }

    @Test
    void testSetTripPlan_validTripPlan() {
        // Test setting a trip plan
        vehicle.setTripPlan(mockTripPlan);
        assertNotNull(vehicle.getTripPlan(), "Trip plan should be set.");
        assertEquals(0.0, vehicle.getMetersOffset(), "Vehicle offset should be initialized to 0.");
    }

    @Test
    void testSetTripPlan_nullTripPlan() {
        assertThrows(IllegalArgumentException.class, () -> vehicle.setTripPlan(null),
                "Setting a null trip plan should throw an exception.");
    }

    @Test
    void testUpdate_reachesEndOfRoute() {
        vehicle.setTripPlan(mockTripPlan);

        // Simulate updates that move the vehicle to the end of the route
        double routeDistance = mockDirections.getRoutes().get(0).getDistance();
        vehicle.setMetersOffset(routeDistance);

        try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

        vehicle.update();

        assertTrue(vehicle.isPositionLimited(), "Vehicle should be limited at the end of the route.");
        assertEquals(routeDistance, vehicle.getMetersOffset(), "Vehicle offset should match the route distance.");
    }

    @Test
    void testBearingAdjustment() {
        vehicle.setTripPlan(mockTripPlan);

        // Simulate a bearing adjustment
        double initialBearing = vehicle.getDegBearing();
        vehicle.setMetersOffset(100.0); // Move slightly along the route
        vehicle.update();

        assertNotEquals(initialBearing, vehicle.getDegBearing(), "Bearing should be adjusted during update.");
    }

    @Test
    void testSpeedAdjustment() {
        vehicle.setTripPlan(mockTripPlan);

        vehicle.setMetersOffset(0.0);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        double initialSpeed = vehicle.getMetersPerSecond();
        vehicle.update();

        assertNotEquals(initialSpeed, vehicle.getMetersPerSecond(), "Speed should change during updates.");
    }
}
