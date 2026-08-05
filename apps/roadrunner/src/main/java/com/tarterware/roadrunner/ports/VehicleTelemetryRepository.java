package com.tarterware.roadrunner.ports;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.tarterware.roadrunner.models.VehicleTelemetry;

/**
 * Spring Data Repository for persisting VehicleTelemetry entities into PostGIS.
 */
@Repository
public interface VehicleTelemetryRepository extends CrudRepository<VehicleTelemetry, Long>
{
}
