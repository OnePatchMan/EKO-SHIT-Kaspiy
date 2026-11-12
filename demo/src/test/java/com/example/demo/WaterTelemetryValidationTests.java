package com.example.demo;

import com.example.demo.dto.CreateStationRequest;
import com.example.demo.dto.WaterTelemetryRequest;
import com.example.demo.exception.ValidationException;
import com.example.demo.service.StationService;
import com.example.demo.service.WaterTelemetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WaterTelemetryValidationTests {

    @Autowired
    private WaterTelemetryService telemetryService;

    @Autowired
    private StationService stationService;

    @Test
    @DisplayName("Валидация: Отсутствие stationId вызывает ValidationException")
    void testNullStationIdThrows() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(null, 12.0, 1.0, 7.0, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Валидация: Температура ниже -10°C вызывает ошибку")
    void testTemperatureTooLowThrows() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, -15.0, 1.0, 7.0, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Валидация: Температура выше 50°C вызывает ошибку")
    void testTemperatureTooHighThrows() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 55.0, 1.0, 7.0, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Валидация: Отрицательная солёность вызывает ошибку")
    void testNegativeSalinityThrows() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, -2.0, 7.0, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Валидация: Солёность выше 80 ‰ вызывает ошибку")
    void testSalinityTooHighThrows() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 95.0, 7.0, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Валидация: Отрицательный кислород вызывает ошибку")
    void testNegativeOxygenThrows() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, -1.5, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Валидация: Кислород выше 30 мг/л вызывает ошибку")
    void testOxygenTooHighThrows() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 35.0, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Валидация: pH ниже 0 вызывает ошибку")
    void testPhTooLowThrows() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 7.0, -0.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Валидация: pH выше 14 вызывает ошибку")
    void testPhTooHighThrows() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 7.0, 15.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Валидация: Мутность выше 1000 NTU вызывает ошибку")
    void testTurbidityTooHighThrows() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 7.0, 7.5, 1200.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Валидация станции: Пустой код вызывает ошибку")
    void testCreateStationEmptyCodeThrows() {
        CreateStationRequest req = new CreateStationRequest("", "Буй", "Зона", 45.0, 48.0);
        assertThrows(ValidationException.class, () -> stationService.createStation(req));
    }

    @Test
    @DisplayName("Валидация станции: Пустое название вызывает ошибку")
    void testCreateStationEmptyNameThrows() {
        CreateStationRequest req = new CreateStationRequest("CODE-VALID", "", "Зона", 45.0, 48.0);
        assertThrows(ValidationException.class, () -> stationService.createStation(req));
    }

    @Test
    @DisplayName("Валидация станции: Повторный код вызывает ошибку уникальности")
    void testCreateStationDuplicateCodeThrows() {
        CreateStationRequest req = new CreateStationRequest("BUOY-DAM-01", "Дубликат", "Зона", 45.0, 48.0);
        assertThrows(ValidationException.class, () -> stationService.createStation(req));
    }

    @Test
    @DisplayName("Детекция аномалий: Высокая мутность генерирует INFO алерт")
    void testHighTurbidityGeneratesAlert() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(2L, 12.0, 0.5, 7.5, 7.5, 75.0, 260.0, null);
        var resp = telemetryService.processTelemetry(req);
        assertTrue(resp.generatedAlertsCount() >= 1);
    }

    @Test
    @DisplayName("Детекция аномалий: Критическое обмеление (<130 см) генерирует WARNING алерт")
    void testLowWaterLevelGeneratesAlert() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(2L, 12.0, 0.5, 7.5, 7.5, 15.0, 110.0, null);
        var resp = telemetryService.processTelemetry(req);
        assertTrue(resp.generatedAlertsCount() >= 1);
    }

    @Test
    @DisplayName("Детекция аномалий: Выход pH за пределы (5.5) генерирует алерт")
    void testAbnormalPhGeneratesAlert() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(2L, 12.0, 0.5, 7.5, 5.5, 15.0, 260.0, null);
        var resp = telemetryService.processTelemetry(req);
        assertTrue(resp.generatedAlertsCount() >= 1);
    }
}

