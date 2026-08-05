package com.tarterware.roadrunner.ports;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.tarterware.roadrunner.models.VehicleRouteEntity;

@Repository
public interface VehicleRouteRepository extends CrudRepository<VehicleRouteEntity, String>
{
}
