package com.tarterware.roadrunner.adapters.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.tarterware.roadrunner.messaging.VehicleCreationRequestEvent;
import com.tarterware.roadrunner.messaging.VehicleSimulationStartEvent;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.ports.TripPlanRepository;
import com.tarterware.roadrunner.services.DirectionsService;

@ExtendWith(MockitoExtension.class)
public class VehicleCreationRequestConsumerTest
{
    @Mock
    private DirectionsService directionsService;

    @Mock
    private TripPlanRepository tripPlanRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private VehicleCreationRequestConsumer consumer;

    @Test
    void shouldSuccessfullyProcessCreationRequest()
    {
        TripPlan tripPlan = new TripPlan();
        tripPlan.setListStops(new ArrayList<>());
        VehicleCreationRequestEvent event = new VehicleCreationRequestEvent(
                "vehicle-123", "user@test.com", tripPlan, Instant.now());

        Directions directions = new Directions();
        
        when(tripPlanRepository.getTripPlan("vehicle-123")).thenReturn(tripPlan);
        when(directionsService.getDirectionsForTripPlan(tripPlan)).thenReturn(directions);

        consumer.receive(event);

        verify(kafkaTemplate).send(any(), eq("vehicle-123"), any(VehicleSimulationStartEvent.class));
    }

    @Test
    void shouldDegradeGracefullyAndUsePayloadTripPlanWhenRedisFails()
    {
        TripPlan tripPlan = new TripPlan();
        tripPlan.setListStops(new ArrayList<>());
        VehicleCreationRequestEvent event = new VehicleCreationRequestEvent(
                "vehicle-123", "user@test.com", tripPlan, Instant.now());

        Directions directions = new Directions();

        // Simulate Redis failure
        when(tripPlanRepository.getTripPlan("vehicle-123")).thenThrow(new RuntimeException("Redis connection error"));
        when(directionsService.getDirectionsForTripPlan(tripPlan)).thenReturn(directions);

        consumer.receive(event);

        verify(kafkaTemplate).send(any(), eq("vehicle-123"), any(VehicleSimulationStartEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenDirectionsServiceFails()
    {
        TripPlan tripPlan = new TripPlan();
        tripPlan.setListStops(new ArrayList<>());
        VehicleCreationRequestEvent event = new VehicleCreationRequestEvent(
                "vehicle-123", "user@test.com", tripPlan, Instant.now());

        when(tripPlanRepository.getTripPlan("vehicle-123")).thenReturn(tripPlan);
        when(directionsService.getDirectionsForTripPlan(tripPlan)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> consumer.receive(event));
    }
}
