package com.tarterware.roadrunner.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class SimulationSessionSerializationTest
{
    @Test
    void testJsonSerialization() throws JsonMappingException, JsonProcessingException
    {
        // 1. Setup ObjectMapper to handle Java 8 Dates/Times correctly
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        // Create the original session with nanosecond precision
        UUID vehicleId = UUID.randomUUID();
        Instant endTime = Instant.now();
        Instant startTime = endTime.minusMillis(100000);

        // Simulate an ongoing session by leaving end null
        SimulationSession originalSession = new SimulationSession();
        originalSession.setId(vehicleId);
        originalSession.setStart(startTime);
        originalSession.setEnd(null);

        // Serialize to JSON string
        String json = mapper.writeValueAsString(originalSession);
        System.out.println("Serialized JSON: " + json);

        // Deserialize back to an object
        SimulationSession deserializedSession = mapper.readValue(json, SimulationSession.class);

        // Assertions to verify no loss of precision
        assertEquals(originalSession.getId(), deserializedSession.getId(), "IDs must match");
        assertEquals(originalSession.getStart(), deserializedSession.getStart(), "Start time nanoseconds must match");
        assertEquals(originalSession.getEnd(), deserializedSession.getEnd(), "End time nanoseconds must match");

        // Repeat test with end time set
        originalSession.setEnd(endTime);

        // Serialize to JSON string
        json = mapper.writeValueAsString(originalSession);
        System.out.println("Serialized JSON: " + json);

        // Deserialize back to an object
        deserializedSession = mapper.readValue(json, SimulationSession.class);

        // Assertions to verify no loss of precision
        assertEquals(originalSession.getId(), deserializedSession.getId(), "IDs must match");
        assertEquals(originalSession.getStart(), deserializedSession.getStart(), "Start time nanoseconds must match");
        assertEquals(originalSession.getEnd(), deserializedSession.getEnd(), "End time nanoseconds must match");

        // Explicitly check nanosecond equality
        assertEquals(originalSession.getStart().getNano(), deserializedSession.getStart().getNano(),
                "Nanosecond precision lost");
    }
}
