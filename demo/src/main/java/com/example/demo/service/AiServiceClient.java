package com.example.demo.service;

import com.example.demo.exception.AiServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** HTTP integration only. Model code and assets live in the separately deployed AI service. */
@Service
public class AiServiceClient {
    private static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE = new ParameterizedTypeReference<>() {};
    private final RestClient client;

    public AiServiceClient(@Value("${ecoshield.ai.service-url:http://127.0.0.1:8001}") String serviceUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(60));
        client = RestClient.builder().baseUrl(serviceUrl).requestFactory(factory).build();
    }

    public Map<String, Object> analyzeAudio(byte[] data, String filename, Long stationId) {
        if (data == null || data.length == 0 || data.length > MAX_AUDIO_BYTES) {
            throw new AiServiceException(422, "Требуется WAV длительностью 1–60 секунд, размером не более 10 МиБ. MP3/WebM необходимо преобразовать в WAV.");
        }
        if (stationId == null) throw new AiServiceException(422, "Не указана станция аудиозаписи.");
        String uploadName = filename == null || filename.isBlank() ? "recording.wav" : filename;
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(data) {
            @Override public String getFilename() { return uploadName; }
        });
        parts.add("station_id", stationId.toString());
        // Capture/upload time is explicit UTC; the upstream validates media bytes and duration.
        parts.add("recorded_at", Instant.now().toString());
        return post("/v1/audio/analyze", parts, MediaType.MULTIPART_FORM_DATA);
    }

    public Map<String, Object> forecast(Map<String, Object> payload) {
        if (payload == null) throw new AiServiceException(422, "Не переданы данные сценария.");
        return post("/v1/spawning/forecast", payload, MediaType.APPLICATION_JSON);
    }

    public boolean isAvailable() {
        try {
            Map<String,Object> health = client.get().uri("/health").retrieve().body(RESPONSE);
            return health != null && Boolean.TRUE.equals(health.get("audio_model_ready"));
        } catch (RestClientException error) {
            return false;
        }
    }

    private Map<String, Object> post(String path, Object body, MediaType contentType) {
        try {
            Map<String,Object> result = client.post().uri(path).contentType(contentType)
                .body(body).retrieve().body(RESPONSE);
            if (result == null || result.isEmpty()) throw new AiServiceException(503, "Сервис ИИ вернул пустой ответ.");
            return result;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 422 || error.getStatusCode().value() == 413) {
                String detail = error.getResponseBodyAsString();
                if (detail.length() > 500) detail = detail.substring(0, 500);
                String guidance = path.equals("/v1/audio/analyze")
                    ? " Для аудио требуется WAV 1–60 с, до 10 МиБ; MP3/WebM не поддерживаются." : "";
                throw new AiServiceException(422, "Сервис ИИ отклонил входные данные." + guidance + " " + detail, error);
            }
            throw new AiServiceException(503, "Внешний сервис ИИ недоступен (HTTP " + error.getStatusCode().value() + ").", error);
        } catch (RestClientException error) {
            throw new AiServiceException(503, "Внешний сервис ИИ недоступен или вернул некорректный ответ.", error);
        }
    }
}
