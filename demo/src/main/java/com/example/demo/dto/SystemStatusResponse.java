package com.example.demo.dto;

import java.time.LocalDateTime;

public record SystemStatusResponse(
    String systemName,
    String status,
    LocalDateTime serverTime,
    long stationCount,
    long telemetryRecordsCount,
    long totalAlertsCount,
    long activeAlertsCount,
    boolean aiServiceAvailable
) {}

