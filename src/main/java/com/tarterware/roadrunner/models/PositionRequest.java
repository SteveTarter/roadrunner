package com.tarterware.roadrunner.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositionRequest
{
	double metersTravel;
	
	TripPlan tripPlan;
}
