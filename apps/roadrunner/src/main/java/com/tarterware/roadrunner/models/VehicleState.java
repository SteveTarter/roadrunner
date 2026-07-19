package com.tarterware.roadrunner.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleState
{
    String id;

    double metersOffset;

    boolean positionLimited;

    boolean positionValid;

    double degLatitude;

    double degLongitude;

    double metersPerSecondDesired;

    double metersPerSecond;

    double mssAcceleration;

    double degBearingDesired;

    double degBearing;

    String colorCode;

    String managerHost;

    long msEpochLastRun;

    long nsLastExec;
}
