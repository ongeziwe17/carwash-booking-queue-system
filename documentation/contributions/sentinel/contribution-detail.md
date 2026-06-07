# Contribution Overview: Docker Compose Support for SentinelPay

## Repository: https://github.com/Teboho66/SentinelPay

## Pull Request: https://github.com/Teboho66/SentinelPay/pull/34

This contribution added Docker Compose support to the SentinelPay project to improve the local development experience for contributors. The goal was to make it easier to run the FastAPI application together with its supporting services without requiring contributors to manually install and configure PostgreSQL and Redis.

The contribution also updated the CI workflow to validate the Docker Compose configuration, helping ensure that future changes do not accidentally break the local development environment.

## Repository Contributed To

| Item | Details |
|---|---|
| Project | SentinelPay |
| Contribution Type | Docker Compose / DevOps / CI Improvement |
| Main Feature | Local full-stack development environment |
| Assignment | Assignment 15: Cross-Project Contributions & Collaborative Development |

## Problem Identified

Before this contribution, contributors needed to understand and configure multiple supporting services manually before running the project locally. This created a higher onboarding barrier, especially for contributors who wanted to test the API with PostgreSQL and Redis available.

A missing or unclear local environment setup can slow down development and make it harder for new contributors to verify changes before opening a pull request.

## Solution Implemented

A root-level `docker-compose.yml` file was added to define a local development stack for SentinelPay. The stack includes:

| Service | Image / Runtime | Purpose |
|---|---|---|
| API | SentinelPay application image | Runs the FastAPI application |
| PostgreSQL | `postgres:16-alpine` | Local relational database |
| Redis | `redis:7-alpine` | Local cache/message broker dependency |

The API service was configured to wait for PostgreSQL and Redis health checks before starting. This helps avoid startup failures caused by the API attempting to connect before supporting services are ready.

## Changes Made

- Added a root-level `docker-compose.yml` for local development.
- Added PostgreSQL service using `postgres:16-alpine`.
- Added Redis service using `redis:7-alpine`.
- Added health checks for the API, PostgreSQL, and Redis services.
- Updated the API service dependency configuration so it waits for PostgreSQL and Redis to become healthy.
- Moved database and Redis configuration into environment variables.
- Updated Dockerfile `COPY` commands to safely handle assignment folder names with spaces.
- Updated the README with Docker Compose setup instructions.
- Added a GitHub Actions CI job to validate the Compose file using:

```bash
docker compose -f docker-compose.yml config