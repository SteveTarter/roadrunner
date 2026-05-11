package com.tarterware.roadrunner.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.models.SimulationSession;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;
import com.tarterware.roadrunner.ports.SimulationRegistry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class MaintenanceService
{
    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final SimulationRegistry registry;
    private final ControllerVehicleStateStore hotStore;
    private final ConsumerFactory<String, VehiclePositionEvent> consumerFactory;
    private final KafkaTopicMetadataService metadataService;

    private int partitionCount;
    private List<TopicPartition> topicPartitions;
    private Consumer<String, VehiclePositionEvent> eventConsumer;

    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topicName;

    public MaintenanceService(
            SimulationRegistry registry,
            ControllerVehicleStateStore hotStore,
            ConsumerFactory<String, VehiclePositionEvent> consumerFactory,
            KafkaTopicMetadataService metadataService)
    {
        this.registry = registry;
        this.hotStore = hotStore;
        this.consumerFactory = consumerFactory;
        this.metadataService = metadataService;
    }

    @PostConstruct
    public void init()
    {
        partitionCount = metadataService.getPartitionCount(topicName);

        log.info("{} topic has {} partitions.", topicName, partitionCount);

        // Pre-cache the partition objects
        this.topicPartitions = IntStream.range(0, partitionCount)
                .mapToObj(p -> new TopicPartition(topicName, p))
                .collect(Collectors.toList());

        // Initialize the consumer immediately
        this.eventConsumer = consumerFactory.createConsumer();

        // Manually assign partitions to avoid rebalance overhead
        this.eventConsumer.assign(topicPartitions);

        log.info("MaintenanceController initialized. Consumer is 'hot' for topic {} with {} partitions.", topicName,
                partitionCount);
    }

    @PreDestroy
    public void tearDown()
    {
        if (eventConsumer != null)
        {
            eventConsumer.close();
            log.info("Event Consumer closed.");
        }
    }

    @Async
    public void cleanseOrphanedSessions()
    {
        List<SimulationSession> orphans = registry.getAllSessions().stream()
                .filter(s -> s.getEnd() == null)
                .collect(Collectors.toList());

        log.info("Background thread started. Found {} potentially orphaned sessions.",
                orphans.size());

        for (SimulationSession session : orphans)
        {
            log.debug("Considering Vehicle {}.", session.getId());

            // Step 1: Check if it's currently in the memory store
            if (hotStore.getVehicle(session.getId()) != null)
            {
                log.debug("Vehicle {} is still active in memory. Skipping.", session.getId());
                continue;
            }

            // Step 2: Search Kafka for the last known position
            Instant finalTime = findLastEventTime(session);

            // Step 3: Record the end time
            registry.recordEnd(session.getId(), finalTime);
            log.info("Cleansed session for vehicle {} with end time {}.  Start time was {}.",
                    session.getId(), finalTime, session.getStart());
        }
    }

    private Instant findLastEventTime(SimulationSession session)
    {
        UUID targetId = session.getId();

        // Default to start time if no messages found
        Instant lastFoundTime = session.getStart();
        log.debug("Vehicle {} start time is {}.", session.getId(), session.getStart().toEpochMilli());

        // Grab 1 minute worth of data at a time. When we encounter a buffer without
        // the vehicle ID, the search is over.
        long windowSizeMs = 60000;
        long currentPointer = session.getStart().toEpochMilli();
        boolean searching = true;

        while (searching)
        {
            List<VehicleState> states = queryTopic(currentPointer, currentPointer + windowSizeMs);

            Optional<Long> latestInWindow = states.stream()
                    .filter(s -> s.getId().equals(targetId))
                    .map(VehicleState::getMsEpochLastRun)
                    .max(Long::compare);

            if (latestInWindow.isPresent())
            {
                lastFoundTime = Instant.ofEpochMilli(latestInWindow.get());
                currentPointer += windowSizeMs; // Move pointer forward
            }
            else
            {
                // If the vehicle isn't in this 1-minute window, it likely stopped in the
                // previous one
                searching = false;
            }

            // Safety break: Don't search into the future
            if (currentPointer > System.currentTimeMillis())
            {
                searching = false;
            }
        }

        return lastFoundTime;
    }

    private List<VehicleState> queryTopic(
            long startTime,
            long endTime)
    {
        List<VehicleState> latestStates = new ArrayList<>();
        // CRITICAL: Kafka Consumer is not thread-safe.
        // We must synchronize to allow multiple users to query playback without
        // crashing.
        synchronized (eventConsumer)
        {
            // Pause all partitions to stop any background fetching
            eventConsumer.pause(topicPartitions);

            // Map startTime to Kafka offsets
            Map<TopicPartition, Long> timestampsToSearch = topicPartitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> startTime));

            Map<TopicPartition, OffsetAndTimestamp> offsets = eventConsumer.offsetsForTimes(timestampsToSearch);

            // Seek each partition to the found offset
            Map<TopicPartition, Boolean> partitionDone = new HashMap<>();
            for (TopicPartition tp : topicPartitions)
            {
                OffsetAndTimestamp oat = offsets.get(tp);

                if (oat != null)
                {
                    eventConsumer.seek(tp, oat.offset());
                    partitionDone.put(tp, false);
                }
                else
                {
                    // No record at or after startTime in this partition
                    eventConsumer.seekToEnd(Collections.singleton(tp));
                    partitionDone.put(tp, true);
                }
            }

            // Resume the partitions - THIS triggers a fresh fetch request to the broker
            eventConsumer.resume(topicPartitions);

            long timeoutDeadline = System.currentTimeMillis() + 1000;

            while (partitionDone.values().stream().anyMatch(done -> !done)
                    && System.currentTimeMillis() < timeoutDeadline)
            {
                // Give broker 100ms to respond.
                ConsumerRecords<String, VehiclePositionEvent> records = eventConsumer
                        .poll(100);

                if (records.isEmpty())
                {
                    continue;
                }

                for (TopicPartition tp : records.partitions())
                {
                    if (partitionDone.getOrDefault(tp, false))
                    {
                        continue;
                    }

                    for (ConsumerRecord<String, VehiclePositionEvent> record : records.records(tp))
                    {
                        long recTs = record.timestamp();

                        // Check if this specific record is within the target window
                        if ((recTs >= startTime) && (recTs <= endTime))
                        {
                            mapToLatestState(record.value(), latestStates);
                        }
                        else
                        {
                            if (recTs > endTime)
                            {
                                partitionDone.put(tp, true);
                                break;
                            }
                        }
                    }
                }
            }
        }

        return latestStates;
    }

    private void mapToLatestState(VehiclePositionEvent event, List<VehicleState> latestStates)
    {
        UUID id = UUID.fromString(event.vehicleId());
        long eventMillis = event.eventTime().toEpochMilli();

        VehicleState vehicleState = new VehicleState();
        vehicleState.setId(id);
        vehicleState.setPositionLimited(event.positionLimited());
        vehicleState.setPositionValid(event.positionValid());
        vehicleState.setDegLatitude(event.latitude());
        vehicleState.setDegLongitude(event.longitude());
        vehicleState.setDegBearing(event.heading());
        vehicleState.setMetersPerSecond(event.speed());
        vehicleState.setColorCode(event.colorCode());
        vehicleState.setManagerHost(event.managerHost());
        vehicleState.setMsEpochLastRun(eventMillis);
        vehicleState.setNsLastExec(event.nsLastExec());

        latestStates.add(vehicleState);
    }

}