package com.example.demo.service;

import com.example.demo.dto.SpawningForecastResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.AiServiceException;
import com.example.demo.model.Station;
import com.example.demo.model.WaterTelemetry;
import com.example.demo.repository.StationRepository;
import com.example.demo.repository.WaterTelemetryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class ForecastService {
    private final StationRepository stationRepository;
    private final WaterTelemetryRepository telemetryRepository;
    private final AiServiceClient aiClient;
    private final ZoneId telemetryZone;
    private final String dataSource;
    private final String levelDatum;

    public ForecastService(StationRepository stations, WaterTelemetryRepository telemetry, AiServiceClient aiClient,
            @Value("${ecoshield.telemetry-zone:Europe/Moscow}") String zone,
            @Value("${ecoshield.forecast.data-source:demo_synthetic}") String dataSource,
            @Value("${ecoshield.forecast.level-datum:}") String levelDatum) {
        this.stationRepository = stations;
        this.telemetryRepository = telemetry;
        this.aiClient = aiClient;
        this.telemetryZone = ZoneId.of(zone);
        this.dataSource = dataSource;
        this.levelDatum = levelDatum == null ? "" : levelDatum.strip();
    }

    public SpawningForecastResponse calculateSpawningForecast(String species, Long stationId) {
        String targetSpecies = species == null || species.isBlank() ? "cyprinus_carpio" : species.strip();
        if (!"cyprinus_carpio".equals(targetSpecies)) {
            throw new IllegalArgumentException("Сценарий поддерживает только сазана / карпа: species=cyprinus_carpio");
        }
        Station station = stationId == null
                ? stationRepository.findAll().stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Нет станции для прогноза"))
                : stationRepository.findById(stationId).orElseThrow(() -> new ResourceNotFoundException("Станция не найдена: " + stationId));
        List<WaterTelemetry> recent = telemetryRepository.findByStationOrderByRecordedAtDesc(station, PageRequest.of(0, 1000));
        List<Map<String,Object>> history = new ArrayList<>();
        for (WaterTelemetry telemetry : recent) {
            Map<String,Object> row = new LinkedHashMap<>();
            if (telemetry.getRecordedAt() != null) row.put("timestamp", telemetry.getRecordedAt().atZone(telemetryZone).toInstant().toString());
            if (telemetry.getTemperatureC() != null) row.put("temperature_c", telemetry.getTemperatureC());
            if (!levelDatum.isBlank() && telemetry.getWaterLevelCm() != null) row.put("water_level_m", telemetry.getWaterLevelCm() / 100.0);
            if (telemetry.getDissolvedOxygenMgL() != null) row.put("dissolved_oxygen_mg_l", telemetry.getDissolvedOxygenMgL());
            // Inundation is not measured by this entity. Missing values stay missing.
            // Salinity PPT is not silently relabeled as the external service's PSU.
            history.add(row);
        }
        Map<String,Object> request = new LinkedHashMap<>();
        request.put("station_id", station.getId().toString());
        request.put("species", targetSpecies);
        request.put("issued_at", Instant.now().toString());
        request.put("horizon_hours", 72);
        request.put("data_source", dataSource);
        request.put("history", history);
        if (!levelDatum.isBlank()) request.put("level_datum", levelDatum);
        // The external service validates required temperature, 48h span and freshness.
        // Validation failures and service outages propagate; no local prediction fallback.
        Map<String,Object> upstream = aiClient.forecast(request);
        validateForecast(upstream);
        Map<String,Object> details = new LinkedHashMap<>(upstream);
        if (levelDatum.isBlank()) {
            List<Object> missing = new ArrayList<>();
            if (upstream.get("missing_data") instanceof List<?> values) missing.addAll(values);
            if (!missing.contains("level_datum")) missing.add("level_datum");
            details.put("missing_data", missing);
            List<Object> warnings = new ArrayList<>();
            if (upstream.get("warnings") instanceof List<?> values) warnings.addAll(values);
            warnings.add("Уровни воды не переданы: неизвестна система отсчёта (level_datum).");
            details.put("warnings", warnings);
        }
        String version = Objects.toString(upstream.get("algorithm_version"), "unknown");
        return new SpawningForecastResponse(targetSpecies, station.getTerritory(), LocalDateTime.now(telemetryZone), null,
                Objects.toString(upstream.get("mode"), "scenario_conditions_forecast"),
                date(upstream.get("valid_from")), date(upstream.get("valid_to")),
                Map.of("observationCount", history.size(), "telemetryZone", telemetryZone.toString(), "dataSource", dataSource),
                "См. missing_data и factors внешнего сценария",
                "Демонстрационный сценарий требует проверки инспектором; фактический нерест не подтверждён.", version, details);
    }

    private void validateForecast(Map<String,Object> result) {
        try {
            if (result == null || !"scenario_conditions_forecast".equals(result.get("mode"))
                    || !Boolean.TRUE.equals(result.get("is_demo"))
                    || !"not_validated_on_spawning_observations".equals(result.get("validation_status"))
                    || !(result.get("algorithm_version") instanceof String version) || version.isBlank()
                    || !(result.get("trajectory") instanceof List<?> rows) || rows.isEmpty()
                    || !result.containsKey("probability") || result.get("probability") != null)
                throw new IllegalArgumentException("Invalid forecast schema");
            var from = OffsetDateTime.parse(result.get("valid_from").toString());
            var to = OffsetDateTime.parse(result.get("valid_to").toString());
            if (!to.isAfter(from)) throw new IllegalArgumentException("Invalid forecast period");
        } catch (RuntimeException error) {
            throw new AiServiceException(503, "Сервис ИИ вернул некорректный сценарный прогноз", error);
        }
    }

    private LocalDate date(Object value) {
        return value == null ? null : OffsetDateTime.parse(value.toString()).atZoneSameInstant(telemetryZone).toLocalDate();
    }
}
