package com.tarterware.roadrunner.models.mapbox;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Annotation
{
	List<Double> speed = new ArrayList<Double>();
	
	List<Double> distance = new ArrayList<Double>();
}
