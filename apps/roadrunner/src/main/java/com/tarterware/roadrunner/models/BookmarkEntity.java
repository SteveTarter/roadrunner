package com.tarterware.roadrunner.models;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bookmarks")
@Data
@NoArgsConstructor
public class BookmarkEntity
{
    @Id
    @Column(name = "vehicle_id", length = 50)
    private String vehicleId;

    @Column(length = 100)
    private String username;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
