package com.example.demo.controller;

import com.example.demo.dto.SpawningForecastResponse;
import com.example.demo.service.ForecastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forecast")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping("/spawning")
    public ResponseEntity<SpawningForecastResponse> getSpawningForecast(
            @RequestParam(required = false, defaultValue = "cyprinus_carpio") String species,
            @RequestParam(required = false) Long stationId) {
        return ResponseEntity.ok(forecastService.calculateSpawningForecast(species, stationId));
    }
}

