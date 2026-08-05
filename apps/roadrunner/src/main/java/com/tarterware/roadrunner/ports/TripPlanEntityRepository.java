package com.tarterware.roadrunner.ports;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.tarterware.roadrunner.models.TripPlanEntity;

@Repository
public interface TripPlanEntityRepository extends CrudRepository<TripPlanEntity, String>
{
}
