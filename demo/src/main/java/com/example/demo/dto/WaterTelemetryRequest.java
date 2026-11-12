package com.example.demo.dto;

import java.time.LocalDateTime;

public record WaterTelemetryRequest(
    Long stationId,
    Double temperatureC,
    Double salinityPpt,
    Double dissolvedOxygenMgL,
    Double ph,
    Double turbidityNtu,
    Double waterLevelCm,
    LocalDateTime recordedAt
) {}

