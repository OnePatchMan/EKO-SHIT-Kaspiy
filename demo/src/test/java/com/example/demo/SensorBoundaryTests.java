package com.example.demo;

import com.example.demo.dto.WaterTelemetryRequest;
import com.example.demo.exception.ValidationException;
import com.example.demo.service.WaterTelemetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SensorBoundaryTests {

    @Autowired
    private WaterTelemetryService telemetryService;

    @Test
    @DisplayName("Граница: Температура ровно -10.0°C (допустимо)")
    void testTemperatureMinExact() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, -10.0, 1.0, 8.0, 7.5, 10.0, 250.0, null);
        assertDoesNotThrow(() -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Температура ровно +50.0°C (допустимо)")
    void testTemperatureMaxExact() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 50.0, 1.0, 8.0, 7.5, 10.0, 250.0, null);
        assertDoesNotThrow(() -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Температура -10.1°C (недопустимо)")
    void testTemperatureBelowMin() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, -10.1, 1.0, 8.0, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Температура +50.1°C (недопустимо)")
    void testTemperatureAboveMax() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 50.1, 1.0, 8.0, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Солёность ровно 0.0 ‰ (допустимо)")
    void testSalinityMinExact() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 0.0, 8.0, 7.5, 10.0, 250.0, null);
        assertDoesNotThrow(() -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Солёность ровно 80.0 ‰ (допустимо)")
    void testSalinityMaxExact() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 80.0, 8.0, 7.5, 10.0, 250.0, null);
        assertDoesNotThrow(() -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Солёность -0.1 ‰ (недопустимо)")
    void testSalinityBelowMin() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, -0.1, 8.0, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Солёность 80.1 ‰ (недопустимо)")
    void testSalinityAboveMax() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 80.1, 8.0, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Кислород ровно 0.0 мг/л (допустимо)")
    void testOxygenMinExact() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 0.0, 7.5, 10.0, 250.0, null);
        assertDoesNotThrow(() -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Кислород ровно 30.0 мг/л (допустимо)")
    void testOxygenMaxExact() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 30.0, 7.5, 10.0, 250.0, null);
        assertDoesNotThrow(() -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Кислород -0.1 мг/л (недопустимо)")
    void testOxygenBelowMin() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, -0.1, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Кислород 30.1 мг/л (недопустимо)")
    void testOxygenAboveMax() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 30.1, 7.5, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: pH ровно 0.0 (допустимо)")
    void testPhMinExact() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 8.0, 0.0, 10.0, 250.0, null);
        assertDoesNotThrow(() -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: pH ровно 14.0 (допустимо)")
    void testPhMaxExact() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 8.0, 14.0, 10.0, 250.0, null);
        assertDoesNotThrow(() -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: pH -0.1 (недопустимо)")
    void testPhBelowMin() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 8.0, -0.1, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: pH 14.1 (недопустимо)")
    void testPhAboveMax() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 8.0, 14.1, 10.0, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Мутность ровно 0.0 NTU (допустимо)")
    void testTurbidityMinExact() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 8.0, 7.5, 0.0, 250.0, null);
        assertDoesNotThrow(() -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Мутность ровно 1000.0 NTU (допустимо)")
    void testTurbidityMaxExact() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 8.0, 7.5, 1000.0, 250.0, null);
        assertDoesNotThrow(() -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Мутность -0.1 NTU (недопустимо)")
    void testTurbidityBelowMin() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 8.0, 7.5, -0.1, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Граница: Мутность 1000.1 NTU (недопустимо)")
    void testTurbidityAboveMax() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 1.0, 8.0, 7.5, 1000.1, 250.0, null);
        assertThrows(ValidationException.class, () -> telemetryService.processTelemetry(req));
    }

    @Test
    @DisplayName("Аномалия: Кислород 3.9 мг/л порождает CRITICAL алерт")
    void testOxygen3_9IsCritical() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(2L, 12.0, 0.5, 3.9, 7.5, 15.0, 250.0, null);
        var resp = telemetryService.processTelemetry(req);
        assertTrue(resp.generatedAlertsCount() >= 1);
    }

    @Test
    @DisplayName("Аномалия: Кислород 4.5 мг/л порождает WARNING алерт")
    void testOxygen4_5IsWarning() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(2L, 12.0, 0.5, 4.5, 7.5, 15.0, 250.0, null);
        var resp = telemetryService.processTelemetry(req);
        assertTrue(resp.generatedAlertsCount() >= 1);
    }

    @Test
    @DisplayName("Аномалия: Кислород 6.5 мг/л - норма (нет алерта по кислороду)")
    void testOxygen6_5IsNormal() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(2L, 12.0, 0.5, 6.5, 7.5, 15.0, 250.0, null);
        var resp = telemetryService.processTelemetry(req);
        assertEquals(0, resp.generatedAlertsCount());
    }

    @Test
    @DisplayName("Аномалия: Солёность 9.5 ‰ в дельте порождает алерт")
    void testSalinity9_5InDelta() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(1L, 12.0, 9.5, 8.0, 7.5, 15.0, 250.0, null);
        var resp = telemetryService.processTelemetry(req);
        assertTrue(resp.generatedAlertsCount() >= 1);
    }

    @Test
    @DisplayName("Аномалия: pH 5.8 порождает алерт кислотности")
    void testPh5_8IsAcidAlert() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(2L, 12.0, 0.5, 7.5, 5.8, 15.0, 250.0, null);
        var resp = telemetryService.processTelemetry(req);
        assertTrue(resp.generatedAlertsCount() >= 1);
    }

    @Test
    @DisplayName("Аномалия: pH 9.2 порождает алерт защелачивания")
    void testPh9_2IsAlkalineAlert() {
        WaterTelemetryRequest req = new WaterTelemetryRequest(2L, 12.0, 0.5, 7.5, 9.2, 15.0, 250.0, null);
        var resp = telemetryService.processTelemetry(req);
        assertTrue(resp.generatedAlertsCount() >= 1);
    }
}

