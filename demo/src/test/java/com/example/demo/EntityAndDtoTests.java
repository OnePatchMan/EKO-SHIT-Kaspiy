package com.example.demo;

import com.example.demo.dto.*;
import com.example.demo.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntityAndDtoTests {

    @Test
    @DisplayName("Проверка сущности Station: конструкторы, геттеры и сеттеры")
    void testStationEntity() {
        Station station = new Station();
        station.setId(10L);
        station.setCode("BUOY-TEST");
        station.setName("Тестовый буй");
        station.setTerritory("Каспий");
        station.setLatitude(45.123);
        station.setLongitude(47.456);
        station.setStatus(StationStatus.WARNING);
        LocalDateTime now = LocalDateTime.now();
        station.setLastUpdated(now);

        assertEquals(10L, station.getId());
        assertEquals("BUOY-TEST", station.getCode());
        assertEquals("Тестовый буй", station.getName());
        assertEquals("Каспий", station.getTerritory());
        assertEquals(45.123, station.getLatitude());
        assertEquals(47.456, station.getLongitude());
        assertEquals(StationStatus.WARNING, station.getStatus());
        assertEquals(now, station.getLastUpdated());
    }

    @Test
    @DisplayName("Проверка сущности WaterTelemetry: геттеры и сеттеры")
    void testWaterTelemetryEntity() {
        Station station = new Station("BUOY-01", "Буй 1", "Дельта", 45.0, 48.0, StationStatus.NORMAL);
        LocalDateTime time = LocalDateTime.now();

        WaterTelemetry telemetry = new WaterTelemetry();
        telemetry.setId(1L);
        telemetry.setStation(station);
        telemetry.setRecordedAt(time);
        telemetry.setTemperatureC(14.5);
        telemetry.setSalinityPpt(1.2);
        telemetry.setDissolvedOxygenMgL(7.8);
        telemetry.setPh(7.5);
        telemetry.setTurbidityNtu(12.0);
        telemetry.setWaterLevelCm(265.0);
        telemetry.setQualityStatus(QualityStatus.NORMAL);

        assertEquals(1L, telemetry.getId());
        assertEquals(station, telemetry.getStation());
        assertEquals(time, telemetry.getRecordedAt());
        assertEquals(14.5, telemetry.getTemperatureC());
        assertEquals(1.2, telemetry.getSalinityPpt());
        assertEquals(7.8, telemetry.getDissolvedOxygenMgL());
        assertEquals(7.5, telemetry.getPh());
        assertEquals(12.0, telemetry.getTurbidityNtu());
        assertEquals(265.0, telemetry.getWaterLevelCm());
        assertEquals(QualityStatus.NORMAL, telemetry.getQualityStatus());
    }

    @Test
    @DisplayName("Проверка сущности Alert: создание и резолв")
    void testAlertEntity() {
        Station station = new Station("B1", "Буй 1", "Зона", 45.0, 48.0, StationStatus.NORMAL);
        Alert alert = new Alert(station, AlertType.HYDRO_LOW_OXYGEN, AlertSeverity.CRITICAL, "Сообщение", "Детали");

        alert.setId(5L);
        assertEquals(5L, alert.getId());
        assertEquals(station, alert.getStation());
        assertEquals(AlertType.HYDRO_LOW_OXYGEN, alert.getType());
        assertEquals(AlertSeverity.CRITICAL, alert.getSeverity());
        assertEquals("Сообщение", alert.getMessage());
        assertEquals("Детали", alert.getDetails());
        assertFalse(alert.isResolved());
        assertNull(alert.getResolvedAt());

        alert.setResolved(true);
        LocalDateTime resTime = LocalDateTime.now();
        alert.setResolvedAt(resTime);
        assertTrue(alert.isResolved());
        assertEquals(resTime, alert.getResolvedAt());
    }

    @Test
    @DisplayName("Проверка сущности AudioRecord")
    void testAudioRecordEntity() {
        Station station = new Station("B1", "Буй 1", "Зона", 45.0, 48.0, StationStatus.NORMAL);
        AudioRecord record = new AudioRecord(
            station, "test.wav", "stored_test.wav", "boat_engine", 0.95, "HIGH", "AI Classifier"
        );

        record.setId(100L);
        assertEquals(100L, record.getId());
        assertEquals(station, record.getStation());
        assertEquals("test.wav", record.getFilename());
        assertEquals("stored_test.wav", record.getFilePath());
        assertEquals("boat_engine", record.getDetectedClass());
        assertEquals(0.95, record.getConfidence());
        assertEquals("HIGH", record.getThreatLevel());
        assertEquals("AI Classifier", record.getAnalysisSource());
        assertNotNull(record.getCreatedAt());
    }

    @Test
    @DisplayName("Проверка всех значений перечисления StationStatus")
    void testStationStatusEnum() {
        assertEquals(4, StationStatus.values().length);
        assertEquals(StationStatus.NORMAL, StationStatus.valueOf("NORMAL"));
        assertEquals(StationStatus.WARNING, StationStatus.valueOf("WARNING"));
        assertEquals(StationStatus.CRITICAL, StationStatus.valueOf("CRITICAL"));
        assertEquals(StationStatus.OFFLINE, StationStatus.valueOf("OFFLINE"));
    }

    @Test
    @DisplayName("Проверка всех значений перечисления QualityStatus")
    void testQualityStatusEnum() {
        assertEquals(3, QualityStatus.values().length);
        assertEquals(QualityStatus.NORMAL, QualityStatus.valueOf("NORMAL"));
        assertEquals(QualityStatus.WARNING, QualityStatus.valueOf("WARNING"));
        assertEquals(QualityStatus.CRITICAL, QualityStatus.valueOf("CRITICAL"));
    }

    @Test
    @DisplayName("Проверка всех значений перечисления AlertSeverity")
    void testAlertSeverityEnum() {
        assertEquals(3, AlertSeverity.values().length);
        assertEquals(AlertSeverity.INFO, AlertSeverity.valueOf("INFO"));
        assertEquals(AlertSeverity.WARNING, AlertSeverity.valueOf("WARNING"));
        assertEquals(AlertSeverity.CRITICAL, AlertSeverity.valueOf("CRITICAL"));
    }

    @Test
    @DisplayName("Проверка всех значений перечисления AlertType")
    void testAlertTypeEnum() {
        assertTrue(AlertType.values().length >= 8);
        assertNotNull(AlertType.valueOf("HYDRO_LOW_OXYGEN"));
        assertNotNull(AlertType.valueOf("HYDRO_HIGH_SALINITY"));
        assertNotNull(AlertType.valueOf("HYDRO_ABNORMAL_PH"));
        assertNotNull(AlertType.valueOf("HYDRO_HIGH_TURBIDITY"));
        assertNotNull(AlertType.valueOf("HYDRO_CRITICAL_LEVEL"));
        assertNotNull(AlertType.valueOf("ACOUSTIC_BOAT_DETECTED"));
        assertNotNull(AlertType.valueOf("ACOUSTIC_GUNFIRE"));
    }

    @Test
    @DisplayName("Проверка DTO рекорда StationDto")
    void testStationDtoRecord() {
        WaterTelemetrySummaryDto summary = new WaterTelemetrySummaryDto(
            1L, LocalDateTime.now(), 12.0, 0.8, 8.5, 7.6, 10.0, 250.0, QualityStatus.NORMAL
        );
        StationDto dto = new StationDto(
            1L, "ST-1", "Станция 1", "Зона 1", 45.1, 48.2, StationStatus.NORMAL, LocalDateTime.now(), summary
        );

        assertEquals(1L, dto.id());
        assertEquals("ST-1", dto.code());
        assertEquals("Станция 1", dto.name());
        assertEquals(45.1, dto.latitude());
        assertEquals(48.2, dto.longitude());
        assertEquals(StationStatus.NORMAL, dto.status());
        assertNotNull(dto.latestTelemetry());
    }

    @Test
    @DisplayName("Проверка DTO рекорда WaterTelemetryRequest")
    void testWaterTelemetryRequestRecord() {
        LocalDateTime time = LocalDateTime.now();
        WaterTelemetryRequest req = new WaterTelemetryRequest(
            2L, 13.5, 0.9, 7.1, 7.8, 15.0, 240.0, time
        );

        assertEquals(2L, req.stationId());
        assertEquals(13.5, req.temperatureC());
        assertEquals(0.9, req.salinityPpt());
        assertEquals(7.1, req.dissolvedOxygenMgL());
        assertEquals(7.8, req.ph());
        assertEquals(15.0, req.turbidityNtu());
        assertEquals(240.0, req.waterLevelCm());
        assertEquals(time, req.recordedAt());
    }

    @Test
    @DisplayName("Проверка DTO рекорда WaterTelemetryResponse")
    void testWaterTelemetryResponseRecord() {
        LocalDateTime time = LocalDateTime.now();
        WaterTelemetryResponse resp = new WaterTelemetryResponse(
            10L, 2L, "CODE-2", "Буй 2", time, 12.5, 0.8, 8.0, 7.5, 12.0, 250.0, QualityStatus.NORMAL, 0
        );

        assertEquals(10L, resp.id());
        assertEquals("CODE-2", resp.stationCode());
        assertEquals(0, resp.generatedAlertsCount());
        assertEquals(QualityStatus.NORMAL, resp.qualityStatus());
    }

    @Test
    @DisplayName("Проверка DTO рекорда AlertDto")
    void testAlertDtoRecord() {
        LocalDateTime time = LocalDateTime.now();
        AlertDto dto = new AlertDto(
            1L, 2L, "CODE-2", "Буй 2", "Дельта", 45.0, 48.0,
            AlertType.HYDRO_LOW_OXYGEN, AlertSeverity.CRITICAL, "Тревога", "Детали",
            time, false, null
        );

        assertEquals(1L, dto.id());
        assertEquals(AlertType.HYDRO_LOW_OXYGEN, dto.type());
        assertEquals(AlertSeverity.CRITICAL, dto.severity());
        assertFalse(dto.resolved());
    }

    @Test
    @DisplayName("Проверка DTO рекорда SpawningForecastResponse")
    void testSpawningForecastResponseRecord() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(7);
        SpawningForecastResponse resp = new SpawningForecastResponse(
            "Осётр", "Дельта", now, 88.5, "OPTIMAL", start, end,
            Map.of("temp", 12.5), "Норма", "Патруль", "v1.0"
        );

        assertEquals("Осётр", resp.species());
        assertEquals(88.5, resp.suitabilityIndex());
        assertEquals("OPTIMAL", resp.status());
        assertEquals(start, resp.windowStart());
        assertEquals(end, resp.windowEnd());
    }

    @Test
    @DisplayName("Проверка DTO рекорда AudioAnalysisResponse")
    void testAudioAnalysisResponseRecord() {
        LocalDateTime now = LocalDateTime.now();
        AudioAnalysisResponse resp = new AudioAnalysisResponse(
            1L, 3L, "Буй 3", "sample.wav", "boat_engine", 0.94, "HIGH", "Model v1", true, 15L, now
        );

        assertEquals(1L, resp.audioId());
        assertEquals("boat_engine", resp.detectedClass());
        assertEquals(0.94, resp.confidence());
        assertTrue(resp.alertGenerated());
        assertEquals(15L, resp.alertId());
    }

    @Test
    @DisplayName("Проверка DTO рекорда SystemStatusResponse")
    void testSystemStatusResponseRecord() {
        LocalDateTime now = LocalDateTime.now();
        SystemStatusResponse resp = new SystemStatusResponse(
            "EcoShield", "ONLINE", now, 5, 100, 3, 1, true
        );

        assertEquals("EcoShield", resp.systemName());
        assertEquals("ONLINE", resp.status());
        assertEquals(5, resp.stationCount());
        assertEquals(100, resp.telemetryRecordsCount());
        assertTrue(resp.aiServiceAvailable());
    }

    @Test
    @DisplayName("Проверка DTO рекорда CreateStationRequest")
    void testCreateStationRequestRecord() {
        CreateStationRequest req = new CreateStationRequest(
            "BUOY-NEW", "Новый буй", "Каспийское море", 44.5, 47.8
        );

        assertEquals("BUOY-NEW", req.code());
        assertEquals("Новый буй", req.name());
        assertEquals("Каспийское море", req.territory());
        assertEquals(44.5, req.latitude());
        assertEquals(47.8, req.longitude());
    }
}

