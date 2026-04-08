package com.tarterware.roadrunner.adapters.kafka;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;

/**
 * Kafka consumer responsible for processing vehicle telemetry events and
 * maintaining the current state of the simulation for the view layer. *
 * <p>
 * This component acts as the bridge between the asynchronous event stream and
 * the synchronous state store used by REST controllers.
 * </p>
 * *
 * <p>
 * The consumer is only active when
 * {@code com.tarterware.roadrunner.messaging.kafka.enabled} is set to
 * {@code true} in the application properties.
 * </p>
 * * @see VehiclePositionEvent
 * 
 * @see ControllerVehicleStateStore
 */
@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaVehicleEventConsumer
{
    private ControllerVehicleStateStore vehicleStateStore;

    private static final Logger log = LoggerFactory.getLogger(KafkaVehicleEventConsumer.class);

    /**
     * Constructs a new consumer with the required state store. * @param
     * vehicleStateStore the store used to persist the latest vehicle telemetry
     */
    public KafkaVehicleEventConsumer(
            ControllerVehicleStateStore vehicleStateStore)
    {
        this.vehicleStateStore = vehicleStateStore;
        log.info("KafkaVehicleEventConsumer is ACTIVE");
        log.info("vehicleStateStore is {}", vehicleStateStore);
    }

    /**
     * Listens to the vehicle position topic and updates the
     * {@link ControllerVehicleStateStore} based on the event status. *
     * <p>
     * The method handles three primary event types:
     * </p>
     * <ul>
     * <li><b>CREATED:</b> Initializes a new {@link VehicleState} and registers the
     * vehicle as active.</li>
     * <li><b>MOVING:</b> Updates existing state, provided the incoming
     * {@code sequenceNumber} is greater than the currently stored version to
     * prevent out-of-order data processing.</li>
     * <li><b>DELETED:</b> Removes the vehicle from the store and the active vehicle
     * list.</li>
     * </ul>
     * * @param event the telemetry event containing position, heading, and status
     * metadata
     * 
     * @throws RuntimeException if a {@code MOVING} event is received for an unknown
     *                          ID, or if an invalid status is encountered.
     */
    @KafkaListener(
            topics = "${com.tarterware.roadrunner.kafka.topic.vehicle-position}",
            groupId = "KafkaVehicleEventConsumer")
    public void receive(@Payload
    VehiclePositionEvent event)
    {
        UUID vehicleId;
        VehicleState vehicleState;

        log.debug("Kafka Event [{}]: Vehicle {} is at ({}, {}) heading {} degrees at {} m/s. Sequence: {}",
                event.status(),
                event.vehicleId(),
                event.latitude(),
                event.longitude(),
                event.heading(),
                event.speed(),
                event.sequenceNumber());

        switch (event.status())
        {

            case "CREATED":
                log.debug("CREATED");

                // Create a new vehicleState and populate with the event
                vehicleState = new VehicleState();
                vehicleId = UUID.fromString(event.vehicleId());
                vehicleState.setId(vehicleId);
                vehicleState.setDegLatitude(event.latitude());
                vehicleState.setDegLongitude(event.longitude());
                vehicleState.setDegBearing(event.heading());
                vehicleState.setMetersPerSecond(event.speed());
                vehicleState.setMsEpochLastRun(event.sequenceNumber());
                vehicleState.setNsLastExec(event.nsLastExec());
                vehicleState.setPositionValid(event.positionValid());
                vehicleState.setPositionLimited(event.positionLimited());
                vehicleState.setColorCode(event.colorCode());
                vehicleState.setManagerHost(event.managerHost());

                // Add it to the vehicleStateStore
                vehicleStateStore.saveVehicle(vehicleState);
                vehicleStateStore.addActiveVehicle(vehicleId);
                break;

            case "MOVING":
                log.debug("MOVING");
                vehicleId = UUID.fromString(event.vehicleId());
                vehicleState = vehicleStateStore.getVehicle(vehicleId);
                if (vehicleState == null)
                {
                    throw new RuntimeException("No vehicle with ID " + vehicleId);
                }

                // Ensure this isn't a stale event. The new sequence number should be greater.
                long sequenceNumber = event.sequenceNumber();
                if (sequenceNumber > vehicleState.getMsEpochLastRun())
                {
                    vehicleState.setId(vehicleId);
                    vehicleState.setDegLatitude(event.latitude());
                    vehicleState.setDegLongitude(event.longitude());
                    vehicleState.setDegBearing(event.heading());
                    vehicleState.setMetersPerSecond(event.speed());
                    vehicleState.setMsEpochLastRun(event.sequenceNumber());
                    vehicleState.setNsLastExec(event.nsLastExec());
                    vehicleState.setPositionValid(event.positionValid());
                    vehicleState.setPositionLimited(event.positionLimited());
                    vehicleState.setColorCode(event.colorCode());
                    vehicleState.setManagerHost(event.managerHost());

                    vehicleStateStore.saveVehicle(vehicleState);
                }

                break;

            case "DELETED":
                log.debug("DELETED");

                vehicleId = UUID.fromString(event.vehicleId());
                vehicleStateStore.deleteVehicle(vehicleId);
                vehicleStateStore.removeActiveVehicle(vehicleId);
                break;

            default:
                throw new RuntimeException("Invalid event status: " + event.status());
        }
    }

}
