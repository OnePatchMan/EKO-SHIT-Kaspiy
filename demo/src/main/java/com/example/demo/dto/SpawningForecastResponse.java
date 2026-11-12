package com.example.demo.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record SpawningForecastResponse(
    String species,
    String targetZone,
    LocalDateTime calculationTime,
    double suitabilityIndex,
    String status,
    LocalDate windowStart,
    LocalDate windowEnd,
    Map<String, Object> hydrologySummary,
    String limitingFactor,
    String patrolRecommendation,
    String modelVersion
) {}

