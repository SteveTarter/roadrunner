package com.tarterware.roadrunner.models.mapbox;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Waypoint {
	
    String name;
    double distance;
    List<Double> location;
    
}
