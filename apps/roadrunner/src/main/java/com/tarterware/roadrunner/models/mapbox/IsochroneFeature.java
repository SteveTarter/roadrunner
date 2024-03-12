package com.tarterware.roadrunner.models.mapbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IsochroneFeature {
	@JsonProperty("type")
	String theType;
	
	Geometry geometry;
}
