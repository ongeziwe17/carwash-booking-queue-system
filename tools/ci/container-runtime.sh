#!/usr/bin/env bash
set -euo pipefail

# The Docker validation job builds one image, then invokes these runtime checks.
case "${1:?Expected compose-restart, image-smoke or observability}" in
  compose-restart)
    set -euo pipefail
    docker compose up --detach --no-build --wait
    for _ in {1..40}; do
      if curl --silent --show-error --fail http://127.0.0.1:8080/actuator/health/readiness --output /dev/null; then
        break
      fi
      sleep 2
    done
    curl --silent --show-error --fail http://127.0.0.1:8080/actuator/health/readiness --output /dev/null
    curl --silent --show-error --fail http://127.0.0.1:8080/v3/api-docs --output /dev/null
    docker compose restart hlambaq-api
    for _ in {1..40}; do
      if curl --silent --show-error --fail http://127.0.0.1:8080/actuator/health/readiness --output /dev/null; then
        curl --silent --show-error --fail http://127.0.0.1:8080/v3/api-docs --output /dev/null
        exit 0
      fi
      sleep 2
    done
    echo "Compose API did not recover after restart."
    exit 1
    ;;
  image-smoke)
    set -euo pipefail

    container_name="hlambaq-api-smoke-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"

    trap 'docker logs "${container_name}" || true; docker rm --force "${container_name}" || true' EXIT

    docker run --detach \
      --name "${container_name}" \
      --publish 18080:8080 \
      --env SPRING_PROFILES_ACTIVE=test \
      --env SPRING_APP_NAME=hlambaq-api \
      --env SERVER_PORT=8080 \
      --env SECURE_PASSWORD_BCRYPT_STRENGTH=4 \
      --env SECURE_PASSWORD_MIN_LENGTH=12 \
      --env SECURE_PASSWORD_MAX_UTF8_BYTES=72 \
      --env SECURE_JWT_ISSUER=carwash-booking-queue-system-ci \
      --env SECURE_JWT_ACCESS_TOKEN_TTL=20m \
      --env SECURE_JWT_SECRET="${CI_JWT_SECRET}" \
      --env SECURE_BOOTSTRAP_ADMIN_ENABLED=false \
      "${LOCAL_IMAGE}"

    for _ in {1..30}; do
      if curl --silent --show-error --fail \
        http://127.0.0.1:18080/actuator/health/readiness \
        --output /dev/null; then
        curl --silent --show-error --fail http://127.0.0.1:18080/v3/api-docs --output /dev/null
        exit 0
      fi

      if ! docker inspect --format '{{.State.Running}}' "${container_name}" \
        | grep --quiet true; then
        echo "Smoke-test container stopped before becoming ready."
        exit 1
      fi

      sleep 2
    done

    echo "Timed out waiting for the smoke-test container."
    exit 1
    ;;
  observability)
    set -euo pipefail
    python3 tools/setup-observability.py
    docker compose --env-file .env --env-file .observability/compose.env -f docker-compose.yml -f docker-compose.observability.yml config --quiet
    docker compose --env-file .env --env-file .observability/compose.env -f docker-compose.yml -f docker-compose.observability.yml up --detach --no-build
    python3 tools/smoke-observability.py
    ;;
  *)
    echo "Unknown container runtime check: $1" >&2
    exit 2
    ;;
esac
