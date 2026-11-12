package com.example.demo.controller;

import com.example.demo.dto.AudioAnalysisResponse;
import com.example.demo.dto.RecordingDeviceDto;
import com.example.demo.model.AudioRecord;
import com.example.demo.service.AudioAnalysisService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/audio")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AudioAnalysisController {

    private final AudioAnalysisService audioAnalysisService;

    public AudioAnalysisController(AudioAnalysisService audioAnalysisService) {
        this.audioAnalysisService = audioAnalysisService;
    }

    @GetMapping("/devices")
    public ResponseEntity<List<RecordingDeviceDto>> getRecordingDevices() {
        return ResponseEntity.ok(audioAnalysisService.getRecordingDevices());
    }

    @GetMapping("/devices/{deviceId}")
    public ResponseEntity<RecordingDeviceDto> getDeviceById(@PathVariable String deviceId) {
        return ResponseEntity.ok(audioAnalysisService.getDeviceById(deviceId));
    }

    @PostMapping("/devices/{deviceId}/capture")
    public ResponseEntity<AudioAnalysisResponse> captureFromDevice(
            @PathVariable String deviceId,
            @RequestParam(value = "scenario", required = false) String scenario) {
        AudioAnalysisResponse response = audioAnalysisService.captureFromDevice(deviceId, scenario);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/devices/{deviceId}/stream")
    public ResponseEntity<byte[]> streamDeviceAudio(
            @PathVariable String deviceId,
            @RequestParam(value = "scenario", required = false) String scenario) {
        byte[] audioData = audioAnalysisService.getDeviceAudioStream(deviceId, scenario);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + deviceId + "_stream.wav\"")
                .body(audioData);
    }

    @PostMapping("/devices/{deviceId}/toggle-sentry")
    public ResponseEntity<RecordingDeviceDto> toggleSentry(@PathVariable String deviceId) {
        return ResponseEntity.ok(audioAnalysisService.toggleSentry(deviceId));
    }

    @PostMapping("/upload")
    public ResponseEntity<AudioAnalysisResponse> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "stationId", defaultValue = "1") Long stationId) {
        AudioAnalysisResponse response = audioAnalysisService.processAudioUpload(file, stationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<AudioRecord>> getAllAudioRecords() {
        return ResponseEntity.ok(audioAnalysisService.getAllAudioRecords());
    }
}


