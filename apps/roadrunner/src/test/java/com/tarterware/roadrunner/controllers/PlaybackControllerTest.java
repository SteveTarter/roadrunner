package com.tarterware.roadrunner.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.tarterware.roadrunner.adapters.kafka.KafkaControllerVehicleStateStore;
import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

@ExtendWith(MockitoExtension.class)
class PlaybackControllerTest
{
    private static final String TOPIC = "vehicle-position-topic";

    @Mock
    private KafkaTopicMetadataService kafkaTopicMetadataService;

    @Mock
    private ConsumerFactory<String, VehiclePositionEvent> consumerFactory;

    @Mock
    private KafkaControllerVehicleStateStore vehicleStateStore;

    @Mock
    private Consumer<String, VehiclePositionEvent> playbackConsumer;

    private PlaybackController controller;

    @BeforeEach
    void setUp()
    {
        controller = new PlaybackController(
                kafkaTopicMetadataService,
                consumerFactory,
                vehicleStateStore);

        ReflectionTestUtils.setField(controller, "topicName", TOPIC);
    }

    @Test
    void init_shouldCreateConsumerAssignAllPartitions()
    {
        when(kafkaTopicMetadataService.getPartitionCount(TOPIC)).thenReturn(3);
        when(consumerFactory.createConsumer()).thenReturn(playbackConsumer);

        controller.init();

        @SuppressWarnings("unchecked")
        List<TopicPartition> assigned = (List<TopicPartition>) ReflectionTestUtils.getField(controller,
                "topicPartitions");
        Integer partitionCount = (Integer) ReflectionTestUtils.getField(controller, "partitionCount");

        assertNotNull(assigned);
        assertEquals(3, partitionCount);
        assertEquals(3, assigned.size());
        assertEquals(new TopicPartition(TOPIC, 0), assigned.get(0));
        assertEquals(new TopicPartition(TOPIC, 1), assigned.get(1));
        assertEquals(new TopicPartition(TOPIC, 2), assigned.get(2));

        verify(playbackConsumer).assign(assigned);
    }

    @Test
    void tearDown_shouldCloseConsumerWhenPresent()
    {
        when(kafkaTopicMetadataService.getPartitionCount(TOPIC)).thenReturn(1);
        when(consumerFactory.createConsumer()).thenReturn(playbackConsumer);
        controller.init();

        controller.tearDown();

        verify(playbackConsumer).close();
    }

    @Test
    void getVehicleStatesAtTimestamp_shouldUseHotStoreForRecentTimestamp()
    {
        when(kafkaTopicMetadataService.getPartitionCount(TOPIC)).thenReturn(1);
        when(consumerFactory.createConsumer()).thenReturn(playbackConsumer);
        controller.init();

        UUID vehicleId = UUID.randomUUID();
        Instant now = Instant.now();

        VehicleState vehicleState = new VehicleState();
        vehicleState.setId(vehicleId);
        vehicleState.setPositionLimited(false);
        vehicleState.setPositionValid(true);
        vehicleState.setDegLatitude(32.75);
        vehicleState.setDegLongitude(-97.33);
        vehicleState.setDegBearing(45.0);
        vehicleState.setMetersPerSecond(8.2);
        vehicleState.setColorCode("#00FF00");
        vehicleState.setManagerHost("host-a");
        vehicleState.setMsEpochLastRun(now.toEpochMilli());

        Map<UUID, VehicleState> vehicleMap = new HashMap<UUID, VehicleState>();
        vehicleMap.put(vehicleId, vehicleState);

        when(vehicleStateStore.getVehicles(any())).thenReturn(vehicleMap);

        ResponseEntity<PagedModel<VehicleState>> response = controller.getVehicleStatesAtTimestamp("Unset", 0,
                10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getContent().size());

        VehicleState state = response.getBody().getContent().iterator().next();
        assertEquals(vehicleId, state.getId());
        assertEquals(now.toEpochMilli(), state.getMsEpochLastRun());
        assertEquals("#00FF00", state.getColorCode());
    }

