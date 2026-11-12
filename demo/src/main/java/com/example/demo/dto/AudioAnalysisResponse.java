package com.example.demo.dto;

import java.time.LocalDateTime;
import java.util.Map;

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
    LocalDateTime createdAt,
    Map<String, Object> analysisDetails
) {
    public AudioAnalysisResponse(Long audioId, Long stationId, String stationName, String filename,
            String detectedClass, Double confidence, String threatLevel, String analysisSource,
            boolean alertGenerated, Long alertId, LocalDateTime createdAt) {
        this(audioId, stationId, stationName, filename, detectedClass, confidence, threatLevel,
            analysisSource, alertGenerated, alertId, createdAt, Map.of());
    }
}
