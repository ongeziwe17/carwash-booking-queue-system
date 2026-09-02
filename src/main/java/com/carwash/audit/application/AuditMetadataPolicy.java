package com.carwash.audit.application;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.nio.charset.StandardCharsets;

/** Deterministic allowlist and bounds for optional audit metadata. */
public final class AuditMetadataPolicy {
    private static final Set<String> ALLOWED = Set.of(
            "httpMethod", "route", "scope", "previousState", "newState", "operation", "category"
    );
    private static final Set<String> SUSPICIOUS = Set.of(
            "password", "token", "authorization", "cookie", "secret", "credential",
            "email", "phone", "address", "note", "request", "message", "body"
    );
    private final AuditProperties properties;

    public AuditMetadataPolicy(AuditProperties properties) { this.properties = properties; }

    public Map<String, String> sanitize(Map<String, String> supplied) {
        if (supplied == null || supplied.isEmpty()) return Map.of();
        if (supplied.size() > properties.metadataMaximumKeys()) {
            throw new IllegalArgumentException("Audit metadata contains too many keys");
        }
        TreeMap<String, String> result = new TreeMap<>();
        supplied.forEach((rawKey, rawValue) -> {
            String key = requireBounded(rawKey, properties.metadataMaximumKeyLength(), "metadata key");
            String lower = key.toLowerCase(java.util.Locale.ROOT);
            if (!ALLOWED.contains(key) || SUSPICIOUS.stream().anyMatch(lower::contains)) {
                throw new IllegalArgumentException("Audit metadata key is not allowed");
            }
            String value = requireBounded(rawValue, properties.metadataMaximumValueLength(), "metadata value");
            String lowerValue = value.toLowerCase(java.util.Locale.ROOT);
            if (lowerValue.contains("bearer ") || lowerValue.contains("password=")
                    || lowerValue.contains("token=") || lowerValue.contains("authorization:")
                    || lowerValue.contains("cookie=") || lowerValue.contains("secret=")
                    || looksLikeJwt(value)) {
                throw new IllegalArgumentException("Audit metadata value contains prohibited credential material");
            }
            result.put(key, value);
        });
        if (serializedSize(result) > properties.metadataMaximumTotalSize()) {
            throw new IllegalArgumentException("Audit metadata exceeds the total size limit");
        }
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(result));
    }

    private static boolean looksLikeJwt(String value) {
        return value.matches("[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");
    }

    private static int serializedSize(Map<String, String> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            appendJsonString(json, entry.getKey());
            json.append(':');
            appendJsonString(json, entry.getValue());
        }
        return json.append('}').toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static void appendJsonString(StringBuilder target, String value) {
        target.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') target.append('\\');
            target.append(character);
        }
        target.append('"');
    }

    private static String requireBounded(String value, int maximum, String name) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("Audit " + name + " is invalid");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Audit " + name + " contains control characters");
        }
        return value;
    }
}
