package com.tarterware.roadrunner.models;

import org.locationtech.jts.geom.LineString;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vehicle_routes")
@Data
@NoArgsConstructor
public class VehicleRouteEntity
{
    @Id
    @Column(name = "vehicle_id", length = 50)
    private String vehicleId;

    @Column(name = "distance_meters", nullable = false)
    private double distanceMeters;

    @Column(name = "duration_seconds", nullable = false)
    private double durationSeconds;

    @Column(name = "route_path", columnDefinition = "geometry(LineString, 4326)", nullable = false)
    private LineString routePath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String waypoints;

    @Column(name = "speed_annotations", columnDefinition = "TEXT")
    private String speedAnnotations;
}
