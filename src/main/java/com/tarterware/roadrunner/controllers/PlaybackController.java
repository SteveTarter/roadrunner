package com.tarterware.roadrunner.controllers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import com.tarterware.roadrunner.adapters.kafka.KafkaControllerVehicleStateStore;
import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * REST controller providing time-based playback access to vehicle state data.
 *
 * <p>
 * This controller supports querying vehicle positions at a specific point in
 * time by reconstructing state from a Kafka-backed event stream. It uses two
 * distinct data sources depending on whether the request is for the current
 * "live" state or a historical point in time:
 *
 * <ul>
 * <li><b>Live store</b>: An in-memory snapshot provided by
 * {@link KafkaControllerVehicleStateStore} used when no timestamp is specified.
 * This provides near-instant access to the most recent state of all active
 * vehicles.</li>
 * <li><b>Cold store</b>: A direct Kafka consumer replay that scans topic
 * partitions from a timestamp-derived offset for historical data.</li>
 * </ul>
 *
 * <p>
 * For historical queries, the controller operates on a fixed 2-second lookback
 * window ending at the requested timestamp. Within that window, the controller
 * returns the most recent state per vehicle found in the Kafka log.
 *
 * <p>
 * <b>Thread safety:</b> The Kafka consumer used for cold queries is not
 * thread-safe. Access is synchronized to allow concurrent HTTP requests without
 * corrupting consumer state.
 *
 * <p>
 * <b>Endpoints:</b>
 * <ul>
 * <li>{@code GET /api/playback/state} – Returns paginated vehicle states for a
 * timestamp or the current live state.</li>
 * <li>{@code GET /api/playback/get-vehicle-state} – Returns a single vehicle's
 * state at a timestamp or its current live state.</li>
 * </ul>
 */
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
    private final KafkaControllerVehicleStateStore vehicleStateStore;

    // The long-lived "hot" resource
    private Consumer<String, VehiclePositionEvent> playbackConsumer;

    private static final String UNSET_VALUE = "Unset";

    private static final Logger logger = LoggerFactory.getLogger(PlaybackController.class);

    /**
     * Constructs a new {@code PlaybackController}.
     *
     * @param kafkaTopicMetadataService service used to determine topic partition
     *                                  metadata
     * @param consumerFactory           factory for creating the long-lived Kafka
     *                                  consumer used in cold queries
     * @param vehicleStateStore         in-memory store providing current vehicle
     *                                  snapshots for live queries
     */
    public PlaybackController(
            KafkaTopicMetadataService kafkaTopicMetadataService,
            ConsumerFactory<String, VehiclePositionEvent> consumerFactory,
            KafkaControllerVehicleStateStore vehicleStateStore)
    {
        this.kafkaTopicMetadataService = kafkaTopicMetadataService;
        this.consumerFactory = consumerFactory;
        this.vehicleStateStore = vehicleStateStore;
    }

    /**
     * Initializes the controller by:
     * <ul>
     * <li>Determining the number of partitions for the configured topic</li>
     * <li>Creating and manually assigning a long-lived Kafka consumer to all
     * partitions to enable high-performance seeking</li>
     * </ul>
     */
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

    /**
     * Cleans up resources by closing the Kafka consumer used for cold playback
     * queries.
     *
     * <p>
     * This method is invoked automatically during application shutdown.
     */
    @PreDestroy
    public void tearDown()
    {
        if (playbackConsumer != null)
        {
            playbackConsumer.close();
            logger.info("Playback Consumer closed.");
        }
    }

    /**
     * Retrieves a paginated list of vehicle states for a given timestamp or current
     * time.
     *
     * <p>
     * If {@code timestamp} is "Unset", the current live states are retrieved from
     * the in-memory store. Otherwise, the method reconstructs the state by
     * replaying the Kafka log within a 2-second window ending at the specified
     * time.
     *
     * @param timestamp ISO-8601 timestamp string (e.g.,
     *                  {@code 2026-04-17T21:47:07.113Z}), or "Unset" to use the
     *                  current live snapshot
     * @param page      zero-based page index
     * @param pageSize  number of items per page
     * @return paginated vehicle states wrapped in a {@link PagedModel}
     */
    @GetMapping("/state")
    ResponseEntity<PagedModel<VehicleState>> getVehicleStatesAtTimestamp(
            @RequestParam(defaultValue = UNSET_VALUE)
            String timestamp,
            @RequestParam(defaultValue = "0")
            int page,
            @RequestParam(defaultValue = "10")
            int pageSize)
    {
        Map<UUID, VehicleState> latestStates = getStatesForTimestamp(timestamp);

        return buildPagedResponse(latestStates, page, pageSize);
    }

    /**
     * Retrieves the state of a specific vehicle at a given timestamp.
     *
     * <p>
     * The result is derived from the same 2-second window logic used by the bulk
     * endpoint. If the vehicle does not have a state within that window, a
     * {@code 404 NOT FOUND} response is returned.
     *
     * @param vehicleId UUID string identifying the vehicle
     * @param timestamp ISO-8601 timestamp string, or "Unset" to use the current
     *                  time
     * @return the vehicle state if found, otherwise {@code 404 NOT FOUND}
     */
    @GetMapping("/get-vehicle-state")
    ResponseEntity<VehicleState> getVehicleStateFor(
            @RequestParam(defaultValue = UNSET_VALUE)
            String vehicleId,
            @RequestParam(defaultValue = UNSET_VALUE)
            String timestamp)
    {
        Map<UUID, VehicleState> latestStates = getStatesForTimestamp(timestamp);

        VehicleState vehicleState = latestStates.get(UUID.fromString(vehicleId));

        if (vehicleState == null)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<VehicleState>(vehicleState, HttpStatus.OK);
    }

    /**
     * Performs a historical query by replaying Kafka topic partitions from a
     * timestamp-derived offset.
     *
     * <p>
     * This method implements a "Surgical Seek" by:
     * <ul>
     * <li>Pausing the consumer and seeking to the calculated offsets</li>
     * <li>Resuming and polling until a record exceeding {@code endTime} is
     * encountered on all relevant partitions</li>
     * <li>Applying a 1-second safety deadline to prevent blocking on sparse
     * partitions</li>
     * </ul>
     *
     * @param startTime start of the query window (epoch millis)
     * @param endTime   end of the query window (epoch millis)
     * @return map of vehicle ID to latest {@link VehicleState} found within the
     *         window
     */
    private Map<UUID, VehicleState> queryColdStore(
            long startTime,
            long endTime)
    {
        Map<UUID, VehicleState> latestStates = new HashMap<>();
        // CRITICAL: Kafka Consumer is not thread-safe.
        // We must synchronize to allow multiple users to query playback without
        // crashing.
        synchronized (playbackConsumer)
        {
            // Pause all partitions to stop any background fetching
            playbackConsumer.pause(topicPartitions);

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
            playbackConsumer.resume(topicPartitions);

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

        return latestStates;
    }

    /**
     * Performs a live query using the in-memory snapshot store.
     *
     * <p>
     * This method bypasses Kafka log scanning and returns the current state of all
     * vehicles as tracked by the application.
     *
     * @return map of vehicle ID to their current {@link VehicleState}
     */
    private Map<UUID, VehicleState> queryHotStore()
    {
        Set<UUID> vehicleIds = vehicleStateStore.getActiveVehicleIds();

        return vehicleStateStore.getVehicles(vehicleIds);
    }

    /**
     * Maps a {@link VehiclePositionEvent} to a {@link VehicleState}, keeping only
     * the most recent event per vehicle.
     *
     * <p>
     * If an existing state is present and has a timestamp greater than or equal to
     * the incoming event, the update is ignored.
     *
     * @param event        the incoming Kafka event
     * @param latestStates map of vehicle ID to latest known state
     */
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

    /**
     * Builds a paginated HTTP response from a collection of vehicle states.
     *
     * <p>
     * This method slices the full result set according to the requested page and
     * page size, and wraps the result in a {@link PagedModel}.
     *
     * @param states   map of vehicle states
     * @param page     zero-based page index
     * @param pageSize number of items per page
     * @return paginated response entity
     */
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

    /**
     * Resolves vehicle states for a given timestamp by selecting the appropriate
     * data source.
     *
     * <ul>
     * <li>If {@code timestamp} is {@code "Unset"}, the in-memory live store is
     * queried via {@link #queryHotStore()}.</li>
     * <li>Otherwise, a 2-second lookback window is constructed and a Kafka consumer
     * replay is performed via {@link #queryColdStore(long, long)}.</li>
     * </ul>
     *
     * @param timestamp ISO-8601 timestamp string or {@code "Unset"}
     * @return a map of vehicle IDs to their latest {@link VehicleState}
     * @throws java.time.format.DateTimeParseException if the timestamp is not
     *                                                 {@code "Unset"} and is
     *                                                 malformed
     */
    private Map<UUID, VehicleState> getStatesForTimestamp(String timestamp)
    {
        if (timestamp.equals(UNSET_VALUE))
        {
            return queryHotStore();
        }

        long endTime = Instant.parse(timestamp).toEpochMilli();

        long startTime = endTime - 2000;

        return queryColdStore(startTime, endTime);
    }
}
