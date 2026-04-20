package com.tarterware.roadrunner.controllers;

import java.time.Duration;
import java.time.Instant;
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

    private KafkaTopicMetadataService kafkaTopicMetadataService;

    private final ConsumerFactory<String, VehiclePositionEvent> consumerFactory;

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
        long endTime;
        if (timestamp.equals(UNSET_VALUE))
        {
            // If no end timestamp was specified, use the current time.
            endTime = Instant.now().toEpochMilli();
        }
        else
        {
            // Parse the given time
            endTime = Instant.parse(timestamp).toEpochMilli();
        }

        long startTime = endTime - (2 * msUpdatePeriod);

        List<TopicPartition> partitions = IntStream.range(0, partitionCount)
                .mapToObj(p -> new TopicPartition(topicName, p))
                .collect(Collectors.toList());

        Map<UUID, VehicleState> latestStates = new HashMap<>();

        // Open a manual consumer
        try (Consumer<String, VehiclePositionEvent> consumer = consumerFactory.createConsumer())
        {
            consumer.assign(partitions);

            // Map startTime to Kafka offsets
            Map<TopicPartition, Long> timestampsToSearch = partitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> startTime));

            Map<TopicPartition, OffsetAndTimestamp> offsets = consumer.offsetsForTimes(timestampsToSearch);

            // Seek each partition to the found offset
            offsets.forEach((tp, offsetAndTimestamp) ->
            {
                if (offsetAndTimestamp != null)
                {
                    consumer.seek(tp, offsetAndTimestamp.offset());
                }
                else
                {
                    // If timestamp is newer than latest record, seek to end
                    consumer.seekToEnd(Collections.singleton(tp));
                }
            });

            // Poll and filter records within the [startTime, endTime] window
            boolean processing = true;
            int emptyPolls = 0;

            while (processing && emptyPolls < 3)
            {
                ConsumerRecords<String, VehiclePositionEvent> records = consumer.poll(Duration.ofMillis(500));
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

                    // Convert VehiclePositionEvent to VehicleState
                    VehiclePositionEvent event = record.value();
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

                    // Overwrite with newer state for the same vehicle ID within the window
                    latestStates.put(vehicleState.getId(), vehicleState);
                }
            }
        }

        List<VehicleState> listVehicleStates = latestStates.values().stream()
                .collect(Collectors.toList());

        // ... Pagination logic (sublist based on page/pageSize) ...
        // Create a Page object
        Page<VehicleState> vehicleStatePage = new PageImpl<VehicleState>(listVehicleStates,
                PageRequest.of(page, pageSize),
                listVehicleStates.size());

        // Create a PagedModel object
        PagedModel<VehicleState> pagedModel = PagedModel.of(vehicleStatePage.getContent(), new PagedModel.PageMetadata(
                vehicleStatePage.getSize(), vehicleStatePage.getNumber(), vehicleStatePage.getTotalElements()));

        return new ResponseEntity<>(pagedModel, HttpStatus.OK);
    }
}
