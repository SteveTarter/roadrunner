package com.tarterware.roadrunner.models.mapbox;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Feature
{
	@JsonProperty("id")
	String id;
	
	float relevance;
	
	List<String> place_type;
	
	List<Double> center;
}
