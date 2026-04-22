package com.tarterware.roadrunner.configs;

import java.time.Duration;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowBytesStoreSupplier;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonSerde;

import com.tarterware.roadrunner.messaging.VehiclePositionEvent;

@Configuration
@EnableKafkaStreams
public class PlaybackStreamConfig
{
    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topic;

    @Bean
    KStream<String, VehiclePositionEvent> playbackStream(StreamsBuilder builder)
    {
        // Define the Store: 1-hour retention, 250ms "slices"
        WindowBytesStoreSupplier storeSupplier = Stores.persistentWindowStore(
                "hot-playback-store",
                Duration.ofHours(1),
                Duration.ofMillis(250),
                false);

        // Add the Store to the builder
        builder.addStateStore(
                Stores.windowStoreBuilder(
                        storeSupplier,
                        Serdes.String(),
                        new JsonSerde<>(VehiclePositionEvent.class)));

        KStream<String, VehiclePositionEvent> stream = builder.stream(
                topic,
                Consumed.with(
                        Serdes.String(),
                        new JsonSerde<>(VehiclePositionEvent.class)));

        // Pipe data into the store
        stream.process(() -> new Processor<String, VehiclePositionEvent, Void, Void>()
        {
            private WindowStore<String, VehiclePositionEvent> stateStore;

            @Override
            public void init(ProcessorContext<Void, Void> context)
            {
                stateStore = context.getStateStore("hot-playback-store");
            }

            @Override
            public void process(Record<String, VehiclePositionEvent> record)
            {
                stateStore.put(record.key(), record.value(), record.timestamp());
            }
        }, "hot-playback-store");

        return stream;
    }
}