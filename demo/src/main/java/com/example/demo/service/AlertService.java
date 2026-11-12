package com.example.demo.service;

import com.example.demo.dto.AlertDto;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Alert;
import com.example.demo.model.AlertSeverity;
import com.example.demo.model.AlertType;
import com.example.demo.model.Station;
import com.example.demo.repository.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Transactional
    public Alert createAlert(Station station, AlertType type, AlertSeverity severity, String message, String details) {
        Alert alert = new Alert(station, type, severity, message, details);
        return alertRepository.save(alert);
    }

    @Transactional(readOnly = true)
    public List<AlertDto> getAlerts(Boolean resolved, AlertSeverity severity, Long stationId) {
        List<Alert> list;
        if (resolved != null && severity != null) {
            list = alertRepository.findByResolvedAndSeverityOrderByCreatedAtDesc(resolved, severity);
        } else if (resolved != null) {
            list = alertRepository.findByResolvedOrderByCreatedAtDesc(resolved);
        } else if (stationId != null) {
            list = alertRepository.findByStationIdOrderByCreatedAtDesc(stationId);
        } else {
            list = alertRepository.findAllByOrderByCreatedAtDesc();
        }

        return list.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AlertDto getAlertById(Long id) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Предупреждение с ID " + id + " не найдено"));
        return toDto(alert);
    }

    @Transactional
    public AlertDto resolveAlert(Long id) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Предупреждение с ID " + id + " не найдено"));
        alert.setResolved(true);
        alert.setResolvedAt(LocalDateTime.now());
        Alert saved = alertRepository.save(alert);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public long countActiveAlerts() {
        return alertRepository.countByResolved(false);
    }

    @Transactional(readOnly = true)
    public long countTotalAlerts() {
        return alertRepository.count();
    }

    public AlertDto toDto(Alert a) {
        Station s = a.getStation();
        return new AlertDto(
            a.getId(),
            s != null ? s.getId() : null,
            s != null ? s.getCode() : null,
            s != null ? s.getName() : null,
            s != null ? s.getTerritory() : null,
            s != null ? s.getLatitude() : null,
            s != null ? s.getLongitude() : null,
            a.getType(),
            a.getSeverity(),
            a.getMessage(),
            a.getDetails(),
            a.getCreatedAt(),
            a.isResolved(),
            a.getResolvedAt()
        );
    }
}

