#!/usr/bin/env python3
"""Validate the rendered reference deployment; CI also uses server-side Kubernetes validation."""
import re
import sys
from pathlib import Path

import yaml


def validate(text: str, *, require_real_digest: bool = False, profile: str = "production") -> None:
    assert profile in ("staging", "production")
    namespace = "hlambaq-staging" if profile == "staging" else "hlambaq"
    documents = [doc for doc in yaml.safe_load_all(text) if doc]
    indexed = {(doc["kind"], doc["metadata"]["name"]): doc for doc in documents}
    required = {(kind, name) for kind, name in (
        ("Namespace", namespace), ("ServiceAccount", "hlambaq-api"),
        ("ConfigMap", "hlambaq-api-config"), ("Deployment", "hlambaq-api"),
        ("Service", "hlambaq-api"), ("PodDisruptionBudget", "hlambaq-api"),
        ("NetworkPolicy", "hlambaq-api-ingress"),
    )}
    assert indexed.keys() == required, "Unexpected/missing reference resources or committed Secret"
    config = indexed["ConfigMap", "hlambaq-api-config"]["data"]
    assert config["SPRING_PROFILES_ACTIVE"] == profile
    assert config["SECURE_BOOTSTRAP_ADMIN_ENABLED"] == "false"
    assert set(config) == {"SPRING_PROFILES_ACTIVE", "SPRING_APP_NAME", "SERVER_PORT",
                           "SECURE_PASSWORD_BCRYPT_STRENGTH", "SECURE_PASSWORD_MIN_LENGTH",
                           "SECURE_PASSWORD_MAX_UTF8_BYTES", "SECURE_JWT_ISSUER",
                           "SECURE_JWT_ACCESS_TOKEN_TTL", "SECURE_BOOTSTRAP_ADMIN_ENABLED",
                           "SPRING_DATASOURCE_MAX_POOL_SIZE", "SPRING_DATASOURCE_MIN_IDLE",
                           "SPRING_DATASOURCE_CONNECTION_TIMEOUT_MS", "CARWASH_TRACE_SAMPLE_PROBABILITY"}
    assert not ({"SECURE_JWT_SECRET", "SPRING_DATASOURCE_PASSWORD", "CARWASH_SCRAPE_USERNAME",
                 "CARWASH_SCRAPE_PASSWORD_HASH", "SECURE_BOOTSTRAP_ADMIN_PASSWORD"} & config.keys()), \
        "Credential in ConfigMap"
    deployment = indexed["Deployment", "hlambaq-api"]["spec"]
    assert deployment["replicas"] == 2
    assert deployment["strategy"]["rollingUpdate"] == {"maxSurge": 1, "maxUnavailable": 0}
    pod = deployment["template"]["spec"]
    assert pod["terminationGracePeriodSeconds"] > 20
    assert pod["automountServiceAccountToken"] is False
    assert pod["securityContext"]["seccompProfile"]["type"] == "RuntimeDefault"
    assert pod["securityContext"]["runAsNonRoot"] is True
    assert pod["securityContext"]["runAsUser"] == 10001
    container, = pod["containers"]
    digest = re.fullmatch(r"[a-z0-9./:-]+@sha256:([a-f0-9]{64})", container["image"])
    assert digest, "Image must be referenced by immutable digest"
    if require_real_digest:
        assert digest.group(1) != "0" * 64, "Replace the example digest before deployment"
    security = container["securityContext"]
    assert security["readOnlyRootFilesystem"] is True
    assert security["allowPrivilegeEscalation"] is False
    assert security["capabilities"]["drop"] == ["ALL"]
    assert {entry["name"] for entry in container["env"]} == {
        "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
        "SECURE_JWT_SECRET", "CARWASH_SCRAPE_USERNAME", "CARWASH_SCRAPE_PASSWORD_HASH",
    }
    assert all("secretKeyRef" in entry["valueFrom"] for entry in container["env"])
    assert {name: container[name]["httpGet"]["path"] for name in
            ("startupProbe", "readinessProbe", "livenessProbe")} == {
        "startupProbe": "/actuator/health/liveness",
        "readinessProbe": "/actuator/health/readiness",
        "livenessProbe": "/actuator/health/liveness",
    }
    assert container["resources"]["limits"]["memory"] == "768Mi"
    assert container["volumeMounts"][0]["mountPath"] == "/tmp"
    assert pod["volumes"][0]["emptyDir"]["sizeLimit"] == "64Mi"
    assert indexed["PodDisruptionBudget", "hlambaq-api"]["spec"]["minAvailable"] == 1
    network = indexed["NetworkPolicy", "hlambaq-api-ingress"]["spec"]
    assert network["policyTypes"] == ["Ingress"]
    assert {source["namespaceSelector"]["matchLabels"]["hlambaq-access"]
            for source in network["ingress"][0]["from"]} == {"gateway", "monitoring"}


if __name__ == "__main__":
    if len(sys.argv) < 2 or any(arg not in ("--real-digest", "--profile=staging") for arg in sys.argv[2:]):
        raise SystemExit("Usage: validate-deployment.py RENDERED_YAML [--real-digest] [--profile=staging]")
    validate(Path(sys.argv[1]).read_text(), require_real_digest="--real-digest" in sys.argv[2:],
             profile="staging" if "--profile=staging" in sys.argv[2:] else "production")
    print("Rendered deployment structure and non-secret configuration validated.")
