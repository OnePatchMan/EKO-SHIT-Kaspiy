package com.example.demo.service;

import com.example.demo.dto.AudioAnalysisResponse;
import com.example.demo.dto.RecordingDeviceDto;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.AiServiceException;
import com.example.demo.model.*;
import com.example.demo.repository.AudioRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioAnalysisService {

    private final AudioRecordRepository audioRecordRepository;
    private final StationService stationService;
    private final AlertService alertService;
    private final AiServiceClient aiServiceClient;

    @Value("${ecoshield.upload-dir:uploads}")
    private String uploadDir;

    // In-memory state of active field recording devices on buoys
    private final Map<String, DeviceInternalState> deviceRegistry = new ConcurrentHashMap<>();

    private static class DeviceInternalState {
        String deviceId;
        String name;
        Long stationId;
        String stationCode;
        String stationName;
        String status;
        int batteryPercent;
        String signalStrength;
        String hydrophoneModel;
        double ambientNoiseDb;
        String defaultSampleFile;
        String currentSampleFile;
        boolean sentryActive;

        DeviceInternalState(String deviceId, String name, Long stationId, String stationCode, String stationName,
                            String status, int batteryPercent, String signalStrength, String hydrophoneModel,
                            double ambientNoiseDb, String defaultSampleFile) {
            this.deviceId = deviceId;
            this.name = name;
            this.stationId = stationId;
            this.stationCode = stationCode;
            this.stationName = stationName;
            this.status = status;
            this.batteryPercent = batteryPercent;
            this.signalStrength = signalStrength;
            this.hydrophoneModel = hydrophoneModel;
            this.ambientNoiseDb = ambientNoiseDb;
            this.defaultSampleFile = defaultSampleFile;
            this.currentSampleFile = defaultSampleFile;
            this.sentryActive = true;
        }
    }

    public AudioAnalysisService(AudioRecordRepository audioRecordRepository,
                                StationService stationService,
                                AlertService alertService,
                                AiServiceClient aiServiceClient) {
        this.audioRecordRepository = audioRecordRepository;
        this.stationService = stationService;
        this.alertService = alertService;
        this.aiServiceClient = aiServiceClient;

        initDeviceRegistry();
    }

    private void initDeviceRegistry() {
        deviceRegistry.put("DEV-DAM-01", new DeviceInternalState(
            "DEV-DAM-01", "Гидроакустический комплекс ГАК-Дамчик-1",
            1L, "BUOY-DAM-01", "Буй Дамчикский №1",
            "ONLINE", 94, "-64 dBm (LoRaWAN/4G)", "Cetacean Research C57 Dual-Array", 38.2, "ambient_caspian_waves.wav"
        ));
        deviceRegistry.put("DEV-TRE-02", new DeviceInternalState(
            "DEV-TRE-02", "Биоакустический регистратор БАР-Трёхизбинка-2",
            2L, "BUOY-TRE-02", "Буй Трёхизбинский №2",
            "ONLINE", 87, "-72 dBm (LoRaWAN)", "Aquarian Audio H2d Hydrophone", 41.0, "sturgeon_water_splash.wav"
        ));
        deviceRegistry.put("DEV-CASP-03", new DeviceInternalState(
            "DEV-CASP-03", "Дальнобойный гидрофон ВКМСК-145",
            3L, "BUOY-CASP-03", "Морской буй 'Северный Каспий' №3",
            "ONLINE", 91, "-58 dBm (Спутниковый канал Иридиум)", "Teledyne RESON TC4032 Marine", 46.5, "ambient_caspian_waves.wav"
        ));
        deviceRegistry.put("DEV-AGR-04", new DeviceInternalState(
            "DEV-AGR-04", "Акустический регистратор Кизляр-Аграхан-4",
            4L, "BUOY-AGR-04", "Станция 'Аграханский залив' №4",
            "ONLINE", 82, "-76 dBm (LoRaWAN)", "Cetacean SQ26-08 Acoustic Array", 39.5, "ambient_caspian_waves.wav"
        ));
        deviceRegistry.put("DEV-TUL-05", new DeviceInternalState(
            "DEV-TUL-05", "Шельфовый гидроакустический пост МГ-2 Тюлений",
            5L, "BUOY-TUL-05", "Морской буй 'Остров Тюлений' №5",
            "ONLINE", 96, "-69 dBm (Морской УКВ/Спутник)", "Ocean Sonics icListen HF Smart", 36.8, "ambient_caspian_waves.wav"
        ));
    }

    public List<RecordingDeviceDto> getRecordingDevices() {
        return deviceRegistry.values().stream()
            .map(this::toDeviceDto)
            .toList();
    }

    public RecordingDeviceDto getDeviceById(String deviceId) {
        DeviceInternalState dev = deviceRegistry.get(deviceId);
        if (dev == null) {
            throw new ResourceNotFoundException("Записывающее устройство " + deviceId + " не зарегистрировано в сети ООПТ");
        }
        return toDeviceDto(dev);
    }

    public RecordingDeviceDto toggleSentry(String deviceId) {
        DeviceInternalState dev = deviceRegistry.get(deviceId);
        if (dev == null) {
            throw new ResourceNotFoundException("Устройство " + deviceId + " не найдено");
        }
        dev.sentryActive = !dev.sentryActive;
        dev.status = dev.sentryActive ? "ONLINE" : "STANDBY";
        return toDeviceDto(dev);
    }

    @Transactional
    public AudioAnalysisResponse captureFromDevice(String deviceId, String soundScenario) {
        DeviceInternalState dev = deviceRegistry.get(deviceId);
        if (dev == null) {
            throw new ResourceNotFoundException("Записывающее устройство " + deviceId + " не найдено");
        }

        Station station = stationService.getStationById(dev.stationId);

        String sampleName = dev.defaultSampleFile;
        if (soundScenario != null && !soundScenario.isBlank()) {
            sampleName = switch (soundScenario.toLowerCase()) {
                case "boat", "motor", "лодка" -> "boat_engine_outboard.wav";
                case "shot", "gun", "выстрел" -> "poaching_gunfire_shot.wav";
                case "splash", "fish", "рыба" -> "sturgeon_water_splash.wav";
                case "ambient", "waves", "шум" -> "ambient_caspian_waves.wav";
                default -> dev.defaultSampleFile;
            };
        }

        // Live acoustic state update on this field hydrophone
        dev.currentSampleFile = sampleName;

        byte[] audioBytes = getDeviceAudioStream(deviceId, soundScenario);
        Map<String,Object> details = aiServiceClient.analyzeAudio(audioBytes, sampleName, station.getId());
        AiInferenceResult inference = mapInference(details);
        return saveAnalysis(station, sampleName, "/samples/" + sampleName, inference, details);
    }

    public byte[] getDeviceAudioStream(String deviceId) {
        return getDeviceAudioStream(deviceId, null);
    }

    public byte[] getDeviceAudioStream(String deviceId, String scenario) {
        DeviceInternalState dev = deviceRegistry.get(deviceId);
        String filename;

        if (scenario != null && !scenario.isBlank()) {
            filename = switch (scenario.toLowerCase()) {
                case "boat", "motor", "лодка" -> "boat_engine_outboard.wav";
                case "shot", "gun", "выстрел" -> "poaching_gunfire_shot.wav";
                case "splash", "fish", "рыба" -> "sturgeon_water_splash.wav";
                case "ambient", "waves", "шум" -> "ambient_caspian_waves.wav";
                default -> (dev != null && dev.currentSampleFile != null) ? dev.currentSampleFile : "ambient_caspian_waves.wav";
            };
            if (dev != null) {
                dev.currentSampleFile = filename;
            }
        } else {
            filename = (dev != null && dev.currentSampleFile != null) 
                ? dev.currentSampleFile 
                : (dev != null ? dev.defaultSampleFile : "ambient_caspian_waves.wav");
        }

        // Try reading from file path or classpath
        try {
            Path[] candidatePaths = {
                Paths.get("demo/src/main/resources/static/samples", filename),
                Paths.get("src/main/resources/static/samples", filename),
                Paths.get("demo/target/classes/static/samples", filename),
                Paths.get("target/classes/static/samples", filename),
                Paths.get("data/audio", filename),
                Paths.get("../data/audio", filename)
            };
            for (Path p : candidatePaths) {
                if (Files.exists(p) && Files.size(p) > 0) {
                    return Files.readAllBytes(p);
                }
            }
            ClassPathResource cp = new ClassPathResource("static/samples/" + filename);
            if (cp.exists()) {
                try (InputStream stream = cp.getInputStream()) {
                    return stream.readAllBytes();
                }
            }
        } catch (IOException e) {
            // fallback
        }
        return new byte[0];
    }

    @Transactional
    public AudioAnalysisResponse processAudioUpload(MultipartFile file, Long stationId) {
        Station station = stationService.getStationById(stationId);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) originalFilename = "recording.wav";
        originalFilename = Paths.get(originalFilename.replace('\\', '/')).getFileName().toString();
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException error) {
            throw new AiServiceException(422, "Не удалось прочитать аудиозапись.", error);
        }
        Map<String,Object> details = aiServiceClient.analyzeAudio(data, originalFilename, stationId);
        AiInferenceResult inference = mapInference(details);
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        try {
            Path uploadPath = Paths.get(uploadDir, "audio");
            Files.createDirectories(uploadPath);
            Files.write(uploadPath.resolve(storedFilename), data);
        } catch (IOException error) {
            throw new java.io.UncheckedIOException("Не удалось сохранить аудиозапись.", error);
        }
        return saveAnalysis(station, originalFilename, storedFilename, inference, details);
    }

    private AudioAnalysisResponse saveAnalysis(Station station, String filename, String storedFilename,
            AiInferenceResult inference, Map<String,Object> details) {
        AudioRecord record = new AudioRecord(station, filename, storedFilename,
            inference.detectedClass(), inference.score(), inference.threatLevel(), inference.source());
        record.setAnalysisDetailsJson(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(details));
        AudioRecord saved = audioRecordRepository.save(record);
        Long alertId = null;
        if (inference.requiresReview()) {
            AlertType type = inference.detectedClass().equals("gunshot_suspected")
                ? AlertType.ACOUSTIC_GUNFIRE : AlertType.ACOUSTIC_BOAT_DETECTED;
            String label = inference.detectedClass().equals("gunshot_suspected") ? "возможный выстрел" : "возможная лодка";
            String message = String.format(java.util.Locale.ROOT,
                "Акустическое событие: %s; оценка модели %.3f. Требуется проверка.", label, inference.score());
            Alert alert = alertService.createAlert(station, type, AlertSeverity.WARNING, message,
                "Станция " + station.getName() + ". Оценка YAMNet не является калиброванной вероятностью. Звук сам по себе не устанавливает браконьерство.");
            alertId = alert.getId();
            stationService.updateStationStatus(station, StationStatus.WARNING);
        }
        return new AudioAnalysisResponse(saved.getId(), station.getId(), station.getName(), filename,
            inference.detectedClass(), inference.score(), inference.threatLevel(), inference.source(),
            inference.requiresReview(), alertId, saved.getCreatedAt(), details);
    }

    @Transactional(readOnly = true)
    public List<AudioRecord> getAllAudioRecords() {
        return audioRecordRepository.findAllByOrderByCreatedAtDesc();
    }

    private RecordingDeviceDto toDeviceDto(DeviceInternalState s) {
        String streamUrl = "/api/audio/devices/" + s.deviceId + "/stream";
        return new RecordingDeviceDto(
            s.deviceId,
            s.name,
            s.stationId,
            s.stationCode,
            s.stationName,
            s.status,
            s.batteryPercent,
            s.signalStrength,
            s.hydrophoneModel,
            s.ambientNoiseDb,
            streamUrl,
            s.sentryActive
        );
    }

    private AiInferenceResult mapInference(Map<String,Object> response) {
        if (response == null || !(response.get("status") instanceof String status)
                || !Set.of("events_detected", "no_target_event", "uncertain").contains(status)
                || !(response.get("model_version") instanceof String version) || version.isBlank()
                || !(response.get("events") instanceof List<?> events)) {
            throw new AiServiceException(503, "Сервис ИИ вернул некорректную структуру анализа.");
        }
        String source = "YAMNet; " + version + "; " + status + "; score is uncalibrated";
        if (source.length() > 255) source = source.substring(0, 255);
        if (events.isEmpty()) {
            if (status.equals("events_detected")) throw new AiServiceException(503, "Сервис ИИ не передал обнаруженные события.");
            return new AiInferenceResult(status, null, status.equals("uncertain") ? "UNKNOWN" : "NONE", source, false);
        }
        if (!status.equals("events_detected")) throw new AiServiceException(503, "Противоречивый статус анализа ИИ.");
        AiInferenceResult selected = null;
        for (Object item : events) {
            if (!(item instanceof Map<?,?> event) || !(event.get("type") instanceof String type)
                    || !Set.of("boat_suspected", "gunshot_suspected", "engine_detected").contains(type)
                    || !(event.get("score") instanceof Number value)) {
                throw new AiServiceException(503, "Сервис ИИ вернул некорректное акустическое событие.");
            }
            double score = value.doubleValue();
            if (!Double.isFinite(score) || score < 0 || score > 1) {
                throw new AiServiceException(503, "Сервис ИИ вернул некорректную оценку события.");
            }
            boolean review = !type.equals("engine_detected");
            AiInferenceResult candidate = new AiInferenceResult(type, score, review ? "REVIEW" : "LOW", source, review);
            if (selected == null || (review && !selected.requiresReview())
                    || (review == selected.requiresReview() && score > selected.score())) selected = candidate;
        }
        return selected;
    }

    private record AiInferenceResult(String detectedClass, Double score, String threatLevel, String source, boolean requiresReview) {}
}
