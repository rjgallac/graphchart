package com.graphchart.graphapi.api;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record EntityRequest(
        @NotBlank String name,
        UUID parentId,
        String metricType,
        String unit,
        Boolean active
) {
}
