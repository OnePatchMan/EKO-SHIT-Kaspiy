package com.example.demo;

import com.example.demo.dto.AlertDto;
import com.example.demo.dto.AudioAnalysisResponse;
import com.example.demo.dto.SpawningForecastResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.service.AlertService;
import com.example.demo.service.AudioAnalysisService;
import com.example.demo.service.ForecastService;
import com.example.demo.service.StationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AlgorithmAndHeuristicTests {

    @Autowired
    private AudioAnalysisService audioService;

    @Autowired
    private ForecastService forecastService;

    @Autowired
    private StationService stationService;

    @Autowired
    private AlertService alertService;

    @Test
    @DisplayName("Исключения: Несуществующая станция вызывает ResourceNotFoundException")
    void testGetNonExistentStationThrows() {
        assertThrows(ResourceNotFoundException.class, () -> stationService.getStationById(99999L));
    }

    @Test
    @DisplayName("Исключения: Несуществующий алерт при резолве вызывает ResourceNotFoundException")
    void testResolveNonExistentAlertThrows() {
        assertThrows(ResourceNotFoundException.class, () -> alertService.resolveAlert(99999L));
    }

    @Test
    @DisplayName("Исключения: Несуществующий алерт при поиске по ID вызывает ResourceNotFoundException")
    void testGetNonExistentAlertThrows() {
        assertThrows(ResourceNotFoundException.class, () -> alertService.getAlertById(99999L));
    }

    @Test
    @DisplayName("Аудио сервис: Список всех записей не null")
    void testGetAllAudioRecordsNotNull() {
        assertNotNull(audioService.getAllAudioRecords());
    }

    @Test
    @DisplayName("Алерты: Проверка правильного сообщения у сгенерированного алерта")
    void testAlertMessageContent() {
        var alerts = alertService.getAlerts(null, null, null);
        assertFalse(alerts.isEmpty());
        AlertDto first = alerts.get(0);
        assertNotNull(first.message());
        assertFalse(first.message().isBlank());
    }

    @Test
    @DisplayName("Станции: Проверка уникальности кодов всех станций")
    void testUniqueStationCodes() {
        var stations = stationService.getAllStations();
        long uniqueCodes = stations.stream().map(s -> s.code()).distinct().count();
        assertEquals(stations.size(), uniqueCodes);
    }

    @Test
    @DisplayName("Станции: Проверка наличия координат у всех станций")
    void testAllStationsHaveCoordinates() {
        var stations = stationService.getAllStations();
        for (var s : stations) {
            assertNotNull(s.latitude());
            assertNotNull(s.longitude());
            assertTrue(s.latitude() > 40.0 && s.latitude() < 50.0);
            assertTrue(s.longitude() > 45.0 && s.longitude() < 55.0);
        }
    }
}
