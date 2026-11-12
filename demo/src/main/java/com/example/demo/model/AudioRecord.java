package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audio_records")
public class AudioRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(nullable = false)
    private String filename;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "detected_class")
    private String detectedClass;

    @Column
    private Double confidence;

    @Column(name = "threat_level")
    private String threatLevel;

    @Column(name = "analysis_source")
    private String analysisSource;

    @Lob
    @Column(name = "analysis_details_json")
    private String analysisDetailsJson;

    public String getAnalysisDetailsJson() { return analysisDetailsJson; }
    public void setAnalysisDetailsJson(String value) { analysisDetailsJson = value; }

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AudioRecord() {
    }

    public AudioRecord(Station station, String filename, String filePath, String detectedClass,
                       Double confidence, String threatLevel, String analysisSource) {
        this.station = station;
        this.filename = filename;
        this.filePath = filePath;
        this.detectedClass = detectedClass;
        this.confidence = confidence;
        this.threatLevel = threatLevel;
        this.analysisSource = analysisSource;
        this.createdAt = LocalDateTime.now();
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

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getDetectedClass() {
        return detectedClass;
    }

    public void setDetectedClass(String detectedClass) {
        this.detectedClass = detectedClass;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getThreatLevel() {
        return threatLevel;
    }

    public void setThreatLevel(String threatLevel) {
        this.threatLevel = threatLevel;
    }

    public String getAnalysisSource() {
        return analysisSource;
    }

    public void setAnalysisSource(String analysisSource) {
        this.analysisSource = analysisSource;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

