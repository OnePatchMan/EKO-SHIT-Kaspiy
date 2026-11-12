package com.example.demo.controller;

import com.example.demo.dto.WaterTelemetryRequest;
import com.example.demo.dto.WaterTelemetryResponse;
import com.example.demo.service.WaterTelemetryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/telemetry/water")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class WaterTelemetryController {

    private final WaterTelemetryService telemetryService;

    public WaterTelemetryController(WaterTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ResponseEntity<WaterTelemetryResponse> recordTelemetry(@RequestBody WaterTelemetryRequest request) {
        WaterTelemetryResponse response = telemetryService.processTelemetry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<WaterTelemetryResponse>> recordTelemetryBatch(@RequestBody List<WaterTelemetryRequest> requests) {
        List<WaterTelemetryResponse> responses = telemetryService.processTelemetryBatch(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<WaterTelemetryResponse>> getStationHistory(
            @PathVariable Long stationId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(telemetryService.getStationHistory(stationId, limit));
    }

    @GetMapping("/latest")
    public ResponseEntity<List<WaterTelemetryResponse>> getLatestForAllStations() {
        return ResponseEntity.ok(telemetryService.getLatestForAllStations());
    }
}

