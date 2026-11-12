package com.example.demo.service;

import com.example.demo.exception.AiServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AiServiceClientTest {
    private HttpServer server;
    private AiServiceClient client;
    private String request;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        client = new AiServiceClient("http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); }
    private void response(String path, int status, String body) {
        server.createContext(path, exchange -> {
            request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });
    }
    @Test void sendsBinaryMultipartAndTimezoneTimestampWithoutInferringFromFilename() {
        response("/v1/audio/analyze", 200, "{\"events\":[],\"status\":\"no_target_event\",\"model_version\":\"yamnet-1\"}");
        Map<String,Object> result = client.analyzeAudio(new byte[]{0,1,2,3}, "boat.wav", 12L);
        assertEquals("no_target_event", result.get("status"));
        assertTrue(request.contains("name=\"file\"; filename=\"boat.wav\""));
        assertTrue(request.contains("name=\"station_id\""));
        assertTrue(request.contains("\r\n12\r\n"));
        assertTrue(request.contains("name=\"recorded_at\""));
        assertTrue(request.contains("Z\r\n"));
        assertTrue(request.contains(new String(new byte[]{0,1,2,3}, StandardCharsets.ISO_8859_1)));
    }
    @Test void preservesValidationFailureInsteadOfFallback() {
        response("/v1/audio/analyze", 422, "{\"detail\":\"cannot decode WAV audio\"}");
        assertEquals(422, assertThrows(AiServiceException.class,
            () -> client.analyzeAudio(new byte[]{1}, "file.wav", 1L)).getStatusCode());
    }
    @Test void unavailableServiceIsExplicit() {
        server.stop(0);
        assertEquals(503, assertThrows(AiServiceException.class,
            () -> client.analyzeAudio(new byte[]{1}, "boat.wav", 1L)).getStatusCode());
    }
    @Test void rejectsEmptyAndOversizedAudioBeforeRequest() {
        assertEquals(422, assertThrows(AiServiceException.class, () -> client.analyzeAudio(new byte[0], "x.wav", 1L)).getStatusCode());
        assertEquals(422, assertThrows(AiServiceException.class, () -> client.analyzeAudio(new byte[10 * 1024 * 1024 + 1], "x.wav", 1L)).getStatusCode());
    }
    @Test void forecastUsesRealJsonRequestAndKeepsValidationMetadata() {
        response("/v1/spawning/forecast", 200, "{\"biological_forecast_validated\":false,\"mode\":\"scenario_conditions_forecast\"}");
        Map<String,Object> result = client.forecast(Map.of("species", "carp"));
        assertTrue(request.contains("\"species\":\"carp\""));
        assertEquals(false, result.get("biological_forecast_validated"));
    }
    @Test void healthRequiresModelReady() {
        response("/health", 200, "{\"status\":\"partial\",\"audio_model_ready\":false}");
        assertFalse(client.isAvailable());
    }
    @Test void readyHealthIsAvailable() {
        response("/health", 200, "{\"audio_model_ready\":true}");
        assertTrue(client.isAvailable());
    }
    @Test void malformedResponseCannotBecomeSuccessfulAnalysis() {
        response("/v1/audio/analyze", 200, "not-json");
        assertEquals(503, assertThrows(AiServiceException.class,
            () -> client.analyzeAudio(new byte[]{1}, "boat.wav", 1L)).getStatusCode());
    }
}
