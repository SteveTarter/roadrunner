package com.tarterware.roadrunner.models;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trip_plans")
@Data
@NoArgsConstructor
public class TripPlanEntity
{
    @Id
    @Column(name = "vehicle_id", length = 50)
    private String vehicleId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String stops;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
