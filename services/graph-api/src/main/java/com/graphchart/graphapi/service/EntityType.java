package com.graphchart.graphapi.service;

import java.util.Arrays;

public enum EntityType {
    UNIVERSITIES("universities", "University", null),
    SITES("sites", "Site", "university_id"),
    ROOMS("rooms", "Room", "site_id"),
    MACHINES("machines", "Machine", "room_id"),
    SENSORS("sensors", "Sensor", "machine_id");

    private final String table;
    private final String label;
    private final String parentColumn;

    EntityType(String table, String label, String parentColumn) {
        this.table = table;
        this.label = label;
        this.parentColumn = parentColumn;
    }

    public String table() { return table; }
    public String label() { return label; }
    public String parentColumn() { return parentColumn; }

    public static EntityType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.table.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown entity type: " + value));
    }
}
