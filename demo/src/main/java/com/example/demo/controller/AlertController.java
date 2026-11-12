package com.example.demo.controller;

import com.example.demo.dto.AlertDto;
import com.example.demo.model.AlertSeverity;
import com.example.demo.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<List<AlertDto>> getAlerts(
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) Long stationId) {
        return ResponseEntity.ok(alertService.getAlerts(resolved, severity, stationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertDto> getAlertById(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.getAlertById(id));
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<AlertDto> resolveAlert(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.resolveAlert(id));
    }
}

