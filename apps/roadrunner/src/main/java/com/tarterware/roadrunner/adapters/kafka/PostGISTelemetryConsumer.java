package com.tarterware.roadrunner.adapters.kafka;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.models.VehicleTelemetry;
import com.tarterware.roadrunner.ports.VehicleTelemetryRepository;

/**
 * Kafka consumer that listens to vehicle telemetry events and persists them
 * to the PostGIS spatial database.
 * <p>
 * This consumer is only active if com.tarterware.roadrunner.persistence.postgis.enabled
 * is set to true. It uses a shared group ID "PostGISTelemetryConsumer" to distribute
 * the load and avoid writing duplicate database records.
 * </p>
 */
@Component
@ConditionalOnProperty(
        prefix = "com.tarterware.roadrunner.persistence.postgis",
        name = "enabled",
        havingValue = "true"
)
public class PostGISTelemetryConsumer
{
    private static final Logger log = LoggerFactory.getLogger(PostGISTelemetryConsumer.class);

    private final VehicleTelemetryRepository telemetryRepository;
    private final GeometryFactory geometryFactory;

    public PostGISTelemetryConsumer(VehicleTelemetryRepository telemetryRepository)
    {
        this.telemetryRepository = telemetryRepository;
        // Construct GeometryFactory with WGS84 SRID 4326
        this.geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        log.info("PostGISTelemetryConsumer is ACTIVE and ready to persist telemetry to PostGIS in batch mode");
    }

    @KafkaListener(
            topics = "${com.tarterware.roadrunner.kafka.topic.vehicle-position}",
            groupId = "PostGISTelemetryConsumer",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void receive(@Payload List<VehiclePositionEvent> events)
    {
        if (events == null || events.isEmpty())
        {
            return;
        }

        log.debug("PostGIS Consumer: Received batch of {} telemetry events", events.size());

        try
        {
            List<VehicleTelemetry> telemetryList = new ArrayList<>();
            for (VehiclePositionEvent event : events)
            {
                VehicleTelemetry telemetry = new VehicleTelemetry();
                telemetry.setVehicleId(event.vehicleId());
                telemetry.setHeading(event.heading());
                telemetry.setSpeed(event.speed());
                telemetry.setSequenceNumber(event.sequenceNumber());
                telemetry.setNsLastExec(event.nsLastExec());
                telemetry.setPositionValid(event.positionValid());
                telemetry.setPositionLimited(event.positionLimited());
                telemetry.setColorCode(event.colorCode());
                telemetry.setManagerHost(event.managerHost());
                telemetry.setStatus(event.status());
                telemetry.setTimestamp(event.eventTime() != null ? event.eventTime() : Instant.now());

                // Build JTS Point (Longitude, Latitude)
                Point position = geometryFactory.createPoint(new Coordinate(event.longitude(), event.latitude()));
                telemetry.setPosition(position);

                telemetryList.add(telemetry);
            }

            telemetryRepository.saveAll(telemetryList);
            log.trace("Saved batch of {} telemetry records", events.size());
        }
        catch (Exception e)
        {
            log.error("Failed to persist batch of {} telemetry events to PostGIS", events.size(), e);
        }
    }
}

