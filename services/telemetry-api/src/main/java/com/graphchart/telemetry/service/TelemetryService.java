package com.graphchart.telemetry.service;

import com.graphchart.telemetry.api.ReadingRequest;
import com.graphchart.telemetry.api.ReadingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TelemetryService {
    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public TelemetryService(JdbcTemplate jdbc, @Value("${telemetry.retention-days:7}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public ReadingResponse ingest(ReadingRequest request) {
        validateMetric(request.metricType());
        UUID eventId = request.eventId() == null ? UUID.randomUUID() : request.eventId();
        OffsetDateTime occurredAt = request.occurredAt() == null ? OffsetDateTime.now() : request.occurredAt();
        try {
            long id = jdbc.queryForObject("INSERT INTO sensor_readings (event_id, sensor_id, metric_type, value, unit, occurred_at, quality) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                    Long.class, eventId, request.sensorId(), request.metricType(), request.value(), request.unit(), occurredAt,
                    request.quality() == null || request.quality().isBlank() ? "GOOD" : request.quality());
            return getById(id);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new DuplicateKeyException("eventId already exists: " + eventId, exception);
        }
    }

    public List<ReadingResponse> readings(UUID sensorId, OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime end = to == null ? OffsetDateTime.now() : to;
        OffsetDateTime start = from == null ? end.minusDays(1) : from;
        return jdbc.query("SELECT id, event_id, sensor_id, metric_type, value, unit, occurred_at, ingested_at, quality FROM sensor_readings WHERE sensor_id = ? AND occurred_at >= ? AND occurred_at <= ? ORDER BY occurred_at",
                (rs, row) -> map(rs), sensorId, start, end);
    }

    public ReadingResponse latest(UUID sensorId) {
        return jdbc.query("SELECT id, event_id, sensor_id, metric_type, value, unit, occurred_at, ingested_at, quality FROM sensor_readings WHERE sensor_id = ? ORDER BY occurred_at DESC LIMIT 1",
                        (rs, row) -> map(rs), sensorId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("No readings found for sensor: " + sensorId));
    }

    @Scheduled(cron = "0 15 3 * * *")
    public int deleteExpiredReadings() {
        return jdbc.update("DELETE FROM sensor_readings WHERE ingested_at < now() - (? * interval '1 day')", retentionDays);
    }

    private ReadingResponse getById(long id) {
        return jdbc.queryForObject("SELECT id, event_id, sensor_id, metric_type, value, unit, occurred_at, ingested_at, quality FROM sensor_readings WHERE id = ?",
                (rs, row) -> map(rs), id);
    }

    private void validateMetric(String metricType) {
        if (!"temperature".equals(metricType) && !"speed".equals(metricType)) {
            throw new IllegalArgumentException("metricType must be temperature or speed");
        }
    }

    private ReadingResponse map(ResultSet rs) throws java.sql.SQLException {
        return new ReadingResponse(rs.getLong("id"), rs.getObject("event_id", UUID.class), rs.getObject("sensor_id", UUID.class),
                rs.getString("metric_type"), rs.getBigDecimal("value"), rs.getString("unit"),
                rs.getObject("occurred_at", OffsetDateTime.class), rs.getObject("ingested_at", OffsetDateTime.class), rs.getString("quality"));
    }
}
