# University Machine Graph & Telemetry Platform

Greenfield project for mapping university equipment and locations as a graph, ingesting sensor telemetry, and presenting both the topology and measurements through web applications.

## Proposed architecture

```text
                        +----------------------+
                        | React Administration  |
                        | universities/sites/   |
                        | rooms/machines/sensors|
                        +----------+-----------+
                                   |
                                   v
                        +----------------------+
                        | Spring Boot Graph API |
                        | CRUD + graph queries  |
                        +----------+-----------+
                                   |
                                   v
                        +----------------------+
                        | PostgreSQL + Apache   |
                        | AGE (graph database)   |
                        +----------------------+

Sensors -> MQTT -> EMQX -> webhook/rule -> Spring Boot Telemetry API
                                      |
                                      v
                        +----------------------+
                        | Telemetry database    |
                        | time-series readings  |
                        +----------+-----------+
                                   ^
                                   |
                        +----------+-----------+
                        | Grafana / React       |
                        | monitoring frontend   |
                        +----------------------+
```

The first implementation uses one PostgreSQL server with separate databases or schemas for graph and telemetry data. The graph and telemetry workloads remain logically separate so they can be split into separate PostgreSQL instances later if required.

## Planned components

| Component | Responsibility | Initial technology |
|---|---|---|
| Graph API | CRUD for universities, sites, rooms, machines, sensors; graph relationships and topology queries | Spring Boot, Spring Web, Spring Data JDBC/JPA, Flyway/Liquibase |
| Telemetry API | Accept normalized sensor readings from EMQX; validate, persist, query, and expose chart-ready data | Spring Boot, Spring Web, standard PostgreSQL tables |
| Administration UI | Create and edit the physical inventory and sensor configuration | React, TypeScript, Vite |
| Monitoring UI | Combine graph locations/topology with telemetry and status data | React, TypeScript, Vite |
| Graph store | University topology and relationships | PostgreSQL with Apache AGE extension |
| Telemetry store | Temperature and speed readings with timestamps | Standard PostgreSQL tables with time-based retention |
| MQTT broker | Receive sensor messages and route them to the telemetry API | EMQX |
| Dashboards | Optional operational dashboards and alerting | Grafana |

## Domain model

The relational model should hold stable entity data and identifiers. Apache AGE should represent relationships and traversal-friendly topology.

Suggested entities:

- `University`
- `Site` — a campus, building, or other university location
- `Room`
- `Machine`
- `Sensor`
- `SensorReading` — timestamped measurement, owned by the telemetry service

Suggested graph labels/relationships:

```text
(University)-[:HAS_SITE]->(Site)
(Site)-[:HAS_ROOM]->(Room)
(Room)-[:CONTAINS_MACHINE]->(Machine)
(Machine)-[:HAS_SENSOR]->(Sensor)
```

Every graph entity should have a stable UUID that matches the corresponding API/database record. Sensor readings should refer to the sensor UUID and should not duplicate the complete location hierarchy.

## Sensor message contract

EMQX should receive messages on a topic such as:

```text
university/{universityId}/site/{siteId}/room/{roomId}/machine/{machineId}/sensor/{sensorId}/reading
```

The preferred payload is a small, versioned JSON document:

```json
{
  "schemaVersion": 1,
  "sensorId": "00000000-0000-0000-0000-000000000000",
  "occurredAt": "2026-01-01T12:00:00Z",
  "measurements": {
    "temperatureC": 22.4,
    "speedRpm": 1450
  },
  "quality": "GOOD"
}
```

The telemetry API should be idempotent where possible, validate that the sensor exists and is active, preserve the device timestamp, record an ingestion timestamp, and return a traceable message/event ID. The expected sampling rate is one reading per minute per sensor. Readings should be retained for one week, after which a scheduled cleanup job can remove them.

## Repository layout (target)

```text
.
├── compose.yaml
├── infra/
│   ├── postgres-age/
│   │   ├── Dockerfile
│   │   └── init/
│   └── emqx/
│       └── ...
├── services/
│   ├── graph-api/
│   └── telemetry-api/
├── apps/
│   ├── admin-ui/
│   └── monitoring-ui/
├── grafana/
│   ├── dashboards/
│   └── provisioning/
└── README.md
```

## Local development and Docker Compose

`compose.yaml` should initially start:

- PostgreSQL built from a custom image with Apache AGE installed and enabled.
- EMQX with its dashboard and MQTT listener.
- The graph API and telemetry API, once their first Dockerfiles exist.
- Grafana with a PostgreSQL datasource and a starter dashboard.

The PostgreSQL image should pin compatible PostgreSQL and AGE versions, install AGE as a PostgreSQL extension, and enable it through database initialization as required by the chosen AGE release. Do not assume the stock `postgres` image contains AGE.

