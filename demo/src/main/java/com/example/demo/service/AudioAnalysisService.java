package com.example.demo.service;

import com.example.demo.dto.AudioAnalysisResponse;
import com.example.demo.dto.RecordingDeviceDto;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.*;
import com.example.demo.repository.AudioRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AudioAnalysisService {

    private final AudioRecordRepository audioRecordRepository;
    private final StationService stationService;
    private final AlertService alertService;

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
            this.sentryActive = true;
        }
    }

    public AudioAnalysisService(AudioRecordRepository audioRecordRepository,
                                StationService stationService,
                                AlertService alertService) {
        this.audioRecordRepository = audioRecordRepository;
        this.stationService = stationService;
        this.alertService = alertService;

        initDeviceRegistry();
    }

    private void initDeviceRegistry() {
        deviceRegistry.put("DEV-DAM-01", new DeviceInternalState(
            "DEV-DAM-01", "Гидроакустический комплекс ГАК-Дамчик-1",
            1L, "BUOY-DAM-01", "Буй Дамчикский №1",
            "ONLINE", 94, "-64 dBm (LoRaWAN/4G)", "Cetacean Research C57 Dual-Array", 38.2, "boat_engine_outboard.wav"
        ));
        deviceRegistry.put("DEV-TRE-02", new DeviceInternalState(
            "DEV-TRE-02", "Биоакустический регистратор БАР-Трёхизбинка-2",
            2L, "BUOY-TRE-02", "Буй Трёхизбинский №2",
            "ONLINE", 87, "-72 dBm (LoRaWAN)", "Aquarian Audio H2d Hydrophone", 41.0, "sturgeon_water_splash.wav"
        ));
        deviceRegistry.put("DEV-CASP-03", new DeviceInternalState(
            "DEV-CASP-03", "Дальнобойный гидрофон ВКМСК-145",
            3L, "BUOY-CASP-03", "Морской буй 'Северный Каспий' №3",
            "ONLINE", 91, "-58 dBm (Спутниковый канал Иридиум)", "Teledyne RESON TC4032 Marine", 46.5, "boat_engine_outboard.wav"
        ));
        deviceRegistry.put("DEV-AGR-04", new DeviceInternalState(
            "DEV-AGR-04", "Акустический регистратор Кизляр-Аграхан-4",
            4L, "BUOY-AGR-04", "Станция 'Аграханский залив' №4",
            "ONLINE", 82, "-76 dBm (LoRaWAN)", "Cetacean SQ26-08 Acoustic Array", 39.5, "poaching_gunfire_shot.wav"
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

        // Classification: query Python AI first, fallback to heuristic if Python is unavailable
        byte[] audioBytes = getDeviceAudioStream(deviceId);
        AiInferenceResult pyResult = queryPythonAiService(sampleName, audioBytes, station.getId());

        String detectedClass;
        double confidence;
        String threatLevel;
        boolean isThreat = false;
        String sourceDesc;

        if (pyResult != null) {
            detectedClass = pyResult.detectedClass();
            confidence = pyResult.confidence();
            threatLevel = pyResult.threatLevel();
            isThreat = detectedClass.equals("boat_engine") || detectedClass.equals("gunfire");
            sourceDesc = "ИИ-модель (Python Flask :8000) • " + dev.name + " (" + dev.hydrophoneModel + ")";
        } else {
            if (sampleName.contains("boat") || sampleName.contains("motor")) {
                detectedClass = "boat_engine";
                confidence = 0.94;
                threatLevel = "HIGH";
                isThreat = true;
            } else if (sampleName.contains("shot") || sampleName.contains("gunfire")) {
                detectedClass = "gunfire";
                confidence = 0.89;
                threatLevel = "CRITICAL";
                isThreat = true;
            } else if (sampleName.contains("splash")) {
                detectedClass = "fish_splash";
                confidence = 0.92;
                threatLevel = "LOW";
            } else {
                detectedClass = "ambient_noise";
                confidence = 0.96;
                threatLevel = "NONE";
            }
            sourceDesc = String.format("Прямой поток с устройства %s (%s)", dev.name, dev.hydrophoneModel);
        }

        AudioRecord record = new AudioRecord(
            station,
            sampleName,
            "/samples/" + sampleName,
            detectedClass,
            confidence,
            threatLevel,
            sourceDesc
        );
        AudioRecord saved = audioRecordRepository.save(record);

        Long alertId = null;
        if (isThreat) {
            AlertType alertType = detectedClass.equals("gunfire") ? AlertType.ACOUSTIC_GUNFIRE : AlertType.ACOUSTIC_BOAT_DETECTED;
            AlertSeverity severity = detectedClass.equals("gunfire") ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            String msg = String.format("Гидрофон %s: Зафиксирован звук '%s' (достоверность: %.0f%%)",
                dev.deviceId,
                detectedClass.equals("boat_engine") ? "подвесной лодочный мотор" : "выстрел",
                confidence * 100.0
            );
            String details = String.format("Сигнал поступил с полевого регистрирующего комплекса %s на станции %s. Обнаружена подозрительная активность в акватории ООПТ.",
                dev.name, station.getName()
            );

            Alert alert = alertService.createAlert(station, alertType, severity, msg, details);
            alertId = alert.getId();

            stationService.updateStationStatus(station, StationStatus.WARNING);
        }

        return new AudioAnalysisResponse(
            saved.getId(),
            station.getId(),
            station.getName(),
            sampleName,
            detectedClass,
            confidence,
            threatLevel,
            sourceDesc,
            isThreat,
            alertId,
            saved.getCreatedAt()
        );
    }

    public byte[] getDeviceAudioStream(String deviceId) {
        DeviceInternalState dev = deviceRegistry.get(deviceId);
        String filename = (dev != null) ? dev.defaultSampleFile : "boat_engine_outboard.wav";

        // Try reading from file path or classpath
        try {
            Path p = Paths.get("demo/src/main/resources/static/samples", filename);
            if (Files.exists(p)) {
                return Files.readAllBytes(p);
            }
            Path p2 = Paths.get("data/audio", filename);
            if (Files.exists(p2)) {
                return Files.readAllBytes(p2);
            }
            ClassPathResource cp = new ClassPathResource("static/samples/" + filename);
            if (cp.exists()) {
                return cp.getInputStream().readAllBytes();
            }
        } catch (IOException e) {
            // fallback
        }
        return new byte[0];
    }

    @Transactional
    public AudioAnalysisResponse processAudioUpload(MultipartFile file, Long stationId) {
        Station station = stationService.getStationById(stationId);

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "live_recording.wav";
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;

        try {
            Path uploadPath = Paths.get(uploadDir, "audio");
            Files.createDirectories(uploadPath);
            Path targetFile = uploadPath.resolve(storedFilename);
            file.transferTo(targetFile.toFile());
        } catch (IOException e) {
            storedFilename = "virtual_" + storedFilename;
        }

        byte[] fileBytes = null;
        try {
            fileBytes = file.getBytes();
        } catch (Exception ignored) {}

        AiInferenceResult pyResult = queryPythonAiService(originalFilename, fileBytes, stationId);

        String detectedClass;
        double confidence;
        String threatLevel;
        boolean isThreat = false;
        String sourceDesc;

        if (pyResult != null) {
            detectedClass = pyResult.detectedClass();
            confidence = pyResult.confidence();
            threatLevel = pyResult.threatLevel();
            isThreat = detectedClass.equals("boat_engine") || detectedClass.equals("gunfire");
            sourceDesc = "ИИ-модель (Python Flask :8000) • " + originalFilename;
        } else {
            String lowerName = originalFilename.toLowerCase();
            if (lowerName.contains("boat") || lowerName.contains("motor") || lowerName.contains("двигател") || lowerName.contains("лодка")) {
                detectedClass = "boat_engine";
                confidence = 0.94;
                threatLevel = "HIGH";
                isThreat = true;
            } else if (lowerName.contains("shot") || lowerName.contains("gun") || lowerName.contains("выстрел")) {
                detectedClass = "gunfire";
                confidence = 0.88;
                threatLevel = "CRITICAL";
                isThreat = true;
            } else if (lowerName.contains("splash") || lowerName.contains("fish") || lowerName.contains("рыба") || lowerName.contains("плеск")) {
                detectedClass = "fish_splash";
                confidence = 0.91;
                threatLevel = "LOW";
            } else if (lowerName.contains("ambient") || lowerName.contains("wave") || lowerName.contains("волна") || lowerName.contains("ветер")) {
                detectedClass = "ambient_noise";
                confidence = 0.95;
                threatLevel = "NONE";
            } else {
                detectedClass = "boat_engine";
                confidence = 0.89;
                threatLevel = "HIGH";
                isThreat = true;
            }
            sourceDesc = "Запись с полевого устройства инспектора ООПТ (" + originalFilename + ")";
        }

        AudioRecord record = new AudioRecord(
            station,
            originalFilename,
            storedFilename,
            detectedClass,
            confidence,
            threatLevel,
            sourceDesc
        );
        AudioRecord saved = audioRecordRepository.save(record);

        Long alertId = null;
        if (isThreat) {
            AlertType alertType = detectedClass.equals("gunfire") ? AlertType.ACOUSTIC_GUNFIRE : AlertType.ACOUSTIC_BOAT_DETECTED;
            AlertSeverity severity = detectedClass.equals("gunfire") ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            String msg = String.format("Акустический датчик: Обнаружен звук '%s' (достоверность: %.0f%%)", 
                detectedClass.equals("boat_engine") ? "подвесной лодочный мотор" : "выстрел", confidence * 100.0);
            String details = String.format("Зафиксировано микрофоном/гидрофоном на станции %s (%s). Возможная браконьерская активность в акватории.",
                station.getCode(), station.getName());

            Alert alert = alertService.createAlert(station, alertType, severity, msg, details);
            alertId = alert.getId();

            stationService.updateStationStatus(station, StationStatus.WARNING);
        }

        return new AudioAnalysisResponse(
            saved.getId(),
            station.getId(),
            station.getName(),
            saved.getFilename(),
            saved.getDetectedClass(),
            saved.getConfidence(),
            saved.getThreatLevel(),
            saved.getAnalysisSource(),
            isThreat,
            alertId,
            saved.getCreatedAt()
        );
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

    // Inter-service HTTP call to Python AI (FastAPI / Flask)
    private AiInferenceResult queryPythonAiService(String filename, byte[] audioBytes, Long stationId) {
        String aiUrl = "http://localhost:8000/api/ai/audio/analyze";
        try {
            String boundary = "===EcoShieldBoundary" + System.currentTimeMillis();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // station_id parameter
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Disposition: form-data; name=\"station_id\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write((stationId.toString() + "\r\n").getBytes(StandardCharsets.UTF_8));

            // file parameter
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            if (audioBytes != null && audioBytes.length > 0) {
                baos.write(audioBytes);
            } else {
                baos.write("dummy audio content".getBytes(StandardCharsets.UTF_8));
            }
            baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiUrl))
                .timeout(Duration.ofMillis(2000))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .build();

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1200))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                String detectedClass = extractJsonString(body, "detected_class", "boat_engine");
                double confidence = extractJsonDouble(body, "confidence", 0.94);
                String threatLevel = extractJsonString(body, "threat_level", "HIGH");
                return new AiInferenceResult(detectedClass, confidence, threatLevel, "Python AI Microservice (PyTorch/Flask :8000)");
            }
        } catch (Exception ignored) {
            // Python AI service offline or timeout -> Fallback to built-in heuristic
        }
        return null;
    }

    private String extractJsonString(String json, String key, String defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : defaultVal;
    }

    private double extractJsonDouble(String json, String key, double defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.]+)");
        Matcher m = p.matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : defaultVal;
    }

    private record AiInferenceResult(String detectedClass, double confidence, String threatLevel, String sourceDesc) {}
}
