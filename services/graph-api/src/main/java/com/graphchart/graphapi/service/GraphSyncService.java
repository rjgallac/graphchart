package com.graphchart.graphapi.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GraphSyncService {
    private final JdbcTemplate jdbc;

    public GraphSyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(EntityType type, UUID id, String name, UUID parentId) {
        String vertex = "SELECT * FROM cypher('university_graph', $$ MERGE (n:" + type.label()
                + " {id: '" + id + "'}) SET n.name = '" + escape(name) + "' RETURN n $$) AS (v agtype)";
        jdbc.query(vertex, rs -> { });
        if (parentId == null) return;
        EntityType parentType = switch (type) {
            case SITES -> EntityType.UNIVERSITIES;
            case ROOMS -> EntityType.SITES;
            case MACHINES -> EntityType.ROOMS;
            case SENSORS -> EntityType.MACHINES;
            case UNIVERSITIES -> null;
        };
        String relationship = "HAS_" + type.label().toUpperCase();
        String edge = "SELECT * FROM cypher('university_graph', $$ MATCH (p:" + parentType.label()
                + " {id: '" + parentId + "'}), (c:" + type.label() + " {id: '" + id
                + "'}) MERGE (p)-[:" + relationship + "]->(c) RETURN p $$) AS (v agtype)";
        jdbc.query(edge, rs -> { });
    }

    public void delete(EntityType type, UUID id) {
        String query = "SELECT * FROM cypher('university_graph', $$ MATCH (n:" + type.label()
                + " {id: '" + id + "'}) DETACH DELETE n RETURN n $$) AS (v agtype)";
        jdbc.query(query, rs -> { });
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
