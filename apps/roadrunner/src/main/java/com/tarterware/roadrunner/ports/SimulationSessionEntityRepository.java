package com.tarterware.roadrunner.ports;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.tarterware.roadrunner.models.SimulationSessionEntity;

@Repository
public interface SimulationSessionEntityRepository extends CrudRepository<SimulationSessionEntity, String>
{
}
