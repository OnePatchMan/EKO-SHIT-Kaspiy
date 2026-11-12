package com.example.demo.controller;

import com.example.demo.dto.CreateStationRequest;
import com.example.demo.dto.StationDto;
import com.example.demo.service.StationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public ResponseEntity<List<StationDto>> getAllStations() {
        return ResponseEntity.ok(stationService.getAllStations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StationDto> getStationById(@PathVariable Long id) {
        return ResponseEntity.ok(stationService.getStationDtoById(id));
    }

    @PostMapping
    public ResponseEntity<StationDto> createStation(@RequestBody CreateStationRequest request) {
        StationDto created = stationService.createStation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}

