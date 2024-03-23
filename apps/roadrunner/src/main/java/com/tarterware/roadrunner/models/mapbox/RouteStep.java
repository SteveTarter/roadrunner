package com.tarterware.roadrunner.models.mapbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteStep
{
	String name;

	double duration;
	
	double distance;
	
	double weight;
	
	Geometry geometry;
}
