package com.example.demo.repository;

import com.example.demo.model.Alert;
import com.example.demo.model.AlertSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findAllByOrderByCreatedAtDesc();

    List<Alert> findByResolvedOrderByCreatedAtDesc(boolean resolved);

    List<Alert> findByResolvedAndSeverityOrderByCreatedAtDesc(boolean resolved, AlertSeverity severity);

    List<Alert> findByStationIdOrderByCreatedAtDesc(Long stationId);

    List<Alert> findByStationIdAndResolvedOrderByCreatedAtDesc(Long stationId, boolean resolved);

    long countByResolved(boolean resolved);
}

