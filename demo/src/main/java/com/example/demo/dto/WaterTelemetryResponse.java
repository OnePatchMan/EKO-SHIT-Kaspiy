package com.example.demo.dto;

import com.example.demo.model.QualityStatus;
import java.time.LocalDateTime;

public record WaterTelemetryResponse(
    Long id,
    Long stationId,
    String stationCode,
    String stationName,
    LocalDateTime recordedAt,
    Double temperatureC,
    Double salinityPpt,
    Double dissolvedOxygenMgL,
    Double ph,
    Double turbidityNtu,
    Double waterLevelCm,
    QualityStatus qualityStatus,
    int generatedAlertsCount
) {}

