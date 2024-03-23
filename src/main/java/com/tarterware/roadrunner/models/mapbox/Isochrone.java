package com.tarterware.roadrunner.models.mapbox;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Isochrone
{
	@JsonProperty("type")
	String theType;
	
	List<IsochroneFeature> features;
}
