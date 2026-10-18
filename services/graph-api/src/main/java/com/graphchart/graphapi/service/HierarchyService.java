package com.graphchart.graphapi.service;

import com.graphchart.graphapi.api.EntityRequest;
import com.graphchart.graphapi.api.EntityResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class HierarchyService {
    private final JdbcTemplate jdbc;
    private final GraphSyncService graphSync;

    public HierarchyService(JdbcTemplate jdbc, GraphSyncService graphSync) {
        this.jdbc = jdbc;
        this.graphSync = graphSync;
    }

    public List<EntityResponse> list(String rawType) {
        EntityType type = EntityType.from(rawType);
        String parentSelect = type.parentColumn() == null ? "NULL::uuid" : type.parentColumn();
        String extra = type == EntityType.SENSORS ? ", metric_type, unit, active" : ", NULL::text AS metric_type, NULL::text AS unit, true AS active";
        return jdbc.query("SELECT id, name, " + parentSelect + " AS parent_id" + extra + ", created_at, updated_at FROM " + type.table() + " ORDER BY name",
                (rs, row) -> map(type, rs));
    }

    public EntityResponse get(String rawType, UUID id) {
        EntityType type = EntityType.from(rawType);
        String parentSelect = type.parentColumn() == null ? "NULL::uuid" : type.parentColumn();
        String extra = type == EntityType.SENSORS ? ", metric_type, unit, active" : ", NULL::text AS metric_type, NULL::text AS unit, true AS active";
        return jdbc.query("SELECT id, name, " + parentSelect + " AS parent_id" + extra + ", created_at, updated_at FROM " + type.table() + " WHERE id = ?",
                        (rs, row) -> map(type, rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new NotFoundException(type.label() + " not found: " + id));
    }

    @Transactional
    public EntityResponse create(String rawType, EntityRequest request) {
        EntityType type = EntityType.from(rawType);
        validate(type, request);
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (type == EntityType.UNIVERSITIES) {
            jdbc.update("INSERT INTO universities (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)", id, request.name(), now, now);
        } else if (type == EntityType.SENSORS) {
            jdbc.update("INSERT INTO sensors (id, machine_id, name, metric_type, unit, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    id, request.parentId(), request.name(), request.metricType(), request.unit(), request.active() == null || request.active(), now, now);
            jdbc.update("INSERT INTO sensor_location_history (sensor_id, machine_id, valid_from) VALUES (?, ?, ?)", id, request.parentId(), now);
        } else {
            jdbc.update("INSERT INTO " + type.table() + " (id, " + type.parentColumn() + ", name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    id, request.parentId(), request.name(), now, now);
            if (type == EntityType.MACHINES) {
                jdbc.update("INSERT INTO machine_location_history (machine_id, room_id, valid_from) VALUES (?, ?, ?)", id, request.parentId(), now);
            }
        }
        graphSync.upsert(type, id, request.name(), request.parentId());
        return get(type.table(), id);
    }

    @Transactional
    public EntityResponse update(String rawType, UUID id, EntityRequest request) {
        EntityType type = EntityType.from(rawType);
        EntityResponse current = get(type.table(), id);
        validate(type, request);
        OffsetDateTime now = OffsetDateTime.now();
        if (type == EntityType.UNIVERSITIES) {
            jdbc.update("UPDATE universities SET name = ?, updated_at = ? WHERE id = ?", request.name(), now, id);
        } else if (type == EntityType.SENSORS) {
            if (!current.parentId().equals(request.parentId())) {
                closeAndRecordSensorLocation(id, request.parentId(), now);
            }
            jdbc.update("UPDATE sensors SET machine_id = ?, name = ?, metric_type = ?, unit = ?, active = ?, updated_at = ? WHERE id = ?",
                    request.parentId(), request.name(), request.metricType(), request.unit(), request.active() == null || request.active(), now, id);
        } else {
            if (!current.parentId().equals(request.parentId())) {
                if (type == EntityType.MACHINES) closeAndRecordMachineLocation(id, request.parentId(), now);
            }
            jdbc.update("UPDATE " + type.table() + " SET " + type.parentColumn() + " = ?, name = ?, updated_at = ? WHERE id = ?",
                    request.parentId(), request.name(), now, id);
        }
        graphSync.upsert(type, id, request.name(), request.parentId());
        return get(type.table(), id);
    }

    @Transactional
    public void delete(String rawType, UUID id) {
        EntityType type = EntityType.from(rawType);
        get(type.table(), id);
        jdbc.update("DELETE FROM " + type.table() + " WHERE id = ?", id);
        graphSync.delete(type, id);
    }

    public List<String> graph(String rawType, UUID id) {
        EntityType type = EntityType.from(rawType);
        get(type.table(), id);
        String query = "SELECT v::text FROM cypher('university_graph', $$ MATCH (n:" + type.label() + " {id: '" + id + "'})-[r*1..5]-(v) RETURN v $$) AS (v agtype)";
        return jdbc.queryForList(query, String.class);
    }

    private void validate(EntityType type, EntityRequest request) {
        if (type != EntityType.UNIVERSITIES && request.parentId() == null) {
            throw new IllegalArgumentException(type.label() + " requires parentId");
        }
        if (type == EntityType.SENSORS) {
            if (request.metricType() == null || !(request.metricType().equals("temperature") || request.metricType().equals("speed"))) {
                throw new IllegalArgumentException("Sensor metricType must be temperature or speed");
            }
            if (request.unit() == null || request.unit().isBlank()) {
                throw new IllegalArgumentException("Sensor unit is required");
            }
        }
        if (type != EntityType.UNIVERSITIES && !parentExists(type, request.parentId())) {
            throw new NotFoundException("Parent not found: " + request.parentId());
        }
    }

    private boolean parentExists(EntityType type, UUID id) {
        EntityType parent = switch (type) {
            case SITES -> EntityType.UNIVERSITIES;
            case ROOMS -> EntityType.SITES;
            case MACHINES -> EntityType.ROOMS;
            case SENSORS -> EntityType.MACHINES;
            case UNIVERSITIES -> null;
        };
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + parent.table() + " WHERE id = ?", Integer.class, id) > 0;
    }

    private void closeAndRecordMachineLocation(UUID machineId, UUID roomId, OffsetDateTime now) {
        jdbc.update("UPDATE machine_location_history SET valid_to = ? WHERE machine_id = ? AND valid_to IS NULL", now, machineId);
        jdbc.update("INSERT INTO machine_location_history (machine_id, room_id, valid_from) VALUES (?, ?, ?)", machineId, roomId, now);
    }

    private void closeAndRecordSensorLocation(UUID sensorId, UUID machineId, OffsetDateTime now) {
        jdbc.update("UPDATE sensor_location_history SET valid_to = ? WHERE sensor_id = ? AND valid_to IS NULL", now, sensorId);
        jdbc.update("INSERT INTO sensor_location_history (sensor_id, machine_id, valid_from) VALUES (?, ?, ?)", sensorId, machineId, now);
    }

    private EntityResponse map(EntityType type, ResultSet rs) throws java.sql.SQLException {
        return new EntityResponse(rs.getObject("id", UUID.class), type.table(), rs.getString("name"),
                rs.getObject("parent_id", UUID.class), rs.getString("metric_type"), rs.getString("unit"),
                rs.getBoolean("active"), rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }
}