    @Test
    void getVehicleStatesAtTimestamp_shouldUseColdStoreForOldTimestamp()
    {
        when(kafkaTopicMetadataService.getPartitionCount(TOPIC)).thenReturn(2);
        when(consumerFactory.createConsumer()).thenReturn(playbackConsumer);
        controller.init();

        Instant endTime = Instant.now().minusSeconds(7200);
        long startMillis = endTime.toEpochMilli() - 2000;

        TopicPartition tp0 = new TopicPartition(TOPIC, 0);
        TopicPartition tp1 = new TopicPartition(TOPIC, 1);

        Map<TopicPartition, OffsetAndTimestamp> offsets = Map.of(
                tp0, new OffsetAndTimestamp(100L, startMillis),
                tp1, new OffsetAndTimestamp(200L, startMillis));

        when(playbackConsumer.offsetsForTimes(anyMap())).thenReturn(offsets);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        ConsumerRecord<String, VehiclePositionEvent> r1 = record(tp0, 100L, startMillis + 500,
                event(id1, Instant.ofEpochMilli(startMillis + 500), 32.70, -97.30, 10.0, 5.5, "#111111", "host-1",
                        100L, "MOVING"));

        ConsumerRecord<String, VehiclePositionEvent> r2 = record(tp1, 200L, startMillis + 1200,
                event(id2, Instant.ofEpochMilli(startMillis + 1200), 32.71, -97.31, 20.0, 6.5, "#222222", "host-2",
                        200L, "MOVING"));

        ConsumerRecord<String, VehiclePositionEvent> r3 = record(tp0, 101L, endTime.toEpochMilli() + 1,
                event(UUID.randomUUID(), Instant.ofEpochMilli(endTime.toEpochMilli() + 1), 0, 0, 0, 0, "#000000",
                        "done", 0L, "MOVING"));

        ConsumerRecord<String, VehiclePositionEvent> r4 = record(tp1, 201L, endTime.toEpochMilli() + 1,
                event(UUID.randomUUID(), Instant.ofEpochMilli(endTime.toEpochMilli() + 1), 0, 0, 0, 0, "#000000",
                        "done", 0L, "MOVING"));

        when(playbackConsumer.poll(any())).thenReturn(records(Map.of(
                tp0, List.of(r1, r3),
                tp1, List.of(r2, r4))));

        ResponseEntity<PagedModel<VehicleState>> response = controller.getVehicleStatesAtTimestamp(endTime.toString(),
                0, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getContent().size());

        verify(playbackConsumer).offsetsForTimes(anyMap());
        verify(playbackConsumer).seek(eq(tp0), eq(100L));
        verify(playbackConsumer).seek(eq(tp1), eq(200L));
        verify(playbackConsumer, never()).seekToEnd(any());
        verify(playbackConsumer, times(1)).poll(any());
    }

    @Test
    void coldStore_shouldUseLatestEventPerVehicleId()
    {
        when(kafkaTopicMetadataService.getPartitionCount(TOPIC)).thenReturn(1);
        when(consumerFactory.createConsumer()).thenReturn(playbackConsumer);
        controller.init();

        Instant endTime = Instant.now().minusSeconds(7200);
        long startMillis = endTime.toEpochMilli() - 2000;
        TopicPartition tp0 = new TopicPartition(TOPIC, 0);

        when(playbackConsumer.offsetsForTimes(anyMap())).thenReturn(
                Map.of(tp0, new OffsetAndTimestamp(10L, startMillis)));

        UUID vehicleId = UUID.randomUUID();

        ConsumerRecord<String, VehiclePositionEvent> older = record(tp0, 10L, startMillis + 500,
                event(vehicleId, Instant.ofEpochMilli(startMillis + 500), 32.70, -97.30, 10.0, 4.0, "#AAAAAA",
                        "old-host", 111L, "MOVING"));

        ConsumerRecord<String, VehiclePositionEvent> newer = record(tp0, 11L, startMillis + 1500,
                event(vehicleId, Instant.ofEpochMilli(startMillis + 1500), 32.80, -97.40, 20.0, 9.0, "#BBBBBB",
                        "new-host", 222L, "MOVING"));

        ConsumerRecord<String, VehiclePositionEvent> stop = record(tp0, 12L, endTime.toEpochMilli() + 1,
                event(UUID.randomUUID(), Instant.ofEpochMilli(endTime.toEpochMilli() + 1), 0, 0, 0, 0, "#000000",
                        "done", 0L, "MOVING"));

        when(playbackConsumer.poll(any())).thenReturn(records(Map.of(tp0, List.of(older, newer, stop))));

        ResponseEntity<PagedModel<VehicleState>> response = controller.getVehicleStatesAtTimestamp(endTime.toString(),
                0, 10);

        assertEquals(1, response.getBody().getContent().size());

        VehicleState state = response.getBody().getContent().iterator().next();
        assertEquals(vehicleId, state.getId());
        assertEquals(startMillis + 1500, state.getMsEpochLastRun());
        assertEquals(32.80, state.getDegLatitude());
        assertEquals(-97.40, state.getDegLongitude());
        assertEquals(9.0, state.getMetersPerSecond());
        assertEquals("#BBBBBB", state.getColorCode());
        assertEquals("new-host", state.getManagerHost());
        assertEquals(222L, state.getNsLastExec());
    }

