package com.example.demo.repository;

import com.example.demo.model.Station;
import com.example.demo.model.WaterTelemetry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaterTelemetryRepository extends JpaRepository<WaterTelemetry, Long> {

    List<WaterTelemetry> findByStationOrderByRecordedAtDesc(Station station, Pageable pageable);

    List<WaterTelemetry> findByStationIdOrderByRecordedAtDesc(Long stationId, Pageable pageable);

    Optional<WaterTelemetry> findTopByStationOrderByRecordedAtDesc(Station station);

    @Query("SELECT t FROM WaterTelemetry t WHERE t.station.id = :stationId ORDER BY t.recordedAt DESC")
    List<WaterTelemetry> findRecentByStationId(@Param("stationId") Long stationId, Pageable pageable);

    @Query("SELECT t FROM WaterTelemetry t WHERE t.id IN (SELECT MAX(t2.id) FROM WaterTelemetry t2 GROUP BY t2.station.id)")
    List<WaterTelemetry> findLatestForAllStations();
}

