package com.graphchart.telemetry.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphchart.telemetry.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelemetryController.class)
class TelemetryControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TelemetryService telemetryService;

    @Test
    void acceptsTemperatureReading() throws Exception {
        UUID sensorId = UUID.randomUUID();
        when(telemetryService.ingest(any(ReadingRequest.class)))
                .thenReturn(new ReadingResponse(1, UUID.randomUUID(), sensorId, "temperature", BigDecimal.valueOf(22.4), "°C", null, null, "GOOD"));

        mvc.perform(post("/api/telemetry/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReadingRequest(null, sensorId, "temperature", BigDecimal.valueOf(22.4), "°C", null, "GOOD"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metricType").value("temperature"))
                .andExpect(jsonPath("$.value").value(22.4));
    }

    @Test
    void rejectsMissingSensorId() throws Exception {
        mvc.perform(post("/api/telemetry/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metricType\":\"temperature\",\"value\":22.4,\"unit\":\"°C\"}"))
                .andExpect(status().isBadRequest());
    }
}
