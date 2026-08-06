package com.tarterware.roadrunner.ports;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tarterware.roadrunner.models.VehicleTelemetry;

/**
 * Spring Data Repository for persisting VehicleTelemetry entities into PostGIS.
 */
@Repository
public interface VehicleTelemetryRepository extends CrudRepository<VehicleTelemetry, Long>
{
    @Query("SELECT t FROM VehicleTelemetry t " +
           "WHERE t.timestamp >= :startTime AND t.timestamp <= :endTime " +
           "ORDER BY t.timestamp DESC")
    List<VehicleTelemetry> findLatestTelemetryWithinWindow(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
}
