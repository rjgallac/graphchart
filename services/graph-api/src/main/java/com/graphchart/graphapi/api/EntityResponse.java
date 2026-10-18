package com.graphchart.graphapi.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EntityResponse(
        UUID id,
        String type,
        String name,
        UUID parentId,
        String metricType,
        String unit,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
