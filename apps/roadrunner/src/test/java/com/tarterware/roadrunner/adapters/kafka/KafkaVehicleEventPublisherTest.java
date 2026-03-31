package com.tarterware.roadrunner.adapters.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.tarterware.roadrunner.components.Vehicle;
import com.tarterware.roadrunner.components.VehicleManager;
import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.ports.TripPlanRepository;
import com.tarterware.roadrunner.ports.VehicleEventPublisher;
import com.tarterware.roadrunner.ports.VehicleStateStore;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.utils.TestUtils;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
public class KafkaVehicleEventPublisherTest
{
    @Mock
    private DirectionsService directionsService;

    @Mock
    private VehicleStateStore vehicleStateStore;

    @Mock
    private TripPlanRepository tripPlanRepository;

    @Mock
    private VehicleEventPublisher vehicleEventPublisher;

    @Mock
    private VehiclePositionEvent vehiclePositionEvent;

    private MeterRegistry meterRegistry;

    @Mock
    private VehicleStateStore stateStore;

    private Vehicle vehicle;

    private VehicleManager vehicleManager;

    private TripPlan mockTripPlan;

    private Directions mockDirections;

    @Autowired
    private Environment environment;

    @Mock
    private KafkaTemplate<String, VehiclePositionEvent> kafkaTemplate;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private KafkaVehicleEventPublisher kafkaPublisher;

    @Test
    void shouldMapVehicleToPositionEventCorrectly() throws IOException
    {
        // Mock DirectionsService
        mockTripPlan = mock(TripPlan.class);
        mockDirections = TestUtils.loadMockDirections("src/test/resources/test_directions.json");

        when(directionsService.getDirectionsForTripPlan(any())).thenReturn(mockDirections);

        // Set the topic name in the kafka publisher
        ReflectionTestUtils.setField(kafkaPublisher, "topicName", "vehicle.position.v1");

        // Return the mocked SendResult in the future
        when(kafkaTemplate.send(any(), any(), any(VehiclePositionEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        meterRegistry = new SimpleMeterRegistry();

        vehicleManager = new VehicleManager(directionsService, vehicleStateStore, tripPlanRepository,
                kafkaPublisher, meterRegistry, environment);

        // Create the vehicle
        vehicle = vehicleManager.createVehicle(mockTripPlan);

        // Capture the event sent to KafkaTemplate
        ArgumentCaptor<VehiclePositionEvent> eventCaptor = ArgumentCaptor.forClass(VehiclePositionEvent.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

        VehiclePositionEvent event = eventCaptor.getValue();
        assertEquals(vehicle.getId().toString(), event.vehicleId());
        assertEquals("CREATED", event.status());
        // Validates the sequence numbering task
        assertEquals(vehicle.getLastCalculationEpochMillis(), event.sequenceNumber());
    }
}
