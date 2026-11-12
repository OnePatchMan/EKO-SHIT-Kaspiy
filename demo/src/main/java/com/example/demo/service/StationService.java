package com.example.demo.service;

import com.example.demo.dto.CreateStationRequest;
import com.example.demo.dto.StationDto;
import com.example.demo.dto.WaterTelemetrySummaryDto;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.ValidationException;
import com.example.demo.model.Station;
import com.example.demo.model.StationStatus;
import com.example.demo.model.WaterTelemetry;
import com.example.demo.repository.StationRepository;
import com.example.demo.repository.WaterTelemetryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class StationService {

    private final StationRepository stationRepository;
    private final WaterTelemetryRepository telemetryRepository;

    public StationService(StationRepository stationRepository, WaterTelemetryRepository telemetryRepository) {
        this.stationRepository = stationRepository;
        this.telemetryRepository = telemetryRepository;
    }

    @Transactional(readOnly = true)
    public List<StationDto> getAllStations() {
        return stationRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public StationDto getStationDtoById(Long id) {
        Station station = getStationById(id);
        return toDto(station);
    }

    @Transactional(readOnly = true)
    public Station getStationById(Long id) {
        return stationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Станция мониторинга с ID " + id + " не найдена"));
    }

    @Transactional
    public StationDto createStation(CreateStationRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new ValidationException("Код станции не может быть пустым");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ValidationException("Название станции обязательно для заполнения");
        }
        if (stationRepository.findByCode(request.code()).isPresent()) {
            throw new ValidationException("Станция с кодом '" + request.code() + "' уже зарегистрирована");
        }

        Station station = new Station(
            request.code(),
            request.name(),
            request.territory() != null ? request.territory() : "Дельта Волги и Северный Каспий",
            request.latitude(),
            request.longitude(),
            StationStatus.NORMAL
        );
        station.setLastUpdated(LocalDateTime.now());
        Station saved = stationRepository.save(station);
        return toDto(saved);
    }

    @Transactional
    public void updateStationStatus(Station station, StationStatus status) {
        station.setStatus(status);
        station.setLastUpdated(LocalDateTime.now());
        stationRepository.save(station);
    }

    public StationDto toDto(Station station) {
        Optional<WaterTelemetry> latestOpt = telemetryRepository.findTopByStationOrderByRecordedAtDesc(station);
        WaterTelemetrySummaryDto summary = latestOpt.map(t -> new WaterTelemetrySummaryDto(
            t.getId(),
            t.getRecordedAt(),
            t.getTemperatureC(),
            t.getSalinityPpt(),
            t.getDissolvedOxygenMgL(),
            t.getPh(),
            t.getTurbidityNtu(),
            t.getWaterLevelCm(),
            t.getQualityStatus()
        )).orElse(null);

        return new StationDto(
            station.getId(),
            station.getCode(),
            station.getName(),
            station.getTerritory(),
            station.getLatitude(),
            station.getLongitude(),
            station.getStatus(),
            station.getLastUpdated(),
            summary
        );
    }
}

