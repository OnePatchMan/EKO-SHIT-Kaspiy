package com.example.demo;

import com.example.demo.model.Station;
import com.example.demo.model.WaterTelemetry;
import com.example.demo.repository.StationRepository;
import com.example.demo.repository.WaterTelemetryRepository;
import com.example.demo.service.AiServiceClient;
import com.example.demo.service.ForecastService;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExternalForecastTests {
    @Test void sendsObservedUnitsAndZonedTimesAndPreservesScenarioMetadata() {
        var stations = mock(StationRepository.class);
        var telemetry = mock(WaterTelemetryRepository.class);
        var client = mock(AiServiceClient.class);
        var station = new Station(); station.setId(7L); station.setTerritory("Delta");
        when(stations.findById(7L)).thenReturn(Optional.of(station));
        var row = new WaterTelemetry(); row.setRecordedAt(LocalDateTime.of(2026, 11, 12, 12, 0)); row.setTemperatureC(18.5); row.setWaterLevelCm(230.0);
        when(telemetry.findByStationOrderByRecordedAtDesc(eq(station), any())).thenReturn(List.of(row));
        Map<String,Object> external = new HashMap<>();
        external.put("mode", "scenario_conditions_forecast"); external.put("is_demo",true);
        external.put("valid_from","2026-11-12T09:00:00Z"); external.put("valid_to","2026-11-15T09:00:00Z");
        external.put("validation_status","not_validated_on_spawning_observations"); external.put("probability",null);
        external.put("trajectory",List.of(Map.of("timestamp","2026-11-13T09:00:00Z")));
        external.put("algorithm_version","carp-scenario-0.1.0"); external.put("missing_data",List.of("inundation"));
        when(client.forecast(anyMap())).thenAnswer(invocation -> {
            Map<String,Object> payload = invocation.getArgument(0);
            assertEquals("cyprinus_carpio",payload.get("species"));
            assertEquals("demo_synthetic",payload.get("data_source"));
            assertEquals("local gauge zero",payload.get("level_datum"));
            var history = (List<Map<String,Object>>)payload.get("history");
            assertEquals("2026-11-12T09:00:00Z",history.getFirst().get("timestamp"));
            assertEquals(2.3,history.getFirst().get("water_level_m"));
            assertFalse(history.getFirst().containsKey("inundated"));
            assertFalse(history.getFirst().containsKey("salinity_psu"));
            return external;
        });
        var service = new ForecastService(stations,telemetry,client,"Europe/Moscow","demo_synthetic","local gauge zero");
        var response = service.calculateSpawningForecast(null,7L);
        assertNull(response.suitabilityIndex()); assertNull(response.forecastDetails().get("probability"));
        assertEquals("scenario_conditions_forecast",response.forecastDetails().get("mode"));
        assertEquals(java.time.LocalDate.of(2026,11,15),response.windowEnd());
        verify(client).forecast(anyMap());
    }
    @Test void unknownLevelDatumAndMissingTemperatureAreNotFabricated() {
        var stations=mock(StationRepository.class); var telemetry=mock(WaterTelemetryRepository.class); var client=mock(AiServiceClient.class);
        var station=new Station(); station.setId(1L); when(stations.findById(1L)).thenReturn(Optional.of(station));
        var row=new WaterTelemetry(); row.setRecordedAt(LocalDateTime.now()); row.setWaterLevelCm(240.0);
        when(telemetry.findByStationOrderByRecordedAtDesc(eq(station),any())).thenReturn(List.of(row));
        when(client.forecast(anyMap())).thenAnswer(invocation -> {
            Map<String,Object> payload=invocation.getArgument(0);
            var history=(List<Map<String,Object>>)payload.get("history");
            assertFalse(history.getFirst().containsKey("water_level_m"));
            assertFalse(history.getFirst().containsKey("temperature_c"));
            assertFalse(payload.containsKey("level_datum"));
            throw new IllegalArgumentException("temperature_c is required");
        });
        var service=new ForecastService(stations,telemetry,client,"Europe/Moscow","demo_synthetic","");
        assertThrows(IllegalArgumentException.class,()->service.calculateSpawningForecast("cyprinus_carpio",1L));
    }
    @Test void rejectsUnsupportedSpeciesBeforeCallingExternalService() {
        var client=mock(AiServiceClient.class);
        var service=new ForecastService(mock(StationRepository.class),mock(WaterTelemetryRepository.class),client,"Europe/Moscow","demo_synthetic","");
        assertThrows(IllegalArgumentException.class,()->service.calculateSpawningForecast("Pusa caspica",1L));
        verifyNoInteractions(client);
    }

    @Test void missingLevelDatumIsVisibleInReturnedMetadata() {
        var stations=mock(StationRepository.class); var telemetry=mock(WaterTelemetryRepository.class); var client=mock(AiServiceClient.class);
        var station=new Station(); station.setId(1L); when(stations.findById(1L)).thenReturn(Optional.of(station));
        when(telemetry.findByStationOrderByRecordedAtDesc(eq(station),any())).thenReturn(List.of());
        when(client.forecast(anyMap())).thenReturn(validScenario());
        var result=new ForecastService(stations,telemetry,client,"Europe/Moscow","demo_synthetic","").calculateSpawningForecast("   ",1L);
        assertEquals("cyprinus_carpio",result.species());
        assertEquals(List.of("inundation","water_level_m","level_datum"),result.forecastDetails().get("missing_data"));
        assertTrue(result.forecastDetails().get("warnings").toString().contains("level_datum"));
        assertNull(result.suitabilityIndex());
    }
    @Test void missingStationCannotProduceAForecastForInventedTerritory() {
        var stations=mock(StationRepository.class); var client=mock(AiServiceClient.class);
        when(stations.findById(99L)).thenReturn(Optional.empty());
        var service=new ForecastService(stations,mock(WaterTelemetryRepository.class),client,"Europe/Moscow","demo_synthetic","");
        assertThrows(com.example.demo.exception.ResourceNotFoundException.class,()->service.calculateSpawningForecast(null,99L));
        verifyNoInteractions(client);
    }

    private Map<String,Object> validScenario() {
        Map<String,Object> r=new HashMap<>();
        r.put("mode","scenario_conditions_forecast"); r.put("is_demo",true);
        r.put("validation_status","not_validated_on_spawning_observations");r.put("algorithm_version","test-version");
        r.put("valid_from","2026-11-12T09:00:00Z");r.put("valid_to","2026-11-15T09:00:00Z");r.put("probability",null);
        r.put("trajectory",List.of(Map.of("temperature_c",17)));r.put("missing_data",List.of("inundation","water_level_m"));r.put("warnings",List.of("not validated"));return r;
    }
    @Test void malformedForecastCannotBecomeASuccessfulCard() {
        var stations=mock(StationRepository.class);var client=mock(AiServiceClient.class);var telemetry=mock(WaterTelemetryRepository.class);
        var station=new Station();station.setId(1L);when(stations.findById(1L)).thenReturn(Optional.of(station));
        when(telemetry.findByStationOrderByRecordedAtDesc(eq(station),any())).thenReturn(List.of());
        when(client.forecast(anyMap())).thenReturn(Map.of("detail","wrong service"));
        var service=new ForecastService(stations,telemetry,client,"Europe/Moscow","demo_synthetic","");
        assertThrows(com.example.demo.exception.AiServiceException.class,()->service.calculateSpawningForecast(null,1L));
    }
}