At minimum, Compose should provide health checks, named volumes for PostgreSQL/EMQX/Grafana data, a private application network, and development credentials via `.env`. Authentication is intentionally out of scope for the first local prototype, so the services should be treated as localhost-only and must not be exposed to an untrusted network.

Example startup flow once the compose file exists:

```bash
cp .env.example .env
docker compose up --build
```

The exact ports, credentials, and AGE image strategy are intentionally left as implementation decisions until the questions below are answered.

## Start the Graph API

The Graph API is started by Docker Compose together with PostgreSQL/Apache AGE, EMQX, and Grafana. It is exposed locally on port `8081`.

From the repository root:

```bash
cp .env.example .env
docker compose up -d --build
```

On Windows PowerShell, use this equivalent command to create the environment file:

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

Check that the Graph API is healthy:

```bash
curl http://localhost:8081/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

Useful endpoints:

```text
GET  http://localhost:8081/api/universities
GET  http://localhost:8081/api/sites
GET  http://localhost:8081/api/rooms
GET  http://localhost:8081/api/machines
GET  http://localhost:8081/api/sensors
```

View Graph API logs:

```bash
docker compose logs -f graph-api
```

Rebuild and restart only the Graph API after code changes:

```bash
docker compose up -d --build graph-api
```

The administration frontend is available at http://localhost:5173. Start or rebuild it with:

```bash
docker compose up -d --build admin-ui
```

The frontend uses a Vite development proxy, so browser requests to `/api/*` are forwarded to the Graph API. This avoids CORS configuration during local development.

The monitoring frontend is available at http://localhost:5174. Start or rebuild it with:

```bash
docker compose up -d --build monitoring-ui
```

It combines the hierarchy from the Graph API with latest readings and recent history from the Telemetry API. Select a sensor card to inspect its current value and recent readings.

To seed the local demo hierarchy used by the simulator:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\seed-demo.ps1
docker compose --profile simulator up -d --build sensor-simulator
```

The seed creates two universities with four sites, five rooms, six machines, and twelve sensors. It includes `Demo University → Main Campus → Engineering Lab → Test Pump` and `City Technical University → Science Campus → Robotics Lab → Robot Arm A`. The seed is idempotent and can be run again after the database is already initialized.

On the monitoring UI, use the **All universities** filter to select a university, then use the **All sites** filter to narrow the machine cards to one site. Select a sensor card to inspect its latest reading and history.

The Telemetry API is available at http://localhost:8082. Start or rebuild it with:

```bash
docker compose up -d --build telemetry-api
```

Check it with:

```bash
curl http://localhost:8082/actuator/health
```

Ingest a reading:

```bash
curl -X POST http://localhost:8082/api/telemetry/readings \
  -H "Content-Type: application/json" \
  -d '{"sensorId":"00000000-0000-0000-0000-000000000000","metricType":"temperature","value":22.4,"unit":"°C"}'
```

Grafana includes a provisioned telemetry dashboard at http://localhost:3000. Its PostgreSQL datasource points at the `telemetry` database and displays temperature and speed readings over the selected time range.

EMQX forwarding setup is documented in [infra/emqx/README.md](infra/emqx/README.md). It uses an EMQX rule to forward matching MQTT payloads to the Telemetry API.

The EMQX connector, HTTP action, and rule are created automatically by the one-shot `emqx-setup` Compose service. To run the local sensor simulator, enable its Compose profile:

```bash
docker compose --profile simulator up -d --build sensor-simulator
```

The simulator discovers all active temperature and speed sensors from the Graph API, then publishes one reading for each sensor every minute to EMQX. After adding sensors, click Refresh in the monitoring UI or wait for the next simulator cycle. View its output with:

```bash
docker compose logs -f sensor-simulator
```

Stop the simulator without stopping the rest of the platform:

```bash
docker compose stop sensor-simulator
```

Stop the local environment:

```bash
docker compose down
```

The current prototype has no application authentication and is intended for localhost development only. `docker compose down -v` also removes the PostgreSQL, EMQX, and Grafana volumes, including all local data.

## API boundaries

### Graph API

Initial CRUD endpoints:

```text
GET/POST         /api/universities
GET/PATCH/DELETE /api/universities/{id}
GET/POST         /api/sites
GET/PATCH/DELETE /api/sites/{id}
GET/POST         /api/rooms
GET/PATCH/DELETE /api/rooms/{id}
GET/POST         /api/machines
GET/PATCH/DELETE /api/machines/{id}
GET/POST         /api/sensors
GET/PATCH/DELETE /api/sensors/{id}
GET              /api/graph/{entityType}/{id}
```

Creation/update operations should enforce parent relationships and should update the AGE graph transactionally with the relational record, or use an explicit outbox/reconciliation process if AGE transactions cannot be safely combined with the selected persistence approach.

### Telemetry API

Initial endpoints:

```text
POST /api/telemetry/readings
GET  /api/telemetry/readings?sensorId=...&from=...&to=...
GET  /api/telemetry/series?machineId=...&metric=temperatureC&from=...&to=...
GET  /api/telemetry/latest?sensorId=...
```

EMQX should call the ingestion endpoint through a rule-engine webhook or HTTP integration. The telemetry API should be the system of record for readings; Grafana should query the telemetry store through its datasource rather than being treated as the write target.

## Security and operations

- Use MQTT username/password or certificate authentication; do not expose anonymous MQTT outside local development.
- Restrict EMQX webhook delivery to the telemetry API and authenticate it with a service credential or signed request.
- Add validation, rate limits, payload-size limits, and dead-letter handling for malformed readings.
- Add OpenAPI documentation, structured logs, correlation IDs, health/readiness endpoints, and metrics.
- Keep graph management permissions separate from telemetry ingestion permissions.
- Add retention/downsampling policies for high-volume readings.
- Back up PostgreSQL and test restoration before production use.
- Treat Grafana as an optional visualization client; the monitoring React app can combine graph topology and telemetry API responses directly.

## Delivery plan

### Phase 1 — infrastructure proof

- [x] Pin PostgreSQL and Apache AGE versions and add the AGE-enabled database image.
- [x] Add `compose.yaml` for PostgreSQL + AGE, EMQX, and Grafana.
- [x] Verify AGE is available with `CREATE EXTENSION age` and create `university_graph`.
- [x] Verify EMQX starts with the local no-auth development configuration.
- [x] Add `.env.example` and health checks.

### Phase 2 — graph API and administration UI

- [x] Create the Spring Boot graph API on port 8081.
- [x] Add initialization SQL for universities, sites, rooms, machines, and sensors.
- [x] Define UUIDs, parent validation, delete behavior, and API error format.
- [x] Add AGE graph creation and relationship synchronization.
- [x] Preserve machine and sensor location history when equipment moves.
- [x] Add CRUD screens and forms in the administration React app.
- [ ] Add integration tests for CRUD and graph traversal.

### Phase 3 — telemetry path

- [x] Choose standard PostgreSQL telemetry storage and schema.
- [x] Create the Spring Boot telemetry API on port 8082.
- [x] Define the MQTT topic and JSON payload contract.
- [x] Configure an EMQX HTTP connector, action, and rule to call the ingestion API.
- [x] Add idempotency, validation, timestamps, and seven-day retention cleanup.
- [x] Add a simulator that publishes temperature and speed readings every minute.
- [x] Add Grafana datasource and starter temperature/speed dashboards.
- [x] Add controller-level API tests for CRUD and telemetry validation.
- [x] Add a repeatable MQTT → EMQX → Telemetry API smoke path.

### Phase 4 — visualization

- [x] Add Grafana datasource, provisioning, and starter dashboards.
- [x] Create the monitoring React app on port 5174.
- [x] Combine graph location/topology results with latest readings and time series.
- [x] Add machine/sensor reporting status and recent history.
- [ ] Add configurable time range selection and richer failure states.
- [ ] Add responsive layouts and role-based access.

### Phase 5 — production readiness

- [ ] Add authentication/authorization, TLS, secret management, and audit logging.
- [ ] Add CI builds, container scanning, migrations in deployment, backups, and restore testing.
- [ ] Load-test MQTT ingestion and telemetry queries.
- [ ] Decide whether graph and telemetry PostgreSQL workloads need separate instances.

## Open questions

The following decisions are confirmed for the initial implementation:

- One PostgreSQL server.
- Standard PostgreSQL telemetry tables; no TimescaleDB dependency.
- Grafana included in the first Compose environment.
- No application authentication for the local prototype.
- Measurements limited to temperature and speed.
- Machine and sensor location history must be preserved.
- One reading per minute per sensor.
- Telemetry retention of one week.

Remaining questions:

- The hierarchy is fixed as University → Site → Room → Machine → Sensor.
- Sensor readings will store both the device timestamp and server ingestion timestamp.
- Docker Compose is the initial deployment target; Kubernetes/cloud deployment is out of scope for now.

## Suggested first decision

For the first prototype, use one Compose PostgreSQL server with separate `graphdb` and `telemetry` databases, a custom AGE-enabled image, EMQX, Grafana, and two Spring Boot services. Store one-minute temperature/speed readings in standard PostgreSQL, retain them for one week, and preserve room/machine/sensor location history in the graph and relational records. Keep the no-auth setup localhost-only until authentication is added.
