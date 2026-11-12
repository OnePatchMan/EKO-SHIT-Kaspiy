package com.example.demo.dto;

import com.example.demo.model.AlertSeverity;
import com.example.demo.model.AlertType;
import java.time.LocalDateTime;

public record AlertDto(
    Long id,
    Long stationId,
    String stationCode,
    String stationName,
    String territory,
    Double latitude,
    Double longitude,
    AlertType type,
    AlertSeverity severity,
    String message,
    String details,
    LocalDateTime createdAt,
    boolean resolved,
    LocalDateTime resolvedAt
) {}

