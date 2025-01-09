package utils;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarterware.roadrunner.models.mapbox.Directions;

public class TestUtils {
    public static Directions loadMockDirections(String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File(filePath), Directions.class);
    }
}
