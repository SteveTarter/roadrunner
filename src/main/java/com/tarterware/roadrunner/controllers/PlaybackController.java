package com.tarterware.roadrunner.controllers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@RestController
@RequestMapping("/api/playback")
public class PlaybackController
{
    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topicName;

    private int partitionCount;
    private List<TopicPartition> topicPartitions;

    private final KafkaTopicMetadataService kafkaTopicMetadataService;
    private final ConsumerFactory<String, VehiclePositionEvent> consumerFactory;
    private final StreamsBuilderFactoryBean streamsFactory;

    // The long-lived "hot" resource
    private Consumer<String, VehiclePositionEvent> playbackConsumer;

    private static final String UNSET_VALUE = "Unset";

    private static final Logger logger = LoggerFactory.getLogger(PlaybackController.class);

    public PlaybackController(
            KafkaTopicMetadataService kafkaTopicMetadataService,
            ConsumerFactory<String, VehiclePositionEvent> consumerFactory,
            StreamsBuilderFactoryBean streamsFactory)
    {
        this.kafkaTopicMetadataService = kafkaTopicMetadataService;
        this.consumerFactory = consumerFactory;
        this.streamsFactory = streamsFactory;
    }

    @PostConstruct
    public void init()
    {
        partitionCount = kafkaTopicMetadataService.getPartitionCount(topicName);

        logger.info("{} topic has {} partitions.", topicName, partitionCount);

        // Pre-cache the partition objects
        this.topicPartitions = IntStream.range(0, partitionCount)
                .mapToObj(p -> new TopicPartition(topicName, p))
                .collect(Collectors.toList());

        // Initialize the consumer immediately
        this.playbackConsumer = consumerFactory.createConsumer();

        // Manually assign partitions to avoid rebalance overhead
        this.playbackConsumer.assign(topicPartitions);

        logger.info("PlaybackController initialized. Consumer is 'hot' for topic {} with {} partitions.", topicName,
                partitionCount);
    }

    @PreDestroy
    public void tearDown()
    {
        if (playbackConsumer != null)
        {
            playbackConsumer.close();
            logger.info("Playback Consumer closed.");
        }
    }

    @GetMapping("/state")
    ResponseEntity<PagedModel<VehicleState>> getVehicleStatesAtTimestamp(
            @RequestParam(defaultValue = UNSET_VALUE)
            String timestamp,
            @RequestParam(defaultValue = "0")
            int page,
            @RequestParam(defaultValue = "10")
            int pageSize)
    {
        long endTime = timestamp.equals(UNSET_VALUE)
                ? Instant.now().toEpochMilli()
                : Instant.parse(timestamp).toEpochMilli();

        // Expand the window 2 seconds back
        long startTime = endTime - 2000;

        // Calculate the point in time that the stream runs out
        long hotThreshold = Instant.now().minus(Duration.ofHours(1)).toEpochMilli();

        if (endTime > hotThreshold)
        {
            return queryHotStore(startTime, endTime, page, pageSize);
        }
        else
        {
            return queryColdStore(startTime, endTime, page, pageSize);
        }
    }

    private ResponseEntity<PagedModel<VehicleState>> queryColdStore(
            long startTime,
            long endTime,
            int page,
            int pageSize)
    {
        Map<UUID, VehicleState> latestStates = new HashMap<>();
        // CRITICAL: Kafka Consumer is not thread-safe.
        // We must synchronize to allow multiple users to query playback without
        // crashing.
        synchronized (playbackConsumer)
        {
            // Pause all partitions to stop any background fetching
            // playbackConsumer.pause(topicPartitions);

            // Map startTime to Kafka offsets
            Map<TopicPartition, Long> timestampsToSearch = topicPartitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> startTime));

            Map<TopicPartition, OffsetAndTimestamp> offsets = playbackConsumer.offsetsForTimes(timestampsToSearch);

            // Seek each partition to the found offset
            Map<TopicPartition, Boolean> partitionDone = new HashMap<>();
            for (TopicPartition tp : topicPartitions)
            {
                OffsetAndTimestamp oat = offsets.get(tp);
                if (oat != null)
                {
                    playbackConsumer.seek(tp, oat.offset());
                    partitionDone.put(tp, false);
                }
                else
                {
                    // No record at or after startTime in this partition
                    playbackConsumer.seekToEnd(Collections.singleton(tp));
                    partitionDone.put(tp, true);
                }
            }

            // Resume the partitions - THIS triggers a fresh fetch request to the broker
            // playbackConsumer.resume(topicPartitions);

            long timeoutDeadline = System.currentTimeMillis() + 1000;

            while (partitionDone.values().stream().anyMatch(done -> !done)
                    && System.currentTimeMillis() < timeoutDeadline)
            {
                // Give broker 100ms to respond.
                ConsumerRecords<String, VehiclePositionEvent> records = playbackConsumer.poll(Duration.ofMillis(20));

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

        return buildPagedResponse(latestStates, page, pageSize);
    }

    private ResponseEntity<PagedModel<VehicleState>> queryHotStore(
            long startTime,
            long endTime,
            int page,
            int pageSize)
    {
        KafkaStreams streams = streamsFactory.getKafkaStreams();
        ReadOnlyWindowStore<String, VehiclePositionEvent> store = streams.store(
                StoreQueryParameters.fromNameAndType("hot-playback-store", QueryableStoreTypes.windowStore()));

        Map<UUID, VehicleState> latestStates = new HashMap<>();

        // Fetch all records in the 2-period window
        try (KeyValueIterator<Windowed<String>, VehiclePositionEvent> iterator = store
                .fetchAll(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime)))
        {

            while (iterator.hasNext())
            {
                var next = iterator.next();
                // Map to VehicleState using your existing helper method
                mapToLatestState(next.value, latestStates);
            }
        }

        return buildPagedResponse(latestStates, page, pageSize);
    }

    private void mapToLatestState(VehiclePositionEvent event, Map<UUID, VehicleState> latestStates)
    {
        UUID id = UUID.fromString(event.vehicleId());
        long eventMillis = event.eventTime().toEpochMilli();

        VehicleState existing = latestStates.get(id);
        if (existing != null && existing.getMsEpochLastRun() >= eventMillis)
        {
            return;
        }

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

        latestStates.put(id, vehicleState);
    }

    private ResponseEntity<PagedModel<VehicleState>> buildPagedResponse(
            Map<UUID, VehicleState> states,
            int page,
            int pageSize)
    {
        List<VehicleState> list = new ArrayList<>(states.values());

        int start = Math.min(page * pageSize, list.size());
        int end = Math.min(start + pageSize, list.size());
        List<VehicleState> pageContent = list.subList(start, end);

        Page<VehicleState> vehicleStatePage = new PageImpl<>(pageContent, PageRequest.of(page, pageSize), list.size());

        return new ResponseEntity<>(
                PagedModel.of(
                        vehicleStatePage.getContent(),
                        new PagedModel.PageMetadata(pageSize, page, list.size())),
                HttpStatus.OK);
    }
}
