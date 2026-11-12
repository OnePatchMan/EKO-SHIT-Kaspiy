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
    @DisplayName("Аудио эвристика: ключевое слово 'motor'")
    void testKeywordMotor() {
        MockMultipartFile file = new MockMultipartFile("file", "recording_motor_rpm.wav", "audio/wav", "data".getBytes());
        AudioAnalysisResponse res = audioService.processAudioUpload(file, 1L);
        assertEquals("boat_engine", res.detectedClass());
    }

    @Test
    @DisplayName("Аудио эвристика: ключевое слово 'двигатель'")
    void testKeywordDvigatel() {
        MockMultipartFile file = new MockMultipartFile("file", "шум_двигателя_ночь.wav", "audio/wav", "data".getBytes());
        AudioAnalysisResponse res = audioService.processAudioUpload(file, 1L);
        assertEquals("boat_engine", res.detectedClass());
    }

    @Test
    @DisplayName("Аудио эвристика: ключевое слово 'лодка'")
    void testKeywordLodka() {
        MockMultipartFile file = new MockMultipartFile("file", "лодка_браконьера.wav", "audio/wav", "data".getBytes());
        AudioAnalysisResponse res = audioService.processAudioUpload(file, 1L);
        assertEquals("boat_engine", res.detectedClass());
    }

    @Test
    @DisplayName("Аудио эвристика: ключевое слово 'shot'")
    void testKeywordShot() {
        MockMultipartFile file = new MockMultipartFile("file", "shot_acoustic.wav", "audio/wav", "data".getBytes());
        AudioAnalysisResponse res = audioService.processAudioUpload(file, 1L);
        assertEquals("gunfire", res.detectedClass());
        assertEquals("CRITICAL", res.threatLevel());
    }

    @Test
    @DisplayName("Аудио эвристика: ключевое слово 'gun'")
    void testKeywordGun() {
        MockMultipartFile file = new MockMultipartFile("file", "gun_report_sample.wav", "audio/wav", "data".getBytes());
        AudioAnalysisResponse res = audioService.processAudioUpload(file, 1L);
        assertEquals("gunfire", res.detectedClass());
    }

    @Test
    @DisplayName("Аудио эвристика: ключевое слово 'выстрел'")
    void testKeywordVystrel() {
        MockMultipartFile file = new MockMultipartFile("file", "ночной_выстрел_рукав.wav", "audio/wav", "data".getBytes());
        AudioAnalysisResponse res = audioService.processAudioUpload(file, 1L);
        assertEquals("gunfire", res.detectedClass());
    }

    @Test
    @DisplayName("Аудио эвристика: ключевое слово 'splash'")
    void testKeywordSplash() {
        MockMultipartFile file = new MockMultipartFile("file", "sturgeon_splash_water.wav", "audio/wav", "data".getBytes());
        AudioAnalysisResponse res = audioService.processAudioUpload(file, 1L);
        assertEquals("fish_splash", res.detectedClass());
        assertEquals("LOW", res.threatLevel());
    }

    @Test
    @DisplayName("Аудио эвристика: ключевое слово 'рыба'")
    void testKeywordRyba() {
        MockMultipartFile file = new MockMultipartFile("file", "рыба_нерестилище.wav", "audio/wav", "data".getBytes());
        AudioAnalysisResponse res = audioService.processAudioUpload(file, 1L);
        assertEquals("fish_splash", res.detectedClass());
    }

    @Test
    @DisplayName("Аудио эвристика: ключевое слово 'плеск'")
    void testKeywordPlesk() {
        MockMultipartFile file = new MockMultipartFile("file", "плеск_каспийского_тюленя.wav", "audio/wav", "data".getBytes());
        AudioAnalysisResponse res = audioService.processAudioUpload(file, 1L);
        assertEquals("fish_splash", res.detectedClass());
    }

    @Test
    @DisplayName("Прогноз нереста: Передача null вида использует Русского осетра по умолчанию")
    void testForecastNullSpecies() {
        SpawningForecastResponse res = forecastService.calculateSpawningForecast(null, 1L);
        assertEquals("Русский осётр (Acipenser gueldenstaedtii)", res.species());
    }

    @Test
    @DisplayName("Прогноз нереста: Пустая строка вида использует Русского осетра по умолчанию")
    void testForecastBlankSpecies() {
        SpawningForecastResponse res = forecastService.calculateSpawningForecast("   ", 1L);
        assertEquals("Русский осётр (Acipenser gueldenstaedtii)", res.species());
    }

    @Test
    @DisplayName("Прогноз нереста: Передача null stationId использует первую доступную станцию")
    void testForecastNullStationId() {
        SpawningForecastResponse res = forecastService.calculateSpawningForecast("Севрюга", null);
        assertNotNull(res.targetZone());
        assertTrue(res.suitabilityIndex() > 0.0);
    }

    @Test
    @DisplayName("Прогноз нереста: Индекс пригодности не превышает 100%")
    void testForecastSuitabilityUpperCap() {
        SpawningForecastResponse res = forecastService.calculateSpawningForecast("Русский осётр", 2L);
        assertTrue(res.suitabilityIndex() <= 100.0);
    }

    @Test
    @DisplayName("Прогноз нереста: Индекс пригодности не опускается ниже 0%")
    void testForecastSuitabilityLowerCap() {
        SpawningForecastResponse res = forecastService.calculateSpawningForecast("Русский осётр", 5L);
        assertTrue(res.suitabilityIndex() >= 0.0);
    }

    @Test
    @DisplayName("Прогноз нереста: Наличие версии модели в ответе")
    void testForecastModelVersionPresent() {
        SpawningForecastResponse res = forecastService.calculateSpawningForecast("Белуга", 1L);
        assertNotNull(res.modelVersion());
        assertTrue(res.modelVersion().contains("Caspian Eco-Predictor"));
    }

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
