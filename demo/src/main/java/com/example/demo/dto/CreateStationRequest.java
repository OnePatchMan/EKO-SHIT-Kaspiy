package com.example.demo.dto;

public record CreateStationRequest(
    String code,
    String name,
    String territory,
    Double latitude,
    Double longitude
) {}

