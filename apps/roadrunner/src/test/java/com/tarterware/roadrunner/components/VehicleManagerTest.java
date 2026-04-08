package com.tarterware.roadrunner.components;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import com.tarterware.roadrunner.configs.NoOpSchedulerConfig;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.ports.RunnerVehicleStateStore;
import com.tarterware.roadrunner.ports.TripPlanRepository;
import com.tarterware.roadrunner.ports.VehicleEventPublisher;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.utils.TestUtils;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@Import(NoOpSchedulerConfig.class)
class VehicleManagerTest
{
    @Mock
    private DirectionsService directionsService;

    @Mock
    private RunnerVehicleStateStore vehicleStateStore;

    @Mock
    private TripPlanRepository tripPlanRepository;

    @Mock
    private VehicleEventPublisher vehicleEventPublisher;

    private MeterRegistry meterRegistry;

    private Vehicle vehicle;

    private VehicleManager vehicleManager;

    private TripPlan mockTripPlan;

    private Directions mockDirections;

    @Autowired
    private Environment environment;

    @Test
    void shouldPublishEventWhenVehicleCreated() throws IOException
    {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Mock DirectionsService
        mockTripPlan = mock(TripPlan.class);
        mockDirections = TestUtils.loadMockDirections("src/test/resources/test_directions.json");

        when(directionsService.getDirectionsForTripPlan(any())).thenReturn(mockDirections);

        meterRegistry = new SimpleMeterRegistry();

        vehicleManager = new VehicleManager(directionsService, (RunnerVehicleStateStore) vehicleStateStore,
                tripPlanRepository,
                vehicleEventPublisher, meterRegistry, environment);

        // Create the vehicle
        vehicle = vehicleManager.createVehicle(mockTripPlan);

        // Verify the abstraction was called
        verify(vehicleEventPublisher, times(1)).publishVehicleCreated(vehicle);
    }
}