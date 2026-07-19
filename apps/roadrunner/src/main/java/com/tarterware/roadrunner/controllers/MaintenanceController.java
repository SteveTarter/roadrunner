package com.tarterware.roadrunner.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/whoami")
    public Map<String, Object> debugAuth(Authentication auth)
    {
        return Map.of(
                "username", auth.getName(),
                "authorities", auth.getAuthorities() // You should see [ROLE_ADMIN, ...] here
        );
    }
}