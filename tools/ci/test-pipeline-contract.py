#!/usr/bin/env python3
"""Exercise safety-check failure paths without publishing or changing repository state."""

from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / ".github/workflows/ci-cd.yml").read_text(encoding="utf-8")
validator = ROOT / "tools/ci/validate-pipeline.py"


def check(label, old, new):
    assert source.count(old) == 1, f"Ambiguous fixture for {label}"
    with TemporaryDirectory() as directory:
        candidate = Path(directory) / "ci-cd.yml"
        candidate.write_text(source.replace(old, new, 1), encoding="utf-8")
        result = subprocess.run([sys.executable, str(validator), str(candidate)],
                                capture_output=True, text=True, check=False)
        assert result.returncode != 0, f"Unsafe change went undetected: {label}"


baseline = subprocess.run([sys.executable, str(validator)],
                          capture_output=True, text=True, check=False)
assert baseline.returncode == 0, baseline.stderr + baseline.stdout

check("failed PostgreSQL verification", "      - postgres-verification\n", "      # PostgreSQL gate removed\n")
check("failed Maven verification", "      - maven-verification\n      - postgres-verification\n",
      "      # Maven gate removed\n      - postgres-verification\n")
check("failed repeatability", "      - test-repeatability\n", "      # Seed gate removed\n")
check("failed selected tests", "      - isolated-test-selection\n", "      # Selected-test gate removed\n")
check("failed Docker/security validation", "      - docker-validation\n", "      # Docker gate removed\n")
check("failed observability validation", "      - observability-validation\n", "      # Observability gate removed\n")
check("skipped/cancelled gate override", "      github.event_name == 'push' &&\n",
      "      always() && github.event_name == 'push' &&\n")
check("PR publishing", "      github.event_name == 'push' &&\n",
      "      github.event_name == 'pull_request' &&\n")
check("unapproved branch", "        github.ref == 'refs/heads/master' ||\n",
      "        github.ref == 'refs/heads/master' ||\n        github.ref == 'refs/heads/feature/test' ||\n")
check("release provenance", "          if ! git merge-base --is-ancestor",
      "          if ! git merge-base --is-removed")
check("missing credentials", '      - name: Validate Docker Hub publishing secrets\n',
      '      - name: Skip Docker Hub publishing secret validation\n')
check("critical vulnerability", "          exit-code: '1'\n", "          exit-code: '0'\n")
check("published image paths", "            ghcr.io/${{ github.repository_owner }}/carwash-booking-queue-system\n",
      "            ghcr.io/${{ github.repository_owner }}/new-image\n")
check("release tags", "            type=raw,value=latest,enable=${{ github.ref == 'refs/heads/master' }}\n",
      "            type=raw,value=latest,enable=${{ github.ref == 'refs/heads/staging' }}\n")

print("Pipeline safety contract rejects missing gates, unsafe events, secrets, provenance and tag changes.")
