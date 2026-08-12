#!/usr/bin/env python3
"""Fail when a current OpenAPI operation is absent from the Bruno YAML collection."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

HTTP_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"}
METHOD_RE = re.compile(r"^\s*method:\s*([A-Z]+)\s*$", re.MULTILINE)
URL_RE = re.compile(r'^\s*url:\s*["\']?(.+?)["\']?\s*$', re.MULTILINE)


def normalize_bruno_path(url: str) -> str:
    path = url.split("?", 1)[0].replace("{{baseUrl}}", "")
    if path.startswith("/api/admin/users/") and path.endswith("/role"):
        return re.sub(r"/\{\{[^}]+\}\}/role$", "/{userId}/role", path)
    if path.startswith("/api/notifications/user/"):
        return re.sub(r"/\{\{[^}]+\}\}$", "/{userId}", path)
    if path.startswith("/api/marketplace/businesses/"):
        return re.sub(r"^/api/marketplace/businesses/\{\{[^}]+\}\}",
                      "/api/marketplace/businesses/{businessId}", path)
    if path.startswith("/api/marketplace/branches/") and not path.endswith("/discoverable"):
        return re.sub(r"^/api/marketplace/branches/\{\{[^}]+\}\}",
                      "/api/marketplace/branches/{branchId}", path)
    return re.sub(r"/\{\{[^}]+\}\}", "/{id}", path)


def bruno_operations(collection: Path) -> set[tuple[str, str]]:
    operations: set[tuple[str, str]] = set()
    for request_file in collection.rglob("*.yml"):
        if request_file.name in {"folder.yml", "opencollection.yml"} or "environments" in request_file.parts:
            continue
        text = request_file.read_text(encoding="utf-8")
        method_match = METHOD_RE.search(text)
        url_match = URL_RE.search(text)
        if not method_match or not url_match:
            continue
        method = method_match.group(1)
        if method in HTTP_METHODS:
            operations.add((method, normalize_bruno_path(url_match.group(1))))
    return operations


def openapi_operations(openapi_file: Path) -> set[tuple[str, str]]:
    document = json.loads(openapi_file.read_text(encoding="utf-8"))
    operations: set[tuple[str, str]] = set()
    for path, path_item in document.get("paths", {}).items():
        for method in path_item:
            upper = method.upper()
            if upper in HTTP_METHODS:
                operations.add((upper, path))
    return operations


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("openapi")
    parser.add_argument("--collection", default=".")
    args = parser.parse_args()

    expected = openapi_operations(Path(args.openapi))
    actual = bruno_operations(Path(args.collection).resolve())
    missing = sorted(expected - actual)

    print(f"OpenAPI operations: {len(expected)}")
    print(f"Bruno distinct HTTP operations: {len(actual)}")
    print(f"OpenAPI operations represented in Bruno: {len(expected) - len(missing)}/{len(expected)}")

    if missing:
        print("Missing OpenAPI operations:")
        for method, path in missing:
            print(f"  {method} {path}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
