package com.tarterware.roadrunner.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tarterware.roadrunner.services.MaintenanceService;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController
{

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService)
    {
        this.maintenanceService = maintenanceService;
    }

    @PostMapping("/cleanse-sessions")
    public ResponseEntity<Map<String, String>> cleanseSessions()
    {
        maintenanceService.cleanseOrphanedSessions();
        return ResponseEntity.accepted().body(Map.of(
                "status", "Operation Started",
                "message", "Cleaning orphaned sessions in the background. Check logs for progress."));
    }
}