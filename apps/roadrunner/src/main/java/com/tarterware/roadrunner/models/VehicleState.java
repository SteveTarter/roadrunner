package com.tarterware.roadrunner.models;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleState
{
    UUID id;

    double metersOffset;
    
    boolean positionLimited;
    
    boolean positionValid;
    
    double degLatitude;
    
    double degLongitude;
    
    double metersPerSecondDesired;
    
    double metersPerSecond;
    
    double mssAcceleration;
    
    String colorCode;
}
