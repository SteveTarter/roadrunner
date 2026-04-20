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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // Target interval between vehicle updates.
    @Value("${com.tarterware.roadrunner.vehicle-update-period:250ms}")
    private String msUpdatePeriodString;

    // Update period in milliseconds.
    private long msUpdatePeriod;

    private int partitionCount;
    private List<TopicPartition> topicPartitions;

    private final KafkaTopicMetadataService kafkaTopicMetadataService;
    private final ConsumerFactory<String, VehiclePositionEvent> consumerFactory;

    // The long-lived "hot" resource
    private Consumer<String, VehiclePositionEvent> playbackConsumer;

    private static final String UNSET_VALUE = "Unset";

    private static final Logger logger = LoggerFactory.getLogger(PlaybackController.class);

    public PlaybackController(
            KafkaTopicMetadataService kafkaTopicMetadataService,
            ConsumerFactory<String, VehiclePositionEvent> consumerFactory)
    {
        this.kafkaTopicMetadataService = kafkaTopicMetadataService;
        this.consumerFactory = consumerFactory;
    }

    @PostConstruct
    public void init()
    {
        // DurationStyle.detect() determines the style (e.g., SIMPLE, ISO-8601) based on
        // the string
        Duration duration = DurationStyle.detect(msUpdatePeriodString).parse(msUpdatePeriodString);
        msUpdatePeriod = duration.toMillis();

        partitionCount = kafkaTopicMetadataService.getPartitionCount(topicName);

        logger.info("{} topic has {} partitions.", topicName, partitionCount);
        logger.info("Update period is {} ms.", msUpdatePeriod);

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

        long startTime = endTime - (2 * msUpdatePeriod);

        Map<UUID, VehicleState> latestStates = new HashMap<>();

        // CRITICAL: Kafka Consumer is not thread-safe.
        // We must synchronize to allow multiple users to query playback without
        // crashing.
        synchronized (playbackConsumer)
        {
            // Map startTime to Kafka offsets
            Map<TopicPartition, Long> timestampsToSearch = topicPartitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> startTime));

            Map<TopicPartition, OffsetAndTimestamp> offsets = playbackConsumer.offsetsForTimes(timestampsToSearch);

            // Seek each partition to the found offset
            offsets.forEach((tp, offsetAndTimestamp) ->
            {
                if (offsetAndTimestamp != null)
                {
                    playbackConsumer.seek(tp, offsetAndTimestamp.offset());
                }
                else
                {
                    // If timestamp is newer than latest record, seek to end
                    playbackConsumer.seekToEnd(Collections.singleton(tp));
                }
            });

            // Poll and filter records within the [startTime, endTime] window
            boolean processing = true;
            int emptyPolls = 0;

            while (processing && emptyPolls < 2)
            {
                ConsumerRecords<String, VehiclePositionEvent> records = playbackConsumer.poll(Duration.ofMillis(100));
                if (records.isEmpty())
                {
                    emptyPolls++;
                    continue;
                }

                for (ConsumerRecord<String, VehiclePositionEvent> record : records)
                {
                    if (record.timestamp() > endTime)
                    {
                        processing = false; // We've moved past our window
                        break;
                    }

                    mapToLatestState(record.value(), latestStates);
                }
            }
        }

        return buildPagedResponse(latestStates, page, pageSize);
    }

    private void mapToLatestState(VehiclePositionEvent event, Map<UUID, VehicleState> latestStates)
    {
        VehicleState vehicleState = new VehicleState();

        vehicleState.setId(UUID.fromString(event.vehicleId()));
        vehicleState.setPositionLimited(event.positionLimited());
        vehicleState.setPositionValid(event.positionValid());
        vehicleState.setDegLatitude(event.latitude());
        vehicleState.setDegLongitude(event.longitude());
        vehicleState.setDegBearing(event.heading());
        vehicleState.setMetersPerSecond(event.speed());
        vehicleState.setColorCode(event.colorCode());
        vehicleState.setManagerHost(event.managerHost());
        vehicleState.setMsEpochLastRun(event.eventTime().toEpochMilli());
        vehicleState.setNsLastExec(event.nsLastExec());

        latestStates.put(vehicleState.getId(), vehicleState);
    }

    private ResponseEntity<PagedModel<VehicleState>> buildPagedResponse(
            Map<UUID, VehicleState> states,
            int page,
            int pageSize)
    {
        List<VehicleState> list = new ArrayList<>(states.values());
        Page<VehicleState> vehicleStatePage = new PageImpl<>(list, PageRequest.of(page, pageSize), list.size());
        return new ResponseEntity<>(PagedModel.of(vehicleStatePage.getContent(),
                new PagedModel.PageMetadata(pageSize, page, list.size())), HttpStatus.OK);
    }
}
