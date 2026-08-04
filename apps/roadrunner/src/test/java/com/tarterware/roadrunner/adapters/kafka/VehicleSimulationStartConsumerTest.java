package com.tarterware.roadrunner.adapters.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tarterware.roadrunner.messaging.VehicleSimulationStartEvent;
import com.tarterware.roadrunner.components.VehicleManager;
import com.tarterware.roadrunner.models.TripPlan;

@ExtendWith(MockitoExtension.class)
public class VehicleSimulationStartConsumerTest
{
    @Mock
    private VehicleManager vehicleManager;

    @InjectMocks
    private VehicleSimulationStartConsumer consumer;

    @Test
    void shouldSuccessfullyStartSimulation()
    {
        TripPlan tripPlan = new TripPlan();
        tripPlan.setListStops(new ArrayList<>());
        VehicleSimulationStartEvent event = new VehicleSimulationStartEvent(
                "vehicle-123", "user@test.com", tripPlan, "#FF0000", Instant.now());

        consumer.receive(event);

        verify(vehicleManager).startVehicleSimulation("vehicle-123", tripPlan, "#FF0000", "user@test.com");
    }

    @Test
    void shouldRethrowExceptionWhenManagerFails()
    {
        TripPlan tripPlan = new TripPlan();
        tripPlan.setListStops(new ArrayList<>());
        VehicleSimulationStartEvent event = new VehicleSimulationStartEvent(
                "vehicle-123", "user@test.com", tripPlan, "#FF0000", Instant.now());

        doThrow(new RuntimeException("Manager error")).when(vehicleManager)
                .startVehicleSimulation("vehicle-123", tripPlan, "#FF0000", "user@test.com");

        assertThrows(RuntimeException.class, () -> consumer.receive(event));
    }
}
