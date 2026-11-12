package com.example.demo;

import com.example.demo.dto.*;
import com.example.demo.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelDeepTests {

    @Test
    @DisplayName("Station: проверка дефолтного конструктора")
    void testStationDefaultConstructor() {
        Station s = new Station();
        assertNull(s.getId());
        assertNull(s.getCode());
        assertNull(s.getName());
        assertEquals(StationStatus.NORMAL, s.getStatus());
    }

    @Test
    @DisplayName("Station: проверка полного конструктора")
    void testStationFullConstructor() {
        Station s = new Station("CODE-99", "Буй 99", "Дельта Волги", 45.1, 47.9, StationStatus.WARNING);
        assertEquals("CODE-99", s.getCode());
        assertEquals("Буй 99", s.getName());
        assertEquals("Дельта Волги", s.getTerritory());
        assertEquals(45.1, s.getLatitude());
        assertEquals(47.9, s.getLongitude());
        assertEquals(StationStatus.WARNING, s.getStatus());
        assertNotNull(s.getLastUpdated());
    }

    @Test
    @DisplayName("Station: изменение координат")
    void testStationCoordinatesUpdate() {
        Station s = new Station();
        s.setLatitude(44.12345);
        s.setLongitude(48.54321);
        assertEquals(44.12345, s.getLatitude(), 0.00001);
        assertEquals(48.54321, s.getLongitude(), 0.00001);
    }

    @Test
    @DisplayName("Station: смена статуса на OFFLINE")
    void testStationStatusOffline() {
        Station s = new Station();
        s.setStatus(StationStatus.OFFLINE);
        assertEquals(StationStatus.OFFLINE, s.getStatus());
    }

    @Test
    @DisplayName("WaterTelemetry: проверка дефолтного конструктора")
    void testWaterTelemetryDefaultConstructor() {
        WaterTelemetry wt = new WaterTelemetry();
        assertNull(wt.getId());
        assertNull(wt.getStation());
        assertEquals(QualityStatus.NORMAL, wt.getQualityStatus());
    }

    @Test
    @DisplayName("WaterTelemetry: проверка полного конструктора")
    void testWaterTelemetryFullConstructor() {
        Station s = new Station();
        LocalDateTime time = LocalDateTime.of(2026, 11, 12, 10, 30);
        WaterTelemetry wt = new WaterTelemetry(s, time, 15.0, 1.5, 8.0, 7.6, 20.0, 300.0, QualityStatus.NORMAL);

        assertEquals(s, wt.getStation());
        assertEquals(time, wt.getRecordedAt());
        assertEquals(15.0, wt.getTemperatureC());
        assertEquals(1.5, wt.getSalinityPpt());
        assertEquals(8.0, wt.getDissolvedOxygenMgL());
        assertEquals(7.6, wt.getPh());
        assertEquals(20.0, wt.getTurbidityNtu());
        assertEquals(300.0, wt.getWaterLevelCm());
        assertEquals(QualityStatus.NORMAL, wt.getQualityStatus());
    }

    @Test
    @DisplayName("WaterTelemetry: обновление статуса качества на CRITICAL")
    void testWaterTelemetrySetQualityStatus() {
        WaterTelemetry wt = new WaterTelemetry();
        wt.setQualityStatus(QualityStatus.CRITICAL);
        assertEquals(QualityStatus.CRITICAL, wt.getQualityStatus());
    }

    @Test
    @DisplayName("WaterTelemetry: установка температуры замерзания")
    void testWaterTelemetryFreezingTemp() {
        WaterTelemetry wt = new WaterTelemetry();
        wt.setTemperatureC(0.0);
        assertEquals(0.0, wt.getTemperatureC());
    }

    @Test
    @DisplayName("Alert: конструктор с параметрами")
    void testAlertConstructor() {
        Station s = new Station();
        Alert alert = new Alert(s, AlertType.ACOUSTIC_BOAT_DETECTED, AlertSeverity.WARNING, "Байда", "Детали звука");
        assertEquals(s, alert.getStation());
        assertEquals(AlertType.ACOUSTIC_BOAT_DETECTED, alert.getType());
        assertEquals(AlertSeverity.WARNING, alert.getSeverity());
        assertEquals("Байда", alert.getMessage());
        assertEquals("Детали звука", alert.getDetails());
        assertFalse(alert.isResolved());
        assertNotNull(alert.getCreatedAt());
    }

    @Test
    @DisplayName("Alert: смена типа и важности")
    void testAlertChangeTypeAndSeverity() {
        Alert alert = new Alert();
        alert.setType(AlertType.ACOUSTIC_GUNFIRE);
        alert.setSeverity(AlertSeverity.CRITICAL);
        assertEquals(AlertType.ACOUSTIC_GUNFIRE, alert.getType());
        assertEquals(AlertSeverity.CRITICAL, alert.getSeverity());
    }

    @Test
    @DisplayName("Alert: установка времени создания")
    void testAlertSetCreatedAt() {
        Alert alert = new Alert();
        LocalDateTime t = LocalDateTime.of(2026, 11, 10, 15, 0);
        alert.setCreatedAt(t);
        assertEquals(t, alert.getCreatedAt());
    }

    @Test
    @DisplayName("AudioRecord: дефолтный конструктор")
    void testAudioRecordDefaultConstructor() {
        AudioRecord rec = new AudioRecord();
        assertNull(rec.getId());
        assertNull(rec.getFilename());
    }

    @Test
    @DisplayName("AudioRecord: изменение полей")
    void testAudioRecordSetters() {
        AudioRecord rec = new AudioRecord();
        rec.setFilename("boat.wav");
        rec.setFilePath("/uploads/boat.wav");
        rec.setDetectedClass("boat_engine");
        rec.setConfidence(0.96);
        rec.setThreatLevel("HIGH");
        rec.setAnalysisSource("NeuralNet-YAMNet");

        assertEquals("boat.wav", rec.getFilename());
        assertEquals("/uploads/boat.wav", rec.getFilePath());
        assertEquals("boat_engine", rec.getDetectedClass());
        assertEquals(0.96, rec.getConfidence());
        assertEquals("HIGH", rec.getThreatLevel());
        assertEquals("NeuralNet-YAMNet", rec.getAnalysisSource());
    }

    @Test
    @DisplayName("Enum StationStatus: проверка ordinal и порядка")
    void testStationStatusOrdinal() {
        assertEquals(0, StationStatus.NORMAL.ordinal());
        assertEquals(1, StationStatus.WARNING.ordinal());
        assertEquals(2, StationStatus.CRITICAL.ordinal());
        assertEquals(3, StationStatus.OFFLINE.ordinal());
    }

    @Test
    @DisplayName("Enum QualityStatus: проверка ordinal и порядка")
    void testQualityStatusOrdinal() {
        assertEquals(0, QualityStatus.NORMAL.ordinal());
        assertEquals(1, QualityStatus.WARNING.ordinal());
        assertEquals(2, QualityStatus.CRITICAL.ordinal());
    }

    @Test
    @DisplayName("Enum AlertSeverity: проверка ordinal и порядка")
    void testAlertSeverityOrdinal() {
        assertEquals(0, AlertSeverity.INFO.ordinal());
        assertEquals(1, AlertSeverity.WARNING.ordinal());
        assertEquals(2, AlertSeverity.CRITICAL.ordinal());
    }

    @Test
    @DisplayName("Enum AlertType: проверка специфических типов")
    void testAlertTypeSpecificValues() {
        assertSame(AlertType.HYDRO_LOW_OXYGEN, AlertType.valueOf("HYDRO_LOW_OXYGEN"));
        assertSame(AlertType.HYDRO_HIGH_SALINITY, AlertType.valueOf("HYDRO_HIGH_SALINITY"));
        assertSame(AlertType.HYDRO_ABNORMAL_PH, AlertType.valueOf("HYDRO_ABNORMAL_PH"));
        assertSame(AlertType.HYDRO_HIGH_TURBIDITY, AlertType.valueOf("HYDRO_HIGH_TURBIDITY"));
        assertSame(AlertType.HYDRO_CRITICAL_LEVEL, AlertType.valueOf("HYDRO_CRITICAL_LEVEL"));
        assertSame(AlertType.SATELLITE_ANOMALY, AlertType.valueOf("SATELLITE_ANOMALY"));
        assertSame(AlertType.MANUAL_INSPECTION, AlertType.valueOf("MANUAL_INSPECTION"));
    }

    @Test
    @DisplayName("Record StationDto: проверка равенства")
    void testStationDtoEquals() {
        LocalDateTime now = LocalDateTime.now();
        StationDto d1 = new StationDto(1L, "C1", "N1", "T1", 45.0, 48.0, StationStatus.NORMAL, now, null);
        StationDto d2 = new StationDto(1L, "C1", "N1", "T1", 45.0, 48.0, StationStatus.NORMAL, now, null);
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    @DisplayName("Record WaterTelemetrySummaryDto: проверка равенства")
    void testWaterTelemetrySummaryDtoEquals() {
        LocalDateTime now = LocalDateTime.now();
        WaterTelemetrySummaryDto s1 = new WaterTelemetrySummaryDto(1L, now, 12.0, 1.0, 8.0, 7.5, 10.0, 250.0, QualityStatus.NORMAL);
        WaterTelemetrySummaryDto s2 = new WaterTelemetrySummaryDto(1L, now, 12.0, 1.0, 8.0, 7.5, 10.0, 250.0, QualityStatus.NORMAL);
        assertEquals(s1, s2);
    }

    @Test
    @DisplayName("Record WaterTelemetryRequest: проверка toString")
    void testWaterTelemetryRequestToString() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 7.0, 7.5, 10.0, 250.0, null);
        assertTrue(req.toString().contains("WaterTelemetryRequest"));
        assertTrue(req.toString().contains("12.0"));
    }

    @Test
    @DisplayName("Record WaterTelemetryResponse: проверка полей с алертом")
    void testWaterTelemetryResponseWithAlerts() {
        LocalDateTime now = LocalDateTime.now();
        WaterTelemetryResponse resp = new WaterTelemetryResponse(
            1L, 2L, "CODE-2", "Имя 2", now, 14.0, 1.0, 3.2, 7.4, 15.0, 240.0, QualityStatus.CRITICAL, 2
        );
        assertEquals(2, resp.generatedAlertsCount());
        assertEquals(QualityStatus.CRITICAL, resp.qualityStatus());
    }

    @Test
    @DisplayName("Record AlertDto: проверка разрешенного алерта")
    void testAlertDtoResolved() {
        LocalDateTime created = LocalDateTime.now().minusHours(1);
        LocalDateTime resolved = LocalDateTime.now();
        AlertDto dto = new AlertDto(
            1L, 2L, "CODE", "Буй", "Зона", 45.0, 48.0,
            AlertType.HYDRO_LOW_OXYGEN, AlertSeverity.CRITICAL, "msg", "det",
            created, true, resolved
        );
        assertTrue(dto.resolved());
        assertEquals(resolved, dto.resolvedAt());
    }

    @Test
    @DisplayName("Record SpawningForecastResponse: проверка карты гидрологии")
    void testSpawningForecastHydrologyMap() {
        Map<String, Object> map = Map.of("temp", 12.5, "salinity", 0.8);
        SpawningForecastResponse resp = new SpawningForecastResponse(
            "Осётр", "Дельта", LocalDateTime.now(), 90.0, "OPTIMAL",
            LocalDate.now(), LocalDate.now().plusDays(5), map, "Норма", "Патруль", "v1"
        );
        assertEquals(12.5, resp.hydrologySummary().get("temp"));
        assertEquals(0.8, resp.hydrologySummary().get("salinity"));
    }

    @Test
    @DisplayName("Record AudioAnalysisResponse: проверка звука без тревоги")
    void testAudioAnalysisResponseNoAlert() {
        AudioAnalysisResponse resp = new AudioAnalysisResponse(
            2L, 1L, "Буй 1", "ambient.wav", "ambient_noise", 0.99, "LOW", "AI", false, null, LocalDateTime.now()
        );
        assertFalse(resp.alertGenerated());
        assertNull(resp.alertId());
    }

    @Test
    @DisplayName("Record SystemStatusResponse: проверка свойств")
    void testSystemStatusResponseProperties() {
        SystemStatusResponse resp = new SystemStatusResponse(
            "EcoShield Core", "ONLINE", LocalDateTime.now(), 5, 200, 4, 1, true
        );
        assertEquals("EcoShield Core", resp.systemName());
        assertEquals(5, resp.stationCount());
        assertEquals(200, resp.telemetryRecordsCount());
        assertEquals(4, resp.totalAlertsCount());
        assertEquals(1, resp.activeAlertsCount());
    }

    @Test
    @DisplayName("Record CreateStationRequest: проверка координат")
    void testCreateStationRequestCoordinates() {
        CreateStationRequest req = new CreateStationRequest("NEW-BUOY", "Буй", "Каспий", 45.99, 48.88);
        assertEquals(45.99, req.latitude());
        assertEquals(48.88, req.longitude());
    }
}

