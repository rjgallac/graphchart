package com.graphchart.telemetry.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadingResponse(
        long id,
        UUID eventId,
        UUID sensorId,
        String metricType,
        BigDecimal value,
        String unit,
        OffsetDateTime occurredAt,
        OffsetDateTime ingestedAt,
        String quality
) {
}
