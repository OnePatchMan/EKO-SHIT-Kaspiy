package com.example.demo;

import com.example.demo.controller.SystemStatusController;
import com.example.demo.exception.AiServiceException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExternalAiErrorContractTests {
    @RestController static class UpstreamFailureController {
        @GetMapping("/test-unavailable") public String unavailable() {
            throw new AiServiceException(503, "AI service unavailable");
        }
        @GetMapping("/test-invalid") public String invalid() {
            throw new AiServiceException(422, "Invalid audio");
        }
    }
    @Test void unavailableIs503Not500() throws Exception {
        MockMvcBuilders.standaloneSetup(new UpstreamFailureController())
            .setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(get("/test-unavailable")).andExpect(status().isServiceUnavailable());
    }
    @Test void invalidAudioIs422Not500() throws Exception {
        MockMvcBuilders.standaloneSetup(new UpstreamFailureController())
            .setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(get("/test-invalid")).andExpect(status().is(422));
    }
    @Mock StationService stations;
    @Mock WaterTelemetryService telemetry;
    @Mock AlertService alerts;
    @Mock AiServiceClient ai;
    @InjectMocks SystemStatusController controller;
    @Test void unavailableAiIsNotAdvertisedAsAvailable() throws Exception {
        try (var ignored = MockitoAnnotations.openMocks(this)) {
            assertFalse(controller.status().getBody().aiServiceAvailable());
        }
    }
}
