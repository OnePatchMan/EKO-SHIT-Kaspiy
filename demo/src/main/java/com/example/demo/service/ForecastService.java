package com.example.demo.service;

import com.example.demo.dto.SpawningForecastResponse;
import com.example.demo.model.Station;
import com.example.demo.model.WaterTelemetry;
import com.example.demo.repository.StationRepository;
import com.example.demo.repository.WaterTelemetryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ForecastService {

    private final StationRepository stationRepository;
    private final WaterTelemetryRepository telemetryRepository;

    public ForecastService(StationRepository stationRepository, WaterTelemetryRepository telemetryRepository) {
        this.stationRepository = stationRepository;
        this.telemetryRepository = telemetryRepository;
    }

    public SpawningForecastResponse calculateSpawningForecast(String species, Long stationId) {
        String targetSpecies = (species != null && !species.isBlank()) ? species : "Русский осётр (Acipenser gueldenstaedtii)";

        Station station = (stationId != null) 
            ? stationRepository.findById(stationId).orElse(null)
            : stationRepository.findAll().stream().findFirst().orElse(null);

        String targetZone = station != null ? station.getTerritory() : "Дельта Волги — Дамчикский участок";

        // Fetch recent telemetry to evaluate real hydrological conditions
        List<WaterTelemetry> recent = station != null 
            ? telemetryRepository.findByStationOrderByRecordedAtDesc(station, PageRequest.of(0, 10))
            : List.of();

        double avgTemp = recent.stream().mapToDouble(t -> t.getTemperatureC() != null ? t.getTemperatureC() : 12.0).average().orElse(12.5);
        double avgSalinity = recent.stream().mapToDouble(t -> t.getSalinityPpt() != null ? t.getSalinityPpt() : 1.0).average().orElse(0.9);
        double avgOxygen = recent.stream().mapToDouble(t -> t.getDissolvedOxygenMgL() != null ? t.getDissolvedOxygenMgL() : 8.0).average().orElse(7.8);
        double avgLevel = recent.stream().mapToDouble(t -> t.getWaterLevelCm() != null ? t.getWaterLevelCm() : 250.0).average().orElse(260.0);

        // Check if forecast is requested for Caspian Seal (Pusa caspica)
        boolean isSeal = targetSpecies.toLowerCase().contains("тюлен") 
                      || targetSpecies.toLowerCase().contains("pusa")
                      || targetSpecies.toLowerCase().contains("нерп");

        if (isSeal) {
            // Caspian Seal (Pusa caspica) Autumn Migration Model
            // Peak migration occurs in Nov-Dec from Middle Caspian to shallow North Caspian ice fields
            double sealScore = 65.0;
            if (avgSalinity > 5.0) sealScore += 18.0; // Marine preference
            if (avgTemp < 13.0 && avgTemp > 3.0) sealScore += 14.0; // Temperature trigger for northward run
            if (station != null && (station.getId() == 5L || station.getId() == 3L || station.getId() == 4L)) {
                sealScore += 15.0; // Epicenters: Ostrov Tyuleniy, VKMSK, Agrakhan
            }

            sealScore = Math.max(20.0, Math.min(96.5, sealScore));

            String sealStatus = sealScore >= 75.0 ? "MIGRATION_PEAK_HIGH_RISK" : "TRANSIT_MIGRATION";
            String sealLimiting = "Пик сезонной миграции каспийского тюленя на северные лежбища. Критическая угроза: браконьерские крупноячеистые сети и скоростные байды.";
            String sealRec = "🚨 ПОВЫШЕННЫЙ РЕЖИМ ОХРАНЫ ЭНДЕМИКА: Направить рейдовые группы по миграционному коридору 'Аграхан — Остров Тюлений — ВКМСК'. Провести траление акватории для ликвидации браконьерских сетей.";

            Map<String, Object> sealSummary = new HashMap<>();
            sealSummary.put("averageTemperatureC", Math.round(avgTemp * 10.0) / 10.0);
            sealSummary.put("averageSalinityPpt", Math.round(avgSalinity * 10.0) / 10.0);
            sealSummary.put("averageOxygenMgL", Math.round(avgOxygen * 10.0) / 10.0);
            sealSummary.put("migrationCorridor", "Средний Каспий -> Остров Тюлений -> Северный Каспий (ВКМСК)");
            sealSummary.put("conservationStatus", "Красная книга РФ, МСОП (Endangered)");

            return new SpawningForecastResponse(
                targetSpecies,
                targetZone,
                LocalDateTime.now(),
                Math.round(sealScore * 10.0) / 10.0,
                sealStatus,
                LocalDate.now(),
                LocalDate.now().plusDays(21),
                sealSummary,
                sealLimiting,
                sealRec,
                "Caspian Seal Migration AI (Pusa caspica Bio-Predictor v1.0)"
            );
        }

        // Biological suitability algorithm for Acipenser gueldenstaedtii:
        // - Optimum temperature: 11 - 15°C
        // - Optimum salinity for delta spawning runs: < 1.5 ppt
        // - Optimum oxygen: > 7.0 mg/L
        // - Optimum flood level: > 230 cm
        double score = 100.0;

        if (avgTemp < 8.0 || avgTemp > 20.0) score -= 40.0;
        else if (avgTemp < 11.0 || avgTemp > 16.0) score -= 15.0;

        if (avgSalinity > 5.0) score -= 35.0;
        else if (avgSalinity > 2.0) score -= 15.0;

        if (avgOxygen < 4.0) score -= 45.0;
        else if (avgOxygen < 6.0) score -= 20.0;

        if (avgLevel < 180.0) score -= 25.0;

        score = Math.max(10.0, Math.min(98.5, score));

        String status = score >= 75.0 ? "OPTIMAL_SPAWNING_CONDITIONS" : (score >= 50.0 ? "AUTUMN_WINTERING_CONCENTRATION" : "UNFAVORABLE");
        String limitingFactor = score >= 75.0 
            ? "Гидрологический режим в норме, температура и уровень паводка оптимальны"
            : (avgTemp < 9.0 ? "Осеннее охлаждение воды: переход от миграции к залеганию на зимовальные ямы" : (avgOxygen < 6.0 ? "Дефицит кислорода" : "Колебания солёности"));

        String recommendation = score >= 75.0
            ? "Высокая вероятность массового нерестового хода. Рекомендовано выставление круглосуточных постов инспекции в прокопах и устьях."
            : (avgTemp < 9.0 ? "Осетровые концентрируются на глубоких зимовальных ямах дельты ('Шабур', 'Седьмая Огнёвка'). Рекомендовано ночное патрулирование против браконьерского багрения." : "Период низкой нерестовой активности. Дежурное патрулирование.");

        Map<String, Object> summary = new HashMap<>();
        summary.put("averageTemperatureC", Math.round(avgTemp * 10.0) / 10.0);
        summary.put("averageSalinityPpt", Math.round(avgSalinity * 10.0) / 10.0);
        summary.put("averageOxygenMgL", Math.round(avgOxygen * 10.0) / 10.0);
        summary.put("waterLevelCm", Math.round(avgLevel * 10.0) / 10.0);
        summary.put("optimalTemperatureRange", "11.0 - 15.5 °C");

        return new SpawningForecastResponse(
            targetSpecies,
            targetZone,
            LocalDateTime.now(),
            Math.round(score * 10.0) / 10.0,
            status,
            LocalDate.now().plusDays(2),
            LocalDate.now().plusDays(9),
            summary,
            limitingFactor,
            recommendation,
            "Caspian Eco-Predictor v1.2 (Hybrid Hydrological AI)"
        );
    }
}

