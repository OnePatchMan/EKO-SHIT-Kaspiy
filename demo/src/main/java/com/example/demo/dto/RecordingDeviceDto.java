package com.example.demo.dto;

public record RecordingDeviceDto(
    String deviceId,
    String name,
    Long stationId,
    String stationCode,
    String stationName,
    String status,
    int batteryPercent,
    String signalStrength,
    String hydrophoneModel,
    double ambientNoiseDb,
    String streamUrl,
    boolean sentryActive
) {}
