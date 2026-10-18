package com.graphchart.telemetry.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadingRequest(
        UUID eventId,
        @NotNull UUID sensorId,
        @NotBlank String metricType,
        @NotNull @DecimalMin("-1000000") BigDecimal value,
        @NotBlank String unit,
        OffsetDateTime occurredAt,
        String quality
) {
}
