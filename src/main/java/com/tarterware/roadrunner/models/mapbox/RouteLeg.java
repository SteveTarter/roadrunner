package com.tarterware.roadrunner.models.mapbox;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteLeg {
	
	double weight;
	double duration;
	
	List<RouteStep> steps;
}
