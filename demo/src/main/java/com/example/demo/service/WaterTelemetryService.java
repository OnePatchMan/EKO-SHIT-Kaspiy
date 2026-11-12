package com.example.demo.service;

import com.example.demo.dto.WaterTelemetryRequest;
import com.example.demo.dto.WaterTelemetryResponse;
import com.example.demo.exception.ValidationException;
import com.example.demo.model.*;
import com.example.demo.repository.StationRepository;
import com.example.demo.repository.WaterTelemetryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WaterTelemetryService {

    private final WaterTelemetryRepository telemetryRepository;
    private final StationRepository stationRepository;
    private final StationService stationService;
    private final AlertService alertService;

    public WaterTelemetryService(WaterTelemetryRepository telemetryRepository,
                                 StationRepository stationRepository,
                                 StationService stationService,
                                 AlertService alertService) {
        this.telemetryRepository = telemetryRepository;
        this.stationRepository = stationRepository;
        this.stationService = stationService;
        this.alertService = alertService;
    }

    @Transactional
    public WaterTelemetryResponse processTelemetry(WaterTelemetryRequest request) {
        if (request.stationId() == null) {
            throw new ValidationException("Не указан stationId станции мониторинга");
        }
        Station station = stationService.getStationById(request.stationId());

        validateTelemetryRanges(request);

        LocalDateTime recordedAt = request.recordedAt() != null ? request.recordedAt() : LocalDateTime.now();

        List<Alert> generatedAlerts = new ArrayList<>();
        QualityStatus qualityStatus = evaluateQualityAndGenerateAlerts(station, request, generatedAlerts);

        WaterTelemetry telemetry = new WaterTelemetry(
            station,
            recordedAt,
            request.temperatureC(),
            request.salinityPpt(),
            request.dissolvedOxygenMgL(),
            request.ph(),
            request.turbidityNtu(),
            request.waterLevelCm(),
            qualityStatus
        );

        WaterTelemetry saved = telemetryRepository.save(telemetry);

        // Update station status based on detected quality
        StationStatus targetStatus = switch (qualityStatus) {
            case CRITICAL -> StationStatus.CRITICAL;
            case WARNING -> StationStatus.WARNING;
            case NORMAL -> StationStatus.NORMAL;
        };
        stationService.updateStationStatus(station, targetStatus);

        return toResponse(saved, generatedAlerts.size());
    }

    @Transactional
    public List<WaterTelemetryResponse> processTelemetryBatch(List<WaterTelemetryRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
            .map(this::processTelemetry)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<WaterTelemetryResponse> getStationHistory(Long stationId, int limit) {
        Station station = stationService.getStationById(stationId);
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return telemetryRepository.findByStationOrderByRecordedAtDesc(station, PageRequest.of(0, safeLimit))
            .stream()
            .map(t -> toResponse(t, 0))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<WaterTelemetryResponse> getLatestForAllStations() {
        return telemetryRepository.findLatestForAllStations()
            .stream()
            .map(t -> toResponse(t, 0))
            .toList();
    }

    @Transactional(readOnly = true)
    public long countTelemetryRecords() {
        return telemetryRepository.count();
    }

    private void validateTelemetryRanges(WaterTelemetryRequest r) {
        if (r.temperatureC() != null && (r.temperatureC() < -10.0 || r.temperatureC() > 50.0)) {
            throw new ValidationException("Температура " + r.temperatureC() + "°C выходит за допустимый физический диапазон [-10, 50]");
        }
        if (r.salinityPpt() != null && (r.salinityPpt() < 0.0 || r.salinityPpt() > 80.0)) {
            throw new ValidationException("Солёность " + r.salinityPpt() + " ‰ выходит за допустимый диапазон [0, 80]");
        }
        if (r.dissolvedOxygenMgL() != null && (r.dissolvedOxygenMgL() < 0.0 || r.dissolvedOxygenMgL() > 30.0)) {
            throw new ValidationException("Растворенный кислород " + r.dissolvedOxygenMgL() + " мг/л выходит за диапазон [0, 30]");
        }
        if (r.ph() != null && (r.ph() < 0.0 || r.ph() > 14.0)) {
            throw new ValidationException("Водородный показатель pH " + r.ph() + " выходит за шкалу [0, 14]");
        }
        if (r.turbidityNtu() != null && (r.turbidityNtu() < 0.0 || r.turbidityNtu() > 1000.0)) {
            throw new ValidationException("Мутность " + r.turbidityNtu() + " NTU выходит за диапазон [0, 1000]");
        }
    }

    private QualityStatus evaluateQualityAndGenerateAlerts(Station station, WaterTelemetryRequest r, List<Alert> alerts) {
        QualityStatus status = QualityStatus.NORMAL;

        // 1. Dissolved Oxygen check (Critical for Caspian sturgeons and fish)
        if (r.dissolvedOxygenMgL() != null) {
            if (r.dissolvedOxygenMgL() < 4.0) {
                status = QualityStatus.CRITICAL;
                alerts.add(alertService.createAlert(
                    station,
                    AlertType.HYDRO_LOW_OXYGEN,
                    AlertSeverity.CRITICAL,
                    String.format("Критический дефицит кислорода: %.2f мг/л (норма > 6.0 мг/л)", r.dissolvedOxygenMgL()),
                    "Угроза заморных явлений и массовой гибели гидробионтов в протоках дельты"
                ));
            } else if (r.dissolvedOxygenMgL() < 5.5) {
                if (status != QualityStatus.CRITICAL) status = QualityStatus.WARNING;
                alerts.add(alertService.createAlert(
                    station,
                    AlertType.HYDRO_LOW_OXYGEN,
                    AlertSeverity.WARNING,
                    String.format("Снижение растворенного кислорода до %.2f мг/л", r.dissolvedOxygenMgL()),
                    "Внимание: падение кислорода ниже оптимума жизнедеятельности осетровых рыб"
                ));
            }
        }

        // 2. Salinity check (Delta of Volga is freshwater, marine intrusion or evaporation alert)
        if (r.salinityPpt() != null) {
            if (r.salinityPpt() > 8.0 && station.getTerritory().toLowerCase().contains("дельта")) {
                if (status != QualityStatus.CRITICAL) status = QualityStatus.WARNING;
                alerts.add(alertService.createAlert(
                    station,
                    AlertType.HYDRO_HIGH_SALINITY,
                    AlertSeverity.WARNING,
                    String.format("Аномальный скачок солёности в дельте: %.2f ‰ (норма < 2.0 ‰)", r.salinityPpt()),
                    "Зафиксировано нагонное явление соленых морских вод Каспия вверх по руслу"
                ));
            }
        }

        // 3. pH check
        if (r.ph() != null) {
            if (r.ph() < 6.0 || r.ph() > 9.0) {
                if (status != QualityStatus.CRITICAL) status = QualityStatus.WARNING;
                alerts.add(alertService.createAlert(
                    station,
                    AlertType.HYDRO_ABNORMAL_PH,
                    AlertSeverity.WARNING,
                    String.format("Аномальный показатель pH: %.2f (допустимо 6.5 - 8.5)", r.ph()),
                    "Возможный промышленный сброс или экстремальное цветение сине-зеленых водорослей"
                ));
            }
        }

        // 4. Turbidity check
        if (r.turbidityNtu() != null && r.turbidityNtu() > 60.0) {
            alerts.add(alertService.createAlert(
                station,
                AlertType.HYDRO_HIGH_TURBIDITY,
                AlertSeverity.INFO,
                String.format("Высокая мутность воды: %.1f NTU", r.turbidityNtu()),
                "Увеличение взвешенных частиц из-за размыва берегов или интенсивного судоходства"
            ));
        }

        // 5. Water Level check
        if (r.waterLevelCm() != null && r.waterLevelCm() < 130.0) {
            if (status != QualityStatus.CRITICAL) status = QualityStatus.WARNING;
            alerts.add(alertService.createAlert(
                station,
                AlertType.HYDRO_CRITICAL_LEVEL,
                AlertSeverity.WARNING,
                String.format("Критическое обмеление протоки: %.1f см", r.waterLevelCm()),
                "Риск пересыхания нерестовых полоев и отсечения рыбы в изолированных водоемах"
            ));
        }

        return status;
    }

    private WaterTelemetryResponse toResponse(WaterTelemetry t, int generatedAlertsCount) {
        return new WaterTelemetryResponse(
            t.getId(),
            t.getStation().getId(),
            t.getStation().getCode(),
            t.getStation().getName(),
            t.getRecordedAt(),
            t.getTemperatureC(),
            t.getSalinityPpt(),
            t.getDissolvedOxygenMgL(),
            t.getPh(),
            t.getTurbidityNtu(),
            t.getWaterLevelCm(),
            t.getQualityStatus(),
            generatedAlertsCount
        );
    }
}

