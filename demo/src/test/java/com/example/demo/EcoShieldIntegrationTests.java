package com.example.demo;

import com.example.demo.dto.*;
import com.example.demo.model.AlertSeverity;
import com.example.demo.model.StationStatus;
import com.example.demo.service.AlertService;
import com.example.demo.service.ForecastService;
import com.example.demo.service.StationService;
import com.example.demo.service.WaterTelemetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EcoShieldIntegrationTests {

    @Autowired
    private StationService stationService;

    @Autowired
    private WaterTelemetryService telemetryService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private ForecastService forecastService;

    @Test
    @DisplayName("Проверка инициализации станций ООПТ")
    void testInitialStations() {
        List<StationDto> stations = stationService.getAllStations();
        assertNotNull(stations);
        assertTrue(stations.size() >= 5, "Должно быть загружено как минимум 5 станций Каспийского бассейна");

        StationDto station1 = stations.get(0);
        assertNotNull(station1.code());
        assertNotNull(station1.latitude());
        assertNotNull(station1.longitude());
        assertNotNull(station1.latestTelemetry(), "Последние показатели воды должны присутствовать для маркера");
    }

    @Test
    @DisplayName("Прием телеметрии и автогенерация критического алерта по кислороду")
    void testTelemetryIngestionAndAnomalyDetection() {
        StationDto station2 = stationService.getAllStations().get(1);

        // Send telemetry with low dissolved oxygen (< 4.0 mg/L)
        WaterTelemetryRequest request = new WaterTelemetryRequest(
            station2.id(),
            12.5,
            0.6,
            3.2, // Critical low dissolved oxygen
            7.3,
            16.0,
            250.0,
            null
        );

        WaterTelemetryResponse response = telemetryService.processTelemetry(request);
        assertNotNull(response);
        assertEquals(station2.id(), response.stationId());
        assertTrue(response.generatedAlertsCount() >= 1, "Должен быть создан хотя бы один алерт");

        // Verify that station status became CRITICAL
        StationDto updatedStation = stationService.getStationDtoById(station2.id());
        assertEquals(StationStatus.CRITICAL, updatedStation.status());

        // Verify active alerts
        List<AlertDto> activeAlerts = alertService.getAlerts(false, AlertSeverity.CRITICAL, station2.id());
        assertFalse(activeAlerts.isEmpty());
    }

    @Test
    @DisplayName("Квитирование (подтверждение) алерта инспектором")
    void testResolveAlert() {
        List<AlertDto> alerts = alertService.getAlerts(false, null, null);
        assertFalse(alerts.isEmpty(), "В системе должны быть активные алерты");

        AlertDto firstAlert = alerts.get(0);
        AlertDto resolved = alertService.resolveAlert(firstAlert.id());

        assertTrue(resolved.resolved());
        assertNotNull(resolved.resolvedAt());
    }

}

