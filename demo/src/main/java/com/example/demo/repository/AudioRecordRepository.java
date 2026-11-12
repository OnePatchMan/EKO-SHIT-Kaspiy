package com.example.demo.repository;

import com.example.demo.model.AudioRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AudioRecordRepository extends JpaRepository<AudioRecord, Long> {

    List<AudioRecord> findAllByOrderByCreatedAtDesc();

    List<AudioRecord> findByStationIdOrderByCreatedAtDesc(Long stationId);
}

