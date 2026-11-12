package com.example.demo.dto;

import com.example.demo.model.StationStatus;
import java.time.LocalDateTime;

public record StationDto(
    Long id,
    String code,
    String name,
    String territory,
    Double latitude,
    Double longitude,
    StationStatus status,
    LocalDateTime lastUpdated,
    WaterTelemetrySummaryDto latestTelemetry
) {}

