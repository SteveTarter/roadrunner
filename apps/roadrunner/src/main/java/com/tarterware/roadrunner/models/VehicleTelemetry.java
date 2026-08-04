package com.tarterware.roadrunner.models;

import java.time.Instant;

import org.locationtech.jts.geom.Point;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "vehicle_telemetry",
        indexes = {
                @Index(name = "idx_telemetry_vehicle_id", columnList = "vehicleId"),
                @Index(name = "idx_telemetry_timestamp", columnList = "timestamp")
        }
)
@Data
@NoArgsConstructor
public class VehicleTelemetry
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String vehicleId;

    // PostGIS Point representation (Longitude, Latitude) SRID 4326
    @Column(columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point position;

    private double heading;

    private double speed;

    private long sequenceNumber;

    private long nsLastExec;

    private boolean positionValid;

    private boolean positionLimited;

    private String colorCode;

    private String managerHost;

    private String status;

    @Column(nullable = false)
    private Instant timestamp;
}
