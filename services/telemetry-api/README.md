# Telemetry API

Spring Boot service for receiving and querying sensor readings.

Run it with:

```bash
docker compose up -d --build telemetry-api
```

It listens on `http://localhost:8082`.

Example ingestion request:

```bash
curl -X POST http://localhost:8082/api/telemetry/readings \
  -H "Content-Type: application/json" \
  -d '{"sensorId":"00000000-0000-0000-0000-000000000000","metricType":"temperature","value":22.4,"unit":"°C"}'
```

Readings are retained for seven days and the scheduled cleanup runs daily. The `DELETE /api/telemetry/retention` endpoint is available for local verification.
