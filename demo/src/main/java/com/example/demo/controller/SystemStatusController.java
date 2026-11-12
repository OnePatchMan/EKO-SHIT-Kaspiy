package com.example.demo.controller;

import com.example.demo.dto.SystemStatusResponse;
import com.example.demo.service.AlertService;
import com.example.demo.service.StationService;
import com.example.demo.service.WaterTelemetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class SystemStatusController {

    private final StationService stationService;
    private final WaterTelemetryService telemetryService;
    private final AlertService alertService;

    public SystemStatusController(StationService stationService,
                                  WaterTelemetryService telemetryService,
                                  AlertService alertService) {
        this.stationService = stationService;
        this.telemetryService = telemetryService;
        this.alertService = alertService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "EcoShield Caspian Backend",
            "timestamp", LocalDateTime.now()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatusResponse> status() {
        long stations = stationService.getAllStations().size();
        long telemetry = telemetryService.countTelemetryRecords();
        long totalAlerts = alertService.countTotalAlerts();
        long activeAlerts = alertService.countActiveAlerts();

        return ResponseEntity.ok(new SystemStatusResponse(
            "Эко-Щит Каспия — Core API",
            "ONLINE",
            LocalDateTime.now(),
            stations,
            telemetry,
            totalAlerts,
            activeAlerts,
            true
        ));
    }
}

