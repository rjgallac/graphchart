import json
import os
import random
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone

import paho.mqtt.client as mqtt


BROKER = os.getenv("MQTT_HOST", "emqx")
PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_USERNAME = os.getenv("MQTT_USERNAME", "")
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD", "")
INTERVAL = int(os.getenv("SIMULATOR_INTERVAL_SECONDS", "60"))
GRAPH_API_URL = os.getenv("GRAPH_API_URL", "http://graph-api:8081")
TEMPERATURE_SENSOR_ID = os.getenv("SIMULATOR_TEMPERATURE_SENSOR_ID", "00000000-0000-0000-0000-000000000001")
SPEED_SENSOR_ID = os.getenv("SIMULATOR_SPEED_SENSOR_ID", "00000000-0000-0000-0000-000000000002")
BASE_TOPIC = os.getenv("SIMULATOR_TOPIC", "")
RECONNECT_DELAY_SECONDS = 5


def sensors_from_graph_api():
    try:
        with urllib.request.urlopen(f"{GRAPH_API_URL}/api/sensors", timeout=5) as response:
            sensors = json.loads(response.read())
        return [sensor for sensor in sensors if sensor.get("active", True) and sensor.get("metricType") in {"temperature", "speed"}]
    except (OSError, urllib.error.URLError, json.JSONDecodeError) as error:
        print(f"could not discover sensors from Graph API: {error}; using fallback sensors", flush=True)
        return [
            {"id": TEMPERATURE_SENSOR_ID, "metricType": "temperature", "unit": "°C"},
            {"id": SPEED_SENSOR_ID, "metricType": "speed", "unit": "rpm"},
        ]


def connect(client):
    while not client.is_connected():
        try:
            client.connect(BROKER, PORT, keepalive=60)
        except OSError as error:
            print(f"MQTT connection failed: {error}; retrying in {RECONNECT_DELAY_SECONDS}s", flush=True)
            time.sleep(RECONNECT_DELAY_SECONDS)
            continue

        # connect() returns before the network loop processes CONNACK. Give it
        # time to finish before attempting another socket connection.
        for _ in range(50):
            if client.is_connected():
                return
            time.sleep(0.1)
        print(f"MQTT broker did not accept the connection; retrying in {RECONNECT_DELAY_SECONDS}s", flush=True)
        time.sleep(RECONNECT_DELAY_SECONDS)


def publish(client, sensor_id, metric_type, value, unit):
    message = {
        "eventId": str(uuid.uuid4()),
        "sensorId": sensor_id,
        "metricType": metric_type,
        "value": round(value, 2),
        "unit": unit,
        "occurredAt": datetime.now(timezone.utc).isoformat(),
        "quality": "GOOD",
    }
    topic = BASE_TOPIC or f"university/demo/site/main/room/lab/machine/pump/sensor/{sensor_id}/reading"
    while True:
        while not client.is_connected():
            print("MQTT connection is unavailable; reconnecting before publish", flush=True)
            connect(client)

        result = client.publish(topic, json.dumps(message), qos=1)
        if result.rc != mqtt.MQTT_ERR_SUCCESS:
            print(f"MQTT publish failed: {mqtt.error_string(result.rc)}; retrying", flush=True)
            time.sleep(RECONNECT_DELAY_SECONDS)
            continue
        try:
            result.wait_for_publish(timeout=10)
            break
        except RuntimeError as error:
            print(f"MQTT publish interrupted: {error}; retrying", flush=True)
    print(f"published {metric_type}={message['value']} {unit} to {topic}", flush=True)


def main():
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="graphchart-simulator")
    if MQTT_USERNAME:
        client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)

    def on_disconnect(_client, _userdata, _disconnect_flags, reason_code, _properties):
        print(f"MQTT disconnected: {reason_code}", flush=True)

    client.on_disconnect = on_disconnect
    client.loop_start()
    try:
        connect(client)

        while True:
            sensors = sensors_from_graph_api()
            print(f"discovered {len(sensors)} active temperature/speed sensors", flush=True)
            for sensor in sensors:
                metric_type = sensor["metricType"]
                value = random.uniform(20, 26) if metric_type == "temperature" else random.uniform(1350, 1500)
                unit = sensor.get("unit") or ("°C" if metric_type == "temperature" else "rpm")
                publish(client, sensor["id"], metric_type, value, unit)
            time.sleep(INTERVAL)
            continue
            publish(client, TEMPERATURE_SENSOR_ID, "temperature", random.uniform(20, 26), "°C")
            publish(client, SPEED_SENSOR_ID, "speed", random.uniform(1350, 1500), "rpm")
            time.sleep(INTERVAL)
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