    @Test
    void buildPagedResponse_shouldReturnOnlyRequestedPageContent()
    {
        when(kafkaTopicMetadataService.getPartitionCount(TOPIC)).thenReturn(1);
        when(consumerFactory.createConsumer()).thenReturn(playbackConsumer);
        controller.init();

        Instant now = Instant.now();

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        Map<UUID, VehicleState> vehicleMap = new HashMap<UUID, VehicleState>();

        VehicleState vehicleState1 = new VehicleState();
        vehicleState1.setId(id1);
        vehicleState1.setPositionLimited(false);
        vehicleState1.setPositionValid(true);
        vehicleState1.setDegLatitude(32.75);
        vehicleState1.setDegLongitude(-97.33);
        vehicleState1.setDegBearing(45.0);
        vehicleState1.setMetersPerSecond(8.2);
        vehicleState1.setColorCode("#00FF00");
        vehicleState1.setManagerHost("host-a");
        vehicleState1.setMsEpochLastRun(now.toEpochMilli() - 1500);
        vehicleMap.put(id1, vehicleState1);

        VehicleState vehicleState2 = new VehicleState();
        vehicleState2.setId(id2);
        vehicleState2.setPositionLimited(false);
        vehicleState2.setPositionValid(true);
        vehicleState2.setDegLatitude(32.76);
        vehicleState2.setDegLongitude(-97.34);
        vehicleState2.setDegBearing(45.0);
        vehicleState2.setMetersPerSecond(8.2);
        vehicleState2.setColorCode("#FF0000");
        vehicleState2.setManagerHost("host-b");
        vehicleState2.setMsEpochLastRun(now.toEpochMilli() - 100);
        vehicleMap.put(id2, vehicleState2);

        VehicleState vehicleState3 = new VehicleState();
        vehicleState3.setId(id3);
        vehicleState3.setPositionLimited(false);
        vehicleState3.setPositionValid(true);
        vehicleState3.setDegLatitude(32.76);
        vehicleState3.setDegLongitude(-97.34);
        vehicleState3.setDegBearing(45.0);
        vehicleState3.setMetersPerSecond(8.2);
        vehicleState3.setColorCode("#FF0000");
        vehicleState3.setManagerHost("host-b");
        vehicleState3.setMsEpochLastRun(now.toEpochMilli() - 50);
        vehicleMap.put(id3, vehicleState3);

        when(vehicleStateStore.getVehicles(any())).thenReturn(vehicleMap);

        ResponseEntity<PagedModel<VehicleState>> response = controller.getVehicleStatesAtTimestamp("Unset", 1,
                2);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        // total = 3, page size = 2, page 1 should contain the remaining 1 item
        assertEquals(1, response.getBody().getContent().size());
        assertEquals(2, response.getBody().getMetadata().getSize());
        assertEquals(1, response.getBody().getMetadata().getNumber());
        assertEquals(3, response.getBody().getMetadata().getTotalElements());
        assertEquals(2, response.getBody().getMetadata().getTotalPages());
    }

    @Test
    void coldStore_shouldSeekToEndWhenOffsetForTimeMissing()
    {
        when(kafkaTopicMetadataService.getPartitionCount(TOPIC)).thenReturn(2);
        when(consumerFactory.createConsumer()).thenReturn(playbackConsumer);
        controller.init();

        Instant endTime = Instant.now().minusSeconds(7200);
        long startMillis = endTime.toEpochMilli() - 2000;

        TopicPartition tp0 = new TopicPartition(TOPIC, 0);
        TopicPartition tp1 = new TopicPartition(TOPIC, 1);

        Map<TopicPartition, OffsetAndTimestamp> offsets = new LinkedHashMap<>();
        offsets.put(tp0, new OffsetAndTimestamp(100L, startMillis));
        offsets.put(tp1, null);

        when(playbackConsumer.offsetsForTimes(anyMap())).thenReturn(offsets);

        ConsumerRecord<String, VehiclePositionEvent> r1 = record(tp0, 100L, endTime.toEpochMilli() + 1,
                event(UUID.randomUUID(), Instant.ofEpochMilli(endTime.toEpochMilli() + 1), 0, 0, 0, 0, "#000000",
                        "done", 0L, "MOVING"));

        when(playbackConsumer.poll(any())).thenReturn(records(Map.of(tp0, List.of(r1))));

        ResponseEntity<PagedModel<VehicleState>> response = controller.getVehicleStatesAtTimestamp(endTime.toString(),
                0, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getContent().isEmpty());

        verify(playbackConsumer).seek(eq(tp0), eq(100L));
        verify(playbackConsumer).seekToEnd(argThat(partitions -> partitions.size() == 1 && partitions.contains(tp1)));
    }

    private VehiclePositionEvent event(
            UUID id,
            Instant eventTime,
            double latitude,
            double longitude,
            double heading,
            double speed,
            String colorCode,
            String managerHost,
            long nsLastExec,
            String status)
    {
        return new VehiclePositionEvent(
                id.toString(),
                eventTime,
                eventTime.toEpochMilli(),
                nsLastExec,
                true,
                false,
                latitude,
                longitude,
                heading,
                speed,
                colorCode,
                managerHost,
                status);
    }

    @SuppressWarnings("deprecation")
    private ConsumerRecord<String, VehiclePositionEvent> record(
            TopicPartition tp,
            long offset,
            long timestamp,
            VehiclePositionEvent event)
    {
        return new ConsumerRecord<String, VehiclePositionEvent>(
                tp.topic(),
                tp.partition(),
                offset,
                timestamp,
                TimestampType.CREATE_TIME,
                0L,
                0,
                0,
                null,
                event,
                new RecordHeaders(),
                Optional.empty());
    }

    private ConsumerRecords<String, VehiclePositionEvent> records(
            Map<TopicPartition, List<ConsumerRecord<String, VehiclePositionEvent>>> byPartition)
    {
        return new ConsumerRecords<>(byPartition);
    }
}