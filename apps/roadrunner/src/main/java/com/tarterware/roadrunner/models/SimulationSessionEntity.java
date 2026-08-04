package com.tarterware.roadrunner.models;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "simulation_sessions")
@Data
@NoArgsConstructor
public class SimulationSessionEntity
{
    @Id
    @Column(name = "vehicle_id", length = 50)
    private String vehicleId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "color_code", nullable = false, length = 10)
    private String colorCode;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;
}
