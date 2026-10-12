#!/usr/bin/env python3
"""Read-only structural validation. Semantic PromQL/collector validation runs separately in CI."""
import json
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[1]
class ComposeLoader(yaml.SafeLoader):
    pass
ComposeLoader.add_constructor('!override', lambda loader, node: loader.construct_sequence(node))
for path in (root / 'observability').rglob('*.yml'):
    assert isinstance(yaml.safe_load(path.read_text()), dict), path
stack = yaml.load((root / 'docker-compose.observability.yml').read_text(), Loader=ComposeLoader)
for name, service in stack['services'].items():
    if 'image' in service:
        assert ':' in service['image'] and not service['image'].endswith(':latest'), name
    for port in service.get('ports', []):
        assert port.startswith('127.0.0.1:'), (name, port)
dashboard = json.loads((root / 'observability/grafana/dashboards/operations.json').read_text())
assert dashboard['uid'] and dashboard['schemaVersion'] >= 39
assert len({p['id'] for p in dashboard['panels']}) == len(dashboard['panels'])
assert any(p.get('datasource', {}).get('uid') == 'prometheus' for p in dashboard['panels'])
for panel in dashboard['panels']:
    assert set(panel['gridPos']) == {'x', 'y', 'w', 'h'}
    for target in panel.get('targets', []):
        assert target['expr'] and target['refId']
        assert not any(word in target['expr'] for word in ('payment_', 'sms_', 'email_'))
alerts = yaml.safe_load((root / 'observability/alerts.yml').read_text())
for rule in alerts['groups'][0]['rules']:
    assert rule['for'] and rule['annotations']['description'] and rule['annotations']['runbook_url']
print('Observability YAML, pinned images, local bindings, dashboard structure and alert annotations validated.')
