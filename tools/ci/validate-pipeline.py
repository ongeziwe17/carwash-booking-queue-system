#!/usr/bin/env python3
"""Guard the release graph and trigger contracts against accidental YAML edits.

This is a static regression check. GitHub Actions runs still provide runtime evidence.
"""

from pathlib import Path
import sys

import yaml


ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github/workflows"
PIPELINE = Path(sys.argv[1]) if len(sys.argv) == 2 else WORKFLOWS / "ci-cd.yml"


def load(path):
    # BaseLoader preserves the Actions `on` key (YAML 1.1 SafeLoader treats it as a boolean).
    return yaml.load(path.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def steps(job):
    return job["steps"]


def named(job, name):
    return next((step for step in steps(job) if step.get("name") == name), None)


pipeline = load(PIPELINE)
bruno = load(WORKFLOWS / "bruno-acceptance.yml")
promotion = load(WORKFLOWS / "branch-protection.yml")
branches = ["staging", "develop", "master"]

require(pipeline["on"]["pull_request"]["branches"] == branches, "CI PR targets changed")
require(pipeline["on"]["push"]["branches"] == branches, "CI push branches changed")
require(pipeline["on"]["push"]["tags"] == ["v*"], "CI release tag trigger changed")
require(bruno["on"]["pull_request"]["branches"] == branches, "Bruno PR targets changed")
require(bruno["on"]["push"]["branches"] == branches, "Bruno push branches changed")
require("tags" not in bruno["on"]["push"], "Bruno tag trigger changed")
require(promotion["on"]["pull_request"]["branches"] == branches, "Promotion targets changed")
require("push" not in promotion["on"], "Promotion push trigger changed")
require(promotion["jobs"]["check-branch"]["name"] == "check-branch", "Promotion check renamed")

jobs = pipeline["jobs"]
required = {
    "workflow-lint",
    "maven-verification",
    "postgres-verification",
    "test-repeatability",
    "isolated-test-selection",
    "observability-validation",
    "docker-validation",
}
require(required | {"publish-container"} == set(jobs), "CI job inventory changed")
require(set(jobs["publish-container"]["needs"]) == required, "Publishing gates changed")
require(set(jobs["docker-validation"]["needs"]) == {"workflow-lint", "maven-verification"},
        "Docker dependency graph changed")
require(set(jobs["postgres-verification"]["needs"]) == {"workflow-lint"},
        "PostgreSQL dependency graph changed")
for independent in ("workflow-lint", "maven-verification", "test-repeatability",
                    "isolated-test-selection", "observability-validation"):
    require("needs" not in jobs[independent], f"{independent} unexpectedly serialized")

names = {
    "workflow-lint": "Workflow syntax validation",
    "maven-verification": "Maven verification",
    "postgres-verification": "PostgreSQL migrations, durability and concurrency",
    "isolated-test-selection": "Selected test isolation",
    "observability-validation": "Observability configuration validation",
    "docker-validation": "Docker validation",
    "publish-container": "Publish container image",
}
for job_id, name in names.items():
    require(jobs[job_id]["name"] == name, f"Required check context changed: {job_id}")
require("matrix.seed" in jobs["test-repeatability"]["name"], "Repeatability check renamed")
require(jobs["test-repeatability"]["strategy"]["matrix"]["seed"] == ["11001", "11002"],
        "Repeatability seeds changed")

require(pipeline["permissions"] == {"contents": "read"}, "Default CI permissions expanded")
require(jobs["publish-container"]["permissions"] == {"contents": "read", "packages": "write"},
        "Publishing permissions changed")
for job_id in required:
    require("permissions" not in jobs[job_id] or jobs[job_id]["permissions"] == {"contents": "read"},
            f"Unexpected permissions on {job_id}")

publish = jobs["publish-container"]
predicate = " ".join(publish["if"].split())
expected_predicate = " ".join("""
github.event_name == 'push' &&
(
  github.ref == 'refs/heads/staging' ||
  github.ref == 'refs/heads/develop' ||
  github.ref == 'refs/heads/master' ||
  startsWith(github.ref, 'refs/tags/v')
)
""".split())
require(predicate == expected_predicate, "Publishing event restriction changed")
require("always()" not in predicate and "needs" not in predicate,
        "Publishing could override implicit successful-needs condition")
require("git merge-base --is-ancestor" in named(publish, "Verify release tag provenance")["run"],
        "Release tag master ancestry check missing")
require(named(publish, "Verify release tag provenance")["if"] ==
        "startsWith(github.ref, 'refs/tags/v')", "Release provenance condition changed")
require("DOCKER_USERNAME" in named(publish, "Validate Docker Hub publishing secrets")["run"] and
        "DOCKER_PASSWORD" in named(publish, "Validate Docker Hub publishing secrets")["run"],
        "Missing publishing credential failure check")

metadata = named(publish, "Generate image metadata")["with"]
require(metadata["images"].splitlines() == [
    "${{ secrets.DOCKER_USERNAME }}/carwash-booking-queue-system",
    "ghcr.io/${{ github.repository_owner }}/carwash-booking-queue-system",
], "Published image paths changed")
expected_tags = [
    "type=raw,value=staging,enable=${{ github.ref == 'refs/heads/staging' }}",
    "type=raw,value=staging-{{commit_date 'YYYYMMDD-HHmmss' tz='Africa/Johannesburg'}},enable=${{ github.ref == 'refs/heads/staging' }}",
    "type=raw,value=develop,enable=${{ github.ref == 'refs/heads/develop' }}",
    "type=raw,value=develop-{{commit_date 'YYYYMMDD-HHmmss' tz='Africa/Johannesburg'}},enable=${{ github.ref == 'refs/heads/develop' }}",
    "type=raw,value=master,enable=${{ github.ref == 'refs/heads/master' }}",
    "type=raw,value=latest,enable=${{ github.ref == 'refs/heads/master' }}",
    "type=ref,event=tag,enable=${{ startsWith(github.ref, 'refs/tags/v') }}",
    "type=raw,value=${{ github.ref_name }}-{{commit_date 'YYYYMMDD-HHmmss' tz='Africa/Johannesburg'}},enable=${{ startsWith(github.ref, 'refs/tags/v') }}",
    "type=sha,prefix=sha-,format=short",
]
require(metadata["tags"].splitlines() == expected_tags, "Published tag conventions changed")
require(named(publish, "Build and publish Docker image")["with"]["push"] == "true",
        "Release build no longer publishes")
require(named(jobs["docker-validation"], "Build Docker image without publishing")["with"]["push"] == "false",
        "PR verification image could publish")
require(named(jobs["docker-validation"], "Reuse verified image in Compose")["run"] ==
        'docker image tag "${LOCAL_IMAGE}" hlambaq-api:local',
        "Compose must reuse the verified image")
runtime = (ROOT / "tools/ci/container-runtime.sh").read_text(encoding="utf-8")
require("docker compose up --detach --no-build --wait" in runtime,
        "Compose could rebuild instead of reusing the verified image")
for command in ("docker compose restart hlambaq-api", "docker run --detach",
                "python3 tools/smoke-observability.py"):
    require(command in runtime, f"Container runtime coverage missing: {command}")
for command in ("compose-restart", "image-smoke", "observability"):
    step = {"compose-restart": "Start persistent-profile Compose stack and verify restart recovery",
            "image-smoke": "Smoke test Docker image",
            "observability": "Verify optional observability stack end to end"}[command]
    require(named(jobs["docker-validation"], step)["run"] ==
            f"bash tools/ci/container-runtime.sh {command}", f"Missing container runtime check: {command}")
require(named(jobs["docker-validation"], "Enforce fixed critical vulnerability gate")["with"]["exit-code"] == "1",
        "Critical vulnerability gate changed")
require(named(jobs["workflow-lint"], "Validate publishing metadata templates") is not None,
        "Publishing metadata validation missing")
require(named(jobs["observability-validation"],
              "Validate observability definitions and semantic configuration") is not None,
        "Observability validation missing")
require(named(jobs["isolated-test-selection"], "Audit test isolation patterns") is not None,
        "Test isolation audit missing")
require(all("secrets.DOCKER_" not in str(jobs[job_id]) for job_id in required),
        "Publishing credentials exposed to verification jobs")

print("CI triggers, check names, dependencies, permissions, release conditions, tags and gates validated.")
