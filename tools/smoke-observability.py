#!/usr/bin/env python3
"""Verify the local stack without printing credentials, response bodies, logs or trace content."""
import base64
import json
from pathlib import Path
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid

root = Path(__file__).resolve().parents[1]
def request(url, headers=None):
    with urlopen(Request(url, headers=headers or {}), timeout=5) as response:
        return response.read()

def eventually(check, description):
    for _ in range(45):
        try:
            if check():
                print(description + ': passed')
                return
        except (HTTPError, URLError, TimeoutError, OSError, ValueError):
            pass
        time.sleep(2)
    raise SystemExit(description + ': failed')

eventually(lambda: json.loads(request('http://127.0.0.1:8080/actuator/health/readiness')) == {'status': 'UP'}, 'Readiness')
basic = base64.b64encode(('scraper:' + (root / '.observability/scrape-password').read_text().strip()).encode()).decode()
assert b'jvm_memory_used_bytes' in request('http://127.0.0.1:8080/actuator/prometheus', {'Authorization': 'Basic ' + basic})
eventually(lambda: any(float(item['value'][1]) == 1 for item in json.loads(request(
    'http://127.0.0.1:9090/api/v1/query?query=up%7Bjob%3D%22carwash%22%7D'))['data']['result']), 'Authenticated Prometheus target')
grafana = base64.b64encode(('observability-admin:' + (root / '.observability/grafana-password').read_text().strip()).encode()).decode()
eventually(lambda: any(item['uid'] == 'hlambaq-operations' for item in json.loads(request(
    'http://127.0.0.1:3000/api/search', {'Authorization': 'Basic ' + grafana}))), 'Provisioned Grafana dashboard')
trace_id = uuid.uuid4().hex
try:
    request('http://127.0.0.1:8080/api/auth/me?secret=observability-privacy-sentinel',
            {'traceparent': '00-' + trace_id + '-1234567890123456-01'})
except HTTPError as error:
    assert error.code == 401
def trace_received():
    payload = request('http://127.0.0.1:3200/api/traces/' + trace_id, {'Accept': 'application/json'})
    assert b'observability-privacy-sentinel' not in payload
    data = json.loads(payload)
    return bool(data.get('batches') or data.get('resourceSpans'))
eventually(trace_received, 'HTTP to collector to Tempo trace path and privacy')
