package com.example.demo;
import com.example.demo.model.AudioRecord;
import com.example.demo.repository.*;
import com.example.demo.service.AudioAnalysisService;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class AudioHistoryLoadingTests {
    @Autowired AudioRecordRepository records;
    @Autowired StationRepository stations;
    @Autowired AudioAnalysisService service;
    @Test void historyLoadsStationBeforeTransactionCloses() {
        var station=stations.findAll().getFirst();
        var record=new AudioRecord(station,"history.wav","history.wav","uncertain",null,"UNKNOWN","test-model");
        record.setAnalysisDetailsJson("{\"model_version\":\"test-model\",\"events\":[]}");
        var saved=records.save(record);
        var found=service.getAllAudioRecords().stream().filter(r->r.getId().equals(saved.getId())).findFirst().orElseThrow();
        assertTrue(Hibernate.isInitialized(found.getStation()));
        assertNotNull(found.getStation().getName());
        assertTrue(found.getAnalysisDetailsJson().contains("test-model"));
        records.deleteById(saved.getId());
    }
}
