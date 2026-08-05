package com.tarterware.roadrunner.ports;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.tarterware.roadrunner.models.BookmarkEntity;

@Repository
public interface BookmarkEntityRepository extends CrudRepository<BookmarkEntity, String>
{
}
