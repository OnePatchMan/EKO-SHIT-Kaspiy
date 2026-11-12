package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "water_telemetry")
public class WaterTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "salinity_ppt")
    private Double salinityPpt;

    @Column(name = "dissolved_oxygen_mg_l")
    private Double dissolvedOxygenMgL;

    @Column(name = "ph")
    private Double ph;

    @Column(name = "turbidity_ntu")
    private Double turbidityNtu;

    @Column(name = "water_level_cm")
    private Double waterLevelCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false)
    private QualityStatus qualityStatus = QualityStatus.NORMAL;

    public WaterTelemetry() {
    }

    public WaterTelemetry(Station station, LocalDateTime recordedAt, Double temperatureC, Double salinityPpt,
                          Double dissolvedOxygenMgL, Double ph, Double turbidityNtu, Double waterLevelCm,
                          QualityStatus qualityStatus) {
        this.station = station;
        this.recordedAt = recordedAt;
        this.temperatureC = temperatureC;
        this.salinityPpt = salinityPpt;
        this.dissolvedOxygenMgL = dissolvedOxygenMgL;
        this.ph = ph;
        this.turbidityNtu = turbidityNtu;
        this.waterLevelCm = waterLevelCm;
        this.qualityStatus = qualityStatus;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Double getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(Double temperatureC) {
        this.temperatureC = temperatureC;
    }

    public Double getSalinityPpt() {
        return salinityPpt;
    }

    public void setSalinityPpt(Double salinityPpt) {
        this.salinityPpt = salinityPpt;
    }

    public Double getDissolvedOxygenMgL() {
        return dissolvedOxygenMgL;
    }

    public void setDissolvedOxygenMgL(Double dissolvedOxygenMgL) {
        this.dissolvedOxygenMgL = dissolvedOxygenMgL;
    }

    public Double getPh() {
        return ph;
    }

    public void setPh(Double ph) {
        this.ph = ph;
    }

    public Double getTurbidityNtu() {
        return turbidityNtu;
    }

    public void setTurbidityNtu(Double turbidityNtu) {
        this.turbidityNtu = turbidityNtu;
    }

    public Double getWaterLevelCm() {
        return waterLevelCm;
    }

    public void setWaterLevelCm(Double waterLevelCm) {
        this.waterLevelCm = waterLevelCm;
    }

    public QualityStatus getQualityStatus() {
        return qualityStatus;
    }

    public void setQualityStatus(QualityStatus qualityStatus) {
        this.qualityStatus = qualityStatus;
    }
}

