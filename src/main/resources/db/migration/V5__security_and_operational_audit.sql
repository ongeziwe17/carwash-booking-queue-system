CREATE TABLE audit_records (
    audit_id varchar(64) PRIMARY KEY,
    occurred_at timestamptz(6) NOT NULL,
    occurred_at_nano_remainder smallint NOT NULL DEFAULT 0,
    actor_type varchar(16) NOT NULL,
    actor_user_id varchar(64),
    actor_role varchar(32),
    business_id varchar(64),
    action varchar(64) NOT NULL,
    target_type varchar(64) NOT NULL,
    target_id varchar(64),
    outcome varchar(16) NOT NULL,
    reason_code varchar(64),
    correlation_id varchar(128) NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    event_source varchar(16) NOT NULL,
    CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'ANONYMOUS')),
    CONSTRAINT ck_audit_actor_identity CHECK (
        (actor_type = 'USER' AND actor_user_id IS NOT NULL AND actor_role IS NOT NULL)
        OR (actor_type IN ('SYSTEM', 'ANONYMOUS') AND actor_user_id IS NULL AND actor_role IS NULL)
    ),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE')),
    CONSTRAINT ck_audit_outcome_reason CHECK (
        (outcome = 'SUCCESS' AND reason_code IS NULL)
        OR (outcome IN ('DENIED', 'FAILURE') AND reason_code IS NOT NULL)
    ),
    CONSTRAINT ck_audit_source CHECK (event_source IN ('API', 'SECURITY', 'SYSTEM')),
    CONSTRAINT ck_audit_required_text CHECK (
        length(btrim(audit_id)) BETWEEN 1 AND 64
        AND length(btrim(action)) BETWEEN 1 AND 64
        AND length(btrim(target_type)) BETWEEN 1 AND 64
        AND length(btrim(correlation_id)) BETWEEN 1 AND 128
    ),
    CONSTRAINT ck_audit_optional_text CHECK (
        (actor_user_id IS NULL OR length(btrim(actor_user_id)) BETWEEN 1 AND 64)
        AND (actor_role IS NULL OR length(btrim(actor_role)) BETWEEN 1 AND 32)
        AND (business_id IS NULL OR length(btrim(business_id)) BETWEEN 1 AND 64)
        AND (target_id IS NULL OR length(btrim(target_id)) BETWEEN 1 AND 64)
        AND (reason_code IS NULL OR reason_code ~ '^[A-Z0-9_]{1,64}$')
    ),
    CONSTRAINT ck_audit_metadata_object CHECK (
        jsonb_typeof(metadata) = 'object'
        AND octet_length(metadata::text) <= 32768
        AND metadata - ARRAY[
            'httpMethod', 'route', 'scope', 'previousState', 'newState', 'operation', 'category'
        ]::text[] = '{}'::jsonb
    ),
    CONSTRAINT ck_audit_occurred_nanos CHECK (occurred_at_nano_remainder BETWEEN 0 AND 999)
);

CREATE INDEX ix_audit_tenant_date ON audit_records(
    business_id, occurred_at DESC, occurred_at_nano_remainder DESC, audit_id DESC
);
CREATE INDEX ix_audit_actor_date ON audit_records(
    actor_user_id, occurred_at DESC, occurred_at_nano_remainder DESC, audit_id DESC
);
CREATE INDEX ix_audit_action_date ON audit_records(
    action, occurred_at DESC, occurred_at_nano_remainder DESC, audit_id DESC
);
CREATE INDEX ix_audit_resource_date ON audit_records(
    target_type, target_id, occurred_at DESC, occurred_at_nano_remainder DESC, audit_id DESC
);

INSERT INTO role_permissions(role_id, permission) VALUES
    ('builtin:BUSINESS_OWNER', 'AUDIT_READ'),
    ('builtin:PLATFORM_ADMIN', 'AUDIT_READ')
ON CONFLICT DO NOTHING;
