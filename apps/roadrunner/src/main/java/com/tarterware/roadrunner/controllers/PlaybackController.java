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

/**
 * REST controller providing time-based playback access to vehicle state data.
 *
 * <p>
 * This controller supports querying vehicle positions at a specific point in
 * time by reconstructing state from a Kafka-backed event stream. It uses two
 * distinct data sources depending on how recent the requested timestamp is:
 *
 * <ul>
 * <li><b>Hot store</b>: A Kafka Streams {@link ReadOnlyWindowStore} used for
 * recent data (typically within the last hour).</li>
 * <li><b>Cold store</b>: A direct Kafka consumer replay that scans topic
 * partitions from a timestamp-derived offset for older data.</li>
 * </ul>
 *
 * <p>
 * All queries operate on a fixed lookback window (currently 2 seconds) ending
 * at the requested timestamp. Within that window, the controller returns the
 * most recent state per vehicle.
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
 * timestamp.</li>
 * <li>{@code GET /api/playback/get-vehicle-state} – Returns a single vehicle's
 * state at a timestamp.</li>
 * </ul>
 *
 * <p>
 * <b>Lifecycle:</b>
 * <ul>
 * <li>On initialization, a Kafka consumer is created and assigned all
 * partitions for the configured topic.</li>
 * <li>On shutdown, the consumer is closed to release resources.</li>
 * </ul>
 *
 * @author
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
    private final StreamsBuilderFactoryBean streamsFactory;

    // The long-lived "hot" resource
    private Consumer<String, VehiclePositionEvent> playbackConsumer;

    private static final String UNSET_VALUE = "Unset";

    private static final Logger logger = LoggerFactory.getLogger(PlaybackController.class);

    /**
     * Constructs a new {@code PlaybackController}.
     *
     * @param kafkaTopicMetadataService service used to determine topic partition
     *                                  metadata
     * @param consumerFactory           factory for creating Kafka consumers used in
     *                                  cold queries
     * @param streamsFactory            factory for accessing Kafka Streams state
     *                                  stores for hot queries
     */
    public PlaybackController(
            KafkaTopicMetadataService kafkaTopicMetadataService,
            ConsumerFactory<String, VehiclePositionEvent> consumerFactory,
            StreamsBuilderFactoryBean streamsFactory)
    {
        this.kafkaTopicMetadataService = kafkaTopicMetadataService;
        this.consumerFactory = consumerFactory;
        this.streamsFactory = streamsFactory;
    }

    /**
     * Initializes the controller by:
     * <ul>
     * <li>Determining the number of partitions for the configured topic</li>
     * <li>Creating and assigning a Kafka consumer to all partitions</li>
     * </ul>
     *
     * <p>
     * This method is invoked automatically after dependency injection.
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
     * Retrieves a paginated list of vehicle states for a given timestamp.
     *
     * <p>
     * If no timestamp is provided, the current system time is used. The query
     * returns the most recent state per vehicle within a 2-second window ending at
     * the specified timestamp.
     *
     * <p>
     * Data is retrieved from either the hot or cold store depending on how recent
     * the timestamp is.
     *
     * @param timestamp ISO-8601 timestamp string (e.g.,
     *                  {@code 2026-04-17T21:47:07.113Z}), or "Unset" to use the
     *                  current time
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
        long endTime = timestamp.equals(UNSET_VALUE)
                ? Instant.now().toEpochMilli()
                : Instant.parse(timestamp).toEpochMilli();

        // Expand the window 2 seconds back
        long startTime = endTime - 2000;

        // Calculate the point in time that the stream runs out
        long hotThreshold = Instant.now().minus(Duration.ofHours(1)).toEpochMilli();

        Map<UUID, VehicleState> latestStates;
        if (endTime > hotThreshold)
        {
            latestStates = queryHotStore(startTime, endTime);
        }
        else
        {
            latestStates = queryColdStore(startTime, endTime);
        }

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
        long endTime = timestamp.equals(UNSET_VALUE)
                ? Instant.now().toEpochMilli()
                : Instant.parse(timestamp).toEpochMilli();

        // Expand the window 2 seconds back
        long startTime = endTime - 2000;

        // Calculate the point in time that the stream runs out
        long hotThreshold = Instant.now().minus(Duration.ofHours(1)).toEpochMilli();

        Map<UUID, VehicleState> latestStates;
        if (endTime > hotThreshold)
        {
            latestStates = queryHotStore(startTime, endTime);
        }
        else
        {
            latestStates = queryColdStore(startTime, endTime);
        }

        VehicleState vehicleState = latestStates.get(UUID.fromString(vehicleId));

        if (vehicleState == null)
        {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<VehicleState>(vehicleState, HttpStatus.OK);
    }

    /**
     * Performs a cold query by replaying Kafka topic partitions from a
     * timestamp-derived offset.
     *
     * <p>
     * For each partition:
     * <ul>
     * <li>Finds the offset corresponding to {@code startTime}</li>
     * <li>Seeks the consumer to that offset</li>
     * <li>Polls records until {@code endTime} is exceeded or a timeout occurs</li>
     * </ul>
     *
     * <p>
     * The method collects the most recent state per vehicle within the time window.
     *
     * <p>
     * <b>Note:</b> Access to the consumer is synchronized because Kafka consumers
     * are not thread-safe.
     *
     * @param startTime start of the query window (epoch millis)
     * @param endTime   end of the query window (epoch millis)
     * @return map of vehicle ID to latest {@link VehicleState} within the window
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
     * Performs a hot query using a Kafka Streams window store.
     *
     * <p>
     * This method retrieves all events within the given time window and reduces
     * them to the most recent state per vehicle.
     *
     * @param startTime start of the query window (epoch millis)
     * @param endTime   end of the query window (epoch millis)
     * @return map of vehicle ID to latest {@link VehicleState} within the window
     */
    private Map<UUID, VehicleState> queryHotStore(
            long startTime,
            long endTime)
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

        return latestStates;
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
}
