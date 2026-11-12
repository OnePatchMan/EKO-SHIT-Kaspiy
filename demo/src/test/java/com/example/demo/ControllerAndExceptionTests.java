package com.example.demo;

import com.example.demo.controller.*;
import com.example.demo.dto.*;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.ValidationException;
import com.example.demo.model.AlertSeverity;
import com.example.demo.model.StationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ControllerAndExceptionTests {

    @Autowired
    private StationController stationController;

    @Autowired
    private WaterTelemetryController telemetryController;

    @Autowired
    private AlertController alertController;

    @Autowired
    private ForecastController forecastController;

    @Autowired
    private SystemStatusController systemStatusController;

    @Autowired
    private AudioAnalysisController audioAnalysisController;

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Контроллер Station: GET /api/stations возвращает HTTP 200 и непустой список")
    void testStationControllerGetAll() {
        ResponseEntity<List<StationDto>> response = stationController.getAllStations();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 5);
    }

    @Test
    @DisplayName("Контроллер Station: GET /api/stations/{id} для существующей станции")
    void testStationControllerGetById() {
        ResponseEntity<StationDto> response = stationController.getStationById(1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().id());
    }

    @Test
    @DisplayName("Контроллер Telemetry: POST /api/telemetry/water/batch пакетный импорт")
    void testTelemetryBatchController() {
        WaterTelemetryRequest r1 = new WaterTelemetryRequest(1L, 12.0, 0.8, 8.0, 7.5, 12.0, 250.0, null);
        WaterTelemetryRequest r2 = new WaterTelemetryRequest(2L, 12.5, 0.5, 8.2, 7.6, 14.0, 260.0, null);
        ResponseEntity<List<WaterTelemetryResponse>> response = telemetryController.recordTelemetryBatch(List.of(r1, r2));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    @DisplayName("Контроллер Telemetry: GET /api/telemetry/water/station/{id}")
    void testTelemetryControllerGetHistory() {
        ResponseEntity<List<WaterTelemetryResponse>> response = telemetryController.getStationHistory(1L, 10);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    @DisplayName("Контроллер Telemetry: GET /api/telemetry/water/latest")
    void testTelemetryControllerGetLatest() {
        ResponseEntity<List<WaterTelemetryResponse>> response = telemetryController.getLatestForAllStations();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 5);
    }

    @Test
    @DisplayName("Контроллер Alert: GET /api/alerts")
    void testAlertControllerGetAlerts() {
        ResponseEntity<List<AlertDto>> response = alertController.getAlerts(null, null, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Контроллер Alert: GET /api/alerts/{id}")
    void testAlertControllerGetById() {
        List<AlertDto> alerts = alertController.getAlerts(null, null, null).getBody();
        assertNotNull(alerts);
        assertFalse(alerts.isEmpty());

        Long firstId = alerts.get(0).id();
        ResponseEntity<AlertDto> response = alertController.getAlertById(firstId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(firstId, response.getBody().id());
    }

    @Test
    @DisplayName("Контроллер Forecast: GET /api/forecast/spawning")
    void testForecastControllerGet() {
        ResponseEntity<SpawningForecastResponse> response = forecastController.getSpawningForecast("Русский осётр", 1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().suitabilityIndex() > 0.0);
    }

    @Test
    @DisplayName("Контроллер System: GET /api/system/health")
    void testSystemHealth() {
        ResponseEntity<Map<String, Object>> response = systemStatusController.health();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    @DisplayName("Контроллер System: GET /api/system/status")
    void testSystemStatus() {
        ResponseEntity<SystemStatusResponse> response = systemStatusController.status();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ONLINE", response.getBody().status());
        assertTrue(response.getBody().stationCount() >= 5);
    }

    @Test
    @DisplayName("ExceptionHandler: ResourceNotFoundException возвращает HTTP 404")
    void testHandleResourceNotFound() {
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleNotFound(
            new ResourceNotFoundException("Объект не найден")
        );
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().get("status"));
        assertEquals("Объект не найден", response.getBody().get("message"));
    }

    @Test
    @DisplayName("ExceptionHandler: ValidationException возвращает HTTP 400")
    void testHandleValidationException() {
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleValidation(
            new ValidationException("Некорректное поле")
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Некорректное поле", response.getBody().get("message"));
    }

    @Test
    @DisplayName("ExceptionHandler: General Exception возвращает HTTP 500")
    void testHandleGeneralException() {
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGeneral(
            new RuntimeException("Критический сбой")
        );
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().get("status"));
    }

    @Test
    @DisplayName("Контроллер Audio: GET /api/audio/devices возвращает список устройств ООПТ")
    void testAudioControllerGetDevices() {
        ResponseEntity<List<RecordingDeviceDto>> response = audioAnalysisController.getRecordingDevices();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 5);
        assertTrue(response.getBody().stream().anyMatch(d -> d.deviceId().equals("DEV-CASP-03")));
    }

    @Test
    @DisplayName("Контроллер Audio: GET /api/audio/devices/{deviceId} возвращает параметры комплекса")
    void testAudioControllerGetDeviceById() {
        ResponseEntity<RecordingDeviceDto> response = audioAnalysisController.getDeviceById("DEV-DAM-01");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DEV-DAM-01", response.getBody().deviceId());
        assertEquals("Гидроакустический комплекс ГАК-Дамчик-1", response.getBody().name());
    }

    @Test
    @DisplayName("Контроллер Audio: POST /api/audio/devices/{id}/capture прямой захват и анализ угрозы")
    void testAudioControllerCaptureFromDevice() {
        ResponseEntity<AudioAnalysisResponse> response = audioAnalysisController.captureFromDevice("DEV-DAM-01", "boat");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("boat_engine", response.getBody().detectedClass());
        assertTrue(response.getBody().alertGenerated());
    }

    @Test
    @DisplayName("Контроллер Audio: POST /api/audio/devices/{id}/toggle-sentry переключение режима сторожа")
    void testAudioControllerToggleSentry() {
        ResponseEntity<RecordingDeviceDto> response = audioAnalysisController.toggleSentry("DEV-AGR-04");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().sentryActive());
        assertEquals("STANDBY", response.getBody().status());
    }
}


