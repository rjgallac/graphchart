import json
import os
import time
import urllib.error
import urllib.request


BASE_URL = os.getenv("EMQX_URL", "http://emqx:18083")
USERNAME = os.getenv("EMQX_USERNAME", "admin")
PASSWORD = os.getenv("EMQX_PASSWORD", "public")
TELEMETRY_API_URL = os.getenv("TELEMETRY_API_URL", "http://telemetry-api:8082")


def request(method, path, token=None, payload=None):
    body = None if payload is None else json.dumps(payload).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{BASE_URL}{path}", data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        if error.code == 409:
            return error.code, json.loads(raw) if raw else {}
        raise RuntimeError(f"EMQX {method} {path} failed ({error.code}): {raw}") from error


def wait_for(url):
    for _ in range(60):
        try:
            urllib.request.urlopen(url, timeout=3).close()
            return
        except Exception:
            time.sleep(2)
    raise RuntimeError(f"Timed out waiting for {url}")


def named(items, name):
    return next((item for item in items if item.get("name") == name), None)


def items_from(response):
    return response if isinstance(response, list) else response.get("data", [])


def main():
    wait_for(f"{BASE_URL}/api/v5/status")
    wait_for(f"{TELEMETRY_API_URL}/actuator/health")
    _, login = request("POST", "/api/v5/login", payload={"username": USERNAME, "password": PASSWORD})
    token = login["token"]

    _, connectors = request("GET", "/api/v5/connectors", token)
    connector = named(items_from(connectors), "telemetry-api")
    if not connector:
        _, connector = request("POST", "/api/v5/connectors", token, {
            "name": "telemetry-api",
            "type": "http",
            "url": TELEMETRY_API_URL,
            "headers": {"content-type": "application/json"},
            "enable": True,
        })
    connector_name = connector.get("name", "telemetry-api")

    _, actions = request("GET", "/api/v5/actions", token)
    action = named(items_from(actions), "telemetry-api-http")
    if not action:
        _, action = request("POST", "/api/v5/actions", token, {
            "name": "telemetry-api-http",
            "type": "http",
            "connector": connector_name,
            "parameters": {
                "path": "/api/telemetry/readings",
                "method": "post",
                "headers": {"content-type": "application/json"},
                "body": "${payload}",
            },
            "resource_opts": {"query_mode": "sync"},
            "enable": True,
        })
    action_id = action.get("id", "http:telemetry-api-http")

    _, rules = request("GET", "/api/v5/rules", token)
    if not named(items_from(rules), "telemetry-api-rule"):
        request("POST", "/api/v5/rules", token, {
            "name": "telemetry-api-rule",
            "sql": 'SELECT payload FROM "university/+/site/+/room/+/machine/+/sensor/+/reading"',
            "actions": [action_id],
            "enable": True,
            "description": "Forward sensor readings to the GraphChart Telemetry API",
        })
    print("EMQX telemetry connector, action, and rule are ready")


if __name__ == "__main__":
    main()
