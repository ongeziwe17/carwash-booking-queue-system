#!/usr/bin/env python3
"""Create ignored local credentials using Docker's htpasswd; never print secrets."""
import os
from pathlib import Path
import secrets
import subprocess

root = Path(__file__).resolve().parents[1]
target = root / '.observability'
if target.exists():
    raise SystemExit('Credentials already exist. See the guide before rotating them.')
password = secrets.token_urlsafe(32)
# Password enters the container through stdin, never command arguments or environment.
result = subprocess.run(['docker', 'run', '--rm', '-i', '--entrypoint', 'htpasswd',
                         'httpd:2.4.65-alpine', '-niBC', '10', 'scraper'],
                        input=password + '\n', text=True, capture_output=True, check=True)
hashed = result.stdout.strip().split(':', 1)[1]
if not hashed.startswith(('$2y$', '$2b$', '$2a$')):
    raise SystemExit('Unexpected bcrypt output')
os.umask(0o077)
target.mkdir(mode=0o700)
(target / 'scrape-password').write_text(password + '\n')
(target / 'grafana-password').write_text(secrets.token_urlsafe(32) + '\n')
# Compose bind-mounted secrets retain host permissions. The 0700 parent protects host access;
# read-only 0444 files let the unprivileged Prometheus/Grafana users read their mounts.
os.chmod(target / 'scrape-password', 0o444)
os.chmod(target / 'grafana-password', 0o444)
(target / 'compose.env').write_text("CARWASH_SCRAPE_PASSWORD_HASH='{bcrypt}" + hashed + "'\n")
print('Local observability credentials created. Follow documentation/OBSERVABILITY.md to start the stack.')
