package com.tarterware.roadrunner.adapters.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;

@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaControllerVehicleStateStore extends KafkaVehicleStateStore implements ControllerVehicleStateStore
{

}
