package com.example.demo;

import com.example.demo.dto.AlertDto;
import com.example.demo.dto.AudioAnalysisResponse;
import com.example.demo.dto.SpawningForecastResponse;
import com.example.demo.dto.StationDto;
import com.example.demo.model.AlertSeverity;
import com.example.demo.model.AlertType;
import com.example.demo.model.Station;
import com.example.demo.model.StationStatus;
import com.example.demo.service.AlertService;
import com.example.demo.service.AudioAnalysisService;
import com.example.demo.service.ForecastService;
import com.example.demo.service.StationService;
import org.junit.jupiter.api.DisplayName;
import com.example.demo.service.AiServiceClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Map;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BusinessLogicTests {

    @MockitoBean
    private AiServiceClient aiServiceClient;

    @Autowired
    private ForecastService forecastService;

    @Autowired
    private AudioAnalysisService audioService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private StationService stationService;




    private void acousticResult(String type, double score) {
        when(aiServiceClient.analyzeAudio(any(), anyString(), anyLong())).thenReturn(Map.of(
            "status", "events_detected", "model_version", "yamnet-test",
            "events", List.of(Map.of("type", type, "score", score)),
            "score_is_calibrated_probability", false));
    }

    @Test
    @DisplayName("Аудио: событие лодки от модели сохраняется и требует проверки")
    void testAudioDetectionBoatEngine() {
        acousticResult("boat_suspected", 0.41);
        AudioAnalysisResponse response = audioService.processAudioUpload(
            new MockMultipartFile("file", "neutral.wav", "audio/wav", new byte[]{1}), 3L);
        assertEquals("boat_suspected", response.detectedClass());
        assertEquals(0.41, response.confidence());
        assertEquals("REVIEW", response.threatLevel());
        assertTrue(response.alertGenerated());
        assertNotNull(response.alertId());
        var stored = audioService.getAllAudioRecords().stream().filter(r -> r.getId().equals(response.audioId())).findFirst().orElseThrow();
        assertEquals("boat_suspected", stored.getDetectedClass());
        assertTrue(stored.getAnalysisSource().contains("yamnet-test"));
    }

    @Test
    @DisplayName("Аудио: событие возможного выстрела создает алерт для проверки")
    void testAudioDetectionGunfire() {
        acousticResult("gunshot_suspected", 0.38);
        AudioAnalysisResponse response = audioService.processAudioUpload(
            new MockMultipartFile("file", "neutral.wav", "audio/wav", new byte[]{1}), 1L);
        assertEquals("gunshot_suspected", response.detectedClass());
        assertTrue(response.alertGenerated());
        assertEquals(AlertSeverity.WARNING, alertService.getAlertById(response.alertId()).severity());
    }

    @Test
    @DisplayName("Аудио: общий двигатель не превращается в лодку из имени файла")
    void testAudioDetectionGenericEngine() {
        acousticResult("engine_detected", 0.73);
        AudioAnalysisResponse response = audioService.processAudioUpload(
            new MockMultipartFile("file", "boat_gun.wav", "audio/wav", new byte[]{1}), 2L);
        assertEquals("engine_detected", response.detectedClass());
        assertEquals(0.73, response.confidence());
        assertFalse(response.alertGenerated());
    }

    @Test
    @DisplayName("Алерты: Создание ручного алерта инспектора")
    void testCreateManualAlert() {
        Station station = stationService.getStationById(1L);
        var alert = alertService.createAlert(
            station, AlertType.MANUAL_INSPECTION, AlertSeverity.INFO,
            "Плановый рейд инспекции", "Проверка сетей в Дамчикском рукаве"
        );

        assertNotNull(alert.getId());
        assertEquals(AlertType.MANUAL_INSPECTION, alert.getType());
        assertEquals(AlertSeverity.INFO, alert.getSeverity());
    }

    @Test
    @DisplayName("Алерты: Подсчет общего количества и активных алертов")
    void testAlertCounts() {
        long total = alertService.countTotalAlerts();
        long active = alertService.countActiveAlerts();
        assertTrue(total >= 0);
        assertTrue(active <= total);
    }

    @Test
    @DisplayName("Алерты: Фильтрация по статусу квитирования")
    void testAlertFilteringByResolved() {
        List<AlertDto> active = alertService.getAlerts(false, null, null);
        assertNotNull(active);
        for (AlertDto a : active) {
            assertFalse(a.resolved(), "Все элементы должны быть неразрешенными");
        }

        List<AlertDto> resolved = alertService.getAlerts(true, null, null);
        assertNotNull(resolved);
        for (AlertDto a : resolved) {
            assertTrue(a.resolved(), "Все элементы должны быть разрешенными");
        }
    }

    @Test
    @DisplayName("Станции: Получение станции по ID")
    void testGetStationById() {
        Station station = stationService.getStationById(1L);
        assertNotNull(station);
        assertEquals(1L, station.getId());
        assertNotNull(station.getCode());
    }

    @Test
    @DisplayName("Станции: Обновление статуса станции")
    void testUpdateStationStatus() {
        Station station = stationService.getStationById(4L);
        stationService.updateStationStatus(station, StationStatus.WARNING);

        StationDto updated = stationService.getStationDtoById(4L);
        assertEquals(StationStatus.WARNING, updated.status());
    }

    @Test
    @DisplayName("Станции: Наличие всех 5 базовых станций")
    void testAllFiveStationsExist() {
        List<StationDto> stations = stationService.getAllStations();
        assertTrue(stations.size() >= 5);
        assertTrue(stations.stream().anyMatch(s -> s.code().equals("BUOY-DAM-01")));
        assertTrue(stations.stream().anyMatch(s -> s.code().equals("BUOY-TRE-02")));
        assertTrue(stations.stream().anyMatch(s -> s.code().equals("BUOY-CASP-03")));
        assertTrue(stations.stream().anyMatch(s -> s.code().equals("BUOY-AGR-04")));
        assertTrue(stations.stream().anyMatch(s -> s.code().equals("BUOY-TUL-05")));
    }
}

