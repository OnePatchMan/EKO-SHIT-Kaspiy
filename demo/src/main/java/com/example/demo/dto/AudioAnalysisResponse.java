package com.example.demo.dto;

import java.time.LocalDateTime;

public record AudioAnalysisResponse(
    Long audioId,
    Long stationId,
    String stationName,
    String filename,
    String detectedClass,
    Double confidence,
    String threatLevel,
    String analysisSource,
    boolean alertGenerated,
    Long alertId,
    LocalDateTime createdAt
) {}

