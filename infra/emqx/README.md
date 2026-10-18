# EMQX → Telemetry API integration

The local broker accepts anonymous MQTT connections for development. The telemetry API listens at `http://telemetry-api:8082` inside the Compose network.

To configure the forwarding rule in the EMQX Dashboard:

1. Open http://localhost:18083 and sign in with the initial local Dashboard credentials. EMQX requires changing the default Dashboard password on first login.
2. Open **Integration → Connectors** and create an **HTTP Server** connector with URL `http://telemetry-api:8082`.
3. Open **Integration → Rules** and create a rule for:

   ```sql
   SELECT payload
   FROM "university/+/site/+/room/+/machine/+/sensor/+/reading"
   ```

4. Add an HTTP action using the connector. Configure it as `POST` to `/api/telemetry/readings` with `Content-Type: application/json` and use the message payload as the request body.
5. Publish a JSON reading to a matching topic, for example:

   ```text
   university/u1/site/s1/room/r1/machine/m1/sensor/00000000-0000-0000-0000-000000000001/reading
   ```

   ```json
   {
     "sensorId": "00000000-0000-0000-0000-000000000001",
     "metricType": "temperature",
     "value": 22.4,
     "unit": "°C"
   }
   ```

EMQX rules are the intended bridge between MQTT messages and the HTTP ingestion API; the rule engine can filter and transform messages before forwarding them.[EMQX rule engine](https://docs.emqx.com/en/emqx/latest/develop/data-integration/rules.html)
