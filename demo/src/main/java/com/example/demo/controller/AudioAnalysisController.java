package com.example.demo.controller;

import com.example.demo.dto.AudioAnalysisResponse;
import com.example.demo.model.AudioRecord;
import com.example.demo.service.AudioAnalysisService;
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

