package com.example.demo.config;

import com.example.demo.model.*;
import com.example.demo.repository.AlertRepository;
import com.example.demo.repository.AudioRecordRepository;
import com.example.demo.repository.StationRepository;
import com.example.demo.repository.WaterTelemetryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final StationRepository stationRepository;
    private final WaterTelemetryRepository telemetryRepository;
    private final AlertRepository alertRepository;
    private final AudioRecordRepository audioRecordRepository;

    public DataInitializer(StationRepository stationRepository,
                           WaterTelemetryRepository telemetryRepository,
                           AlertRepository alertRepository,
                           AudioRecordRepository audioRecordRepository) {
        this.stationRepository = stationRepository;
        this.telemetryRepository = telemetryRepository;
        this.alertRepository = alertRepository;
        this.audioRecordRepository = audioRecordRepository;
    }

    @Override
    public void run(String... args) {
        if (stationRepository.count() > 0) {
            return;
        }

        // 1. Initializing 5 Realistic Protected Territory Stations
        Station s1 = new Station(
            "BUOY-DAM-01",
            "Буй Дамчикский №1",
            "Дельта Волги — Дамчикский участок (Астраханский биосферный заповедник)",
            45.7825,
            47.9234,
            StationStatus.WARNING
        );

        Station s2 = new Station(
            "BUOY-TRE-02",
            "Буй Трёхизбинский №2",
            "Дельта Волги — Тишковский рукав",
            45.8912,
            48.5411,
            StationStatus.NORMAL
        );

        Station s3 = new Station(
            "BUOY-CASP-03",
            "Морской буй 'Северный Каспий' №3",
            "Взморье Каспия — выход из Волго-Каспийского канала",
            45.4120,
            48.1560,
            StationStatus.NORMAL
        );

        Station s4 = new Station(
            "BUOY-AGR-04",
            "Станция 'Аграханский залив' №4",
            "Дагестанский биосферный заповедник — Кизлярский залив",
            43.6811,
            47.5218,
            StationStatus.NORMAL
        );

        Station s5 = new Station(
            "BUOY-TUL-05",
            "Морской буй 'Остров Тюлений' №5",
            "Северо-Западная акватория Каспийского моря (Лежбище тюленя)",
            44.4789,
            47.5180,
            StationStatus.NORMAL
        );

        List<Station> stations = stationRepository.saveAll(List.of(s1, s2, s3, s4, s5));

        // 2. Generating Historical Water Telemetry for each station
        LocalDateTime now = LocalDateTime.now();

        // Station 1: Damchik - autumn hydrology + localized hypoxia event
        for (int h = 12; h >= 1; h--) {
            telemetryRepository.save(new WaterTelemetry(
                s1,
                now.minusHours(h * 2L),
                7.4 + (h * 0.05),
                0.28,
                9.1 + (h * 0.08),
                7.8,
                15.0,
                258.0 + h,
                QualityStatus.NORMAL
            ));
        }
        // Latest reading on S1 has critical oxygen drop in dead-end canal
        telemetryRepository.save(new WaterTelemetry(
            s1,
            now.minusMinutes(15),
            7.8,
            0.32,
            3.75, // Below critical 4.0 mg/L!
            7.5,
            22.0,
            254.0,
            QualityStatus.CRITICAL
        ));

        // Create alert for Station 1
        Alert lowO2Alert = new Alert(
            s1,
            AlertType.HYDRO_LOW_OXYGEN,
            AlertSeverity.CRITICAL,
            "Критическое падение растворенного кислорода: 3.75 мг/л (порог нормы 6.0 мг/л)",
            "В протоке Дамчикского участка зафиксирована локальная гипоксия. Риск замора осетровых на нерестовых путях."
        );
        lowO2Alert.setCreatedAt(now.minusMinutes(15));
        alertRepository.save(lowO2Alert);

        // Station 2: Trekhizbinsky - optimal freshwater conditions
        for (int h = 8; h >= 0; h--) {
            telemetryRepository.save(new WaterTelemetry(
                s2,
                now.minusHours(h * 3L),
                7.1 + (h * 0.04),
                0.24,
                9.7 - (h * 0.03),
                7.9,
                12.0,
                268.0,
                QualityStatus.NORMAL
            ));
        }

        // Station 3: Marine Caspian Buoy 3
        for (int h = 8; h >= 0; h--) {
            telemetryRepository.save(new WaterTelemetry(
                s3,
                now.minusHours(h * 3L),
                8.4 + (h * 0.03),
                3.8,
                9.6,
                8.2,
                11.0,
                310.0,
                QualityStatus.NORMAL
            ));
        }

        // Station 4: Agrakhan
        for (int h = 6; h >= 0; h--) {
            telemetryRepository.save(new WaterTelemetry(
                s4,
                now.minusHours(h * 4L),
                9.1,
                1.65,
                9.0,
                8.0,
                18.0,
                248.0,
                QualityStatus.NORMAL
            ));
        }

        // Station 5: Tyuleniy Island
        for (int h = 6; h >= 0; h--) {
            telemetryRepository.save(new WaterTelemetry(
                s5,
                now.minusHours(h * 4L),
                10.1,
                9.2, // Brackish sea water
                10.2,
                8.3,
                7.5,
                382.0,
                QualityStatus.NORMAL
            ));
        }

        // 3. Register Authentic Audio Recordings in Database
        AudioRecord r1 = new AudioRecord(
            s3,
            "boat_engine_outboard.wav",
            "/samples/boat_engine_outboard.wav",
            "boat_engine",
            0.94,
            "HIGH",
            "Гидроакустический комплекс ВКМСК (145 км)"
        );
        AudioRecord r2 = new AudioRecord(
            s1,
            "poaching_gunfire_shot.wav",
            "/samples/poaching_gunfire_shot.wav",
            "gunfire",
            0.89,
            "CRITICAL",
            "Акустический буй Дамчикский №1"
        );
        AudioRecord r3 = new AudioRecord(
            s2,
            "sturgeon_water_splash.wav",
            "/samples/sturgeon_water_splash.wav",
            "fish_splash",
            0.92,
            "LOW",
            "Биоакустический датчик Трёхизбинка"
        );
        AudioRecord r4 = new AudioRecord(
            s5,
            "ambient_caspian_waves.wav",
            "/samples/ambient_caspian_waves.wav",
            "ambient_noise",
            0.97,
            "NONE",
            "Морская станция МГ-2 Тюлений"
        );
        audioRecordRepository.saveAll(List.of(r1, r2, r3, r4));
    }
}

