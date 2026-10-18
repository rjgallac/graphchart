package com.graphchart.telemetry.api;

import com.graphchart.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {
    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping("/readings")
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingResponse ingest(@Valid @RequestBody ReadingRequest request) {
        return telemetryService.ingest(request);
    }

    @GetMapping("/readings")
    public List<ReadingResponse> readings(@RequestParam UUID sensorId,
                                          @RequestParam(required = false) OffsetDateTime from,
                                          @RequestParam(required = false) OffsetDateTime to) {
        return telemetryService.readings(sensorId, from, to);
    }

    @GetMapping("/latest")
    public ReadingResponse latest(@RequestParam UUID sensorId) {
        return telemetryService.latest(sensorId);
    }

    @DeleteMapping("/retention")
    public int runRetention() {
        return telemetryService.deleteExpiredReadings();
    }
}
