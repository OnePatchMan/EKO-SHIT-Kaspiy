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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BusinessLogicTests {

    @Autowired
    private ForecastService forecastService;

    @Autowired
    private AudioAnalysisService audioService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private StationService stationService;

    @Test
    @DisplayName("Прогноз нереста: Корректный расчет для Русского осетра")
    void testForecastRussianSturgeon() {
        SpawningForecastResponse forecast = forecastService.calculateSpawningForecast(
            "Русский осётр (Acipenser gueldenstaedtii)", 1L
        );
        assertNotNull(forecast);
        assertEquals("Русский осётр (Acipenser gueldenstaedtii)", forecast.species());
        assertTrue(forecast.suitabilityIndex() > 0.0);
        assertNotNull(forecast.patrolRecommendation());
        assertNotNull(forecast.windowStart());
        assertNotNull(forecast.windowEnd());
    }

    @Test
    @DisplayName("Прогноз нереста: Корректный расчет для Севрюги")
    void testForecastSevruga() {
        SpawningForecastResponse forecast = forecastService.calculateSpawningForecast(
            "Севрюга (Acipenser stellatus)", 2L
        );
        assertNotNull(forecast);
        assertEquals("Севрюга (Acipenser stellatus)", forecast.species());
        assertTrue(forecast.suitabilityIndex() >= 10.0);
    }

    @Test
    @DisplayName("Прогноз нереста: Корректный расчет для Белуги")
    void testForecastBeluga() {
        SpawningForecastResponse forecast = forecastService.calculateSpawningForecast(
            "Белуга (Huso huso)", 3L
        );
        assertNotNull(forecast);
        assertEquals("Белуга (Huso huso)", forecast.species());
    }

    @Test
    @DisplayName("Аудио ИИ: Детекция подвесного лодочного мотора по аудиофайлу")
    void testAudioDetectionBoatEngine() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "hydrophone_boat_motor_high_rpm.wav", "audio/wav", "dummy bytes".getBytes()
        );

        AudioAnalysisResponse response = audioService.processAudioUpload(file, 3L);
        assertNotNull(response);
        assertEquals("boat_engine", response.detectedClass());
        assertEquals("HIGH", response.threatLevel());
        assertTrue(response.alertGenerated());
        assertNotNull(response.alertId());
    }

    @Test
    @DisplayName("Аудио ИИ: Детекция выстрела браконьера (gunfire)")
    void testAudioDetectionGunfire() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "audio_gunfire_shot_detected.wav", "audio/wav", "dummy gunshot bytes".getBytes()
        );

        AudioAnalysisResponse response = audioService.processAudioUpload(file, 1L);
        assertNotNull(response);
        assertEquals("gunfire", response.detectedClass());
        assertEquals("CRITICAL", response.threatLevel());
        assertTrue(response.alertGenerated());
    }

    @Test
    @DisplayName("Аудио ИИ: Детекция плеска рыбы / фонового шума")
    void testAudioDetectionFishSplash() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "sturgeon_splash_water.wav", "audio/wav", "splash bytes".getBytes()
        );

        AudioAnalysisResponse response = audioService.processAudioUpload(file, 2L);
        assertNotNull(response);
        assertEquals("fish_splash", response.detectedClass());
        assertEquals("LOW", response.threatLevel());
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

