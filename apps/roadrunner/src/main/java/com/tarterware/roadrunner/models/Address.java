package com.tarterware.roadrunner.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Address
{
    // The origin of the data: e.g. GeocodingService, NumericEntry, etc.
    private String source;

    // US street address
    private String address1;
    private String address2;
    private String city;
    private String state;
    private String zipCode;

    // Geodetic location
    private double latitude;
    private double longitude;
}
