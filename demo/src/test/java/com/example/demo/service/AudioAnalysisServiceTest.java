package com.example.demo.service;

import com.example.demo.exception.AiServiceException;
import com.example.demo.model.*;
import com.example.demo.repository.AudioRecordRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AudioAnalysisServiceTest {
    @TempDir Path uploads;
    AudioRecordRepository records;
    AlertService alerts;
    AiServiceClient ai;
    AudioAnalysisService service;
    @BeforeEach void setup() {
        records = mock(AudioRecordRepository.class);
        alerts = mock(AlertService.class);
        ai = mock(AiServiceClient.class);
        StationService stations = mock(StationService.class);
        Station station = new Station(); station.setId(1L); station.setName("Test station");
        when(stations.getStationById(1L)).thenReturn(station);
        when(records.save(any())).thenAnswer(invocation -> { AudioRecord record = invocation.getArgument(0); record.setId(7L); return record; });
        service = new AudioAnalysisService(records, stations, alerts, ai);
        ReflectionTestUtils.setField(service, "uploadDir", uploads.toString());
    }
    Map<String,Object> result(String status, List<Map<String,Object>> events) {
        return Map.of("status",status,"events",events,"model_version","yamnet-test", "score_is_calibrated_probability",false,
            "target_scores",Map.of("engine_detected",0.7));
    }
    @Test void identicalAudioRenamedBoatDoesNotBecomeBoat() {
        when(ai.analyzeAudio(any(), anyString(), eq(1L))).thenReturn(result("no_target_event",List.of()));
        var first = service.processAudioUpload(new MockMultipartFile("file","ambient.wav","audio/wav",new byte[]{1,2}),1L);
        var second = service.processAudioUpload(new MockMultipartFile("file","boat_gun.wav","audio/wav",new byte[]{1,2}),1L);
        assertEquals("no_target_event",first.detectedClass());
        assertEquals(first.detectedClass(),second.detectedClass());
        assertNull(second.confidence());
        assertFalse(second.alertGenerated());
        verifyNoInteractions(alerts);
    }
    @Test void genericEngineRemainsGenericWithActualScoreAndMetadata() {
        Map<String,Object> event = Map.of("type","engine_detected","score",0.713,"start_seconds",0.48,"end_seconds",1.9);
        when(ai.analyzeAudio(any(),anyString(),eq(1L))).thenReturn(result("events_detected",List.of(event)));
        var response = service.processAudioUpload(new MockMultipartFile("file","boat.wav","audio/wav",new byte[]{1}),1L);
        assertEquals("engine_detected",response.detectedClass());
        assertEquals(0.713,response.confidence());
        assertFalse(response.alertGenerated());
        assertTrue(response.analysisSource().contains("yamnet-test"));
        assertEquals(List.of(event),response.analysisDetails().get("events"));
        verifyNoInteractions(alerts);
    }
    @Test void uncertainDoesNotBecomeThreat() {
        when(ai.analyzeAudio(any(),anyString(),eq(1L))).thenReturn(result("uncertain",List.of()));
        var response=service.processAudioUpload(new MockMultipartFile("file","shot.wav","audio/wav",new byte[]{1}),1L);
        assertEquals("uncertain",response.detectedClass());
        assertNull(response.confidence());
        assertFalse(response.alertGenerated());
    }
    @Test void failedServiceDoesNotStoreFabricatedClassification() {
        when(ai.analyzeAudio(any(),anyString(),eq(1L))).thenThrow(new AiServiceException(503,"offline"));
        assertThrows(AiServiceException.class,() -> service.processAudioUpload(new MockMultipartFile("file","boat.wav","audio/wav",new byte[]{1}),1L));
        verify(records,never()).save(any());
        verifyNoInteractions(alerts);
    }
    @Test void deviceCaptureUsesSameModelMappingAndFullMetadata() {
        when(ai.analyzeAudio(any(),anyString(),eq(1L))).thenReturn(result("uncertain",List.of()));
        var response=service.captureFromDevice("DEV-DAM-01","boat");
        assertEquals("uncertain",response.detectedClass());
        assertFalse(response.alertGenerated());
        assertEquals("yamnet-test",response.analysisDetails().get("model_version"));
        verify(ai).analyzeAudio(any(),eq("boat_engine_outboard.wav"),eq(1L));
    }
    @Test void malformedModelDataCannotDefaultToBoat() {
        when(ai.analyzeAudio(any(),anyString(),eq(1L))).thenReturn(Map.of("events",List.of()));
        assertThrows(AiServiceException.class,() -> service.processAudioUpload(new MockMultipartFile("file","boat.wav","audio/wav",new byte[]{1}),1L));
        verify(records,never()).save(any());
    }
    @Test void explicitBoatEventCreatesReviewAlertWithoutClaimingPoaching() {
        when(ai.analyzeAudio(any(),anyString(),eq(1L))).thenReturn(result("events_detected",List.of(Map.of("type","boat_suspected","score",0.42))));
        Alert alert=new Alert(); alert.setId(15L);
        when(alerts.createAlert(any(),eq(AlertType.ACOUSTIC_BOAT_DETECTED),eq(AlertSeverity.WARNING),anyString(),anyString())).thenReturn(alert);
        var response=service.processAudioUpload(new MockMultipartFile("file","plain.wav","audio/wav",new byte[]{1}),1L);
        assertTrue(response.alertGenerated()); assertEquals(15L,response.alertId());
        assertEquals("boat_suspected",response.detectedClass()); assertEquals(0.42,response.confidence());
    }
    @Test void storesFullModelEvidenceForLaterHistory() {
        Map<String,Object> event = Map.of("type","engine_detected","score",0.713,"start_seconds",0.48,"end_seconds",1.9);
        Map<String,Object> raw = Map.of("analysis_id","analysis-123","status","events_detected",
            "model_version","yamnet-test","events",List.of(event));
        when(ai.analyzeAudio(any(),anyString(),eq(1L))).thenReturn(raw);
        service.processAudioUpload(new MockMultipartFile("file","plain.wav","audio/wav",new byte[]{1}),1L);
        var saved = org.mockito.ArgumentCaptor.forClass(AudioRecord.class);
        verify(records).save(saved.capture());
        var evidence = tools.jackson.databind.json.JsonMapper.builder().build().readTree(saved.getValue().getAnalysisDetailsJson());
        assertEquals("analysis-123",evidence.get("analysis_id").asString());
        assertEquals("yamnet-test",evidence.get("model_version").asString());
        assertEquals(0.48,evidence.get("events").get(0).get("start_seconds").asDouble());
        assertEquals(1.9,evidence.get("events").get(0).get("end_seconds").asDouble());
        assertEquals(0.713,evidence.get("events").get(0).get("score").asDouble());
    }

}
