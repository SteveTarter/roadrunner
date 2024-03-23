package com.tarterware.roadrunner.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositionResponse
{
	boolean valid;
	
	String message;
	
	boolean positionLimited;
	
	double latitude;
	
	double longitude;
}
