CREATE TABLE tenant_memberships (
    user_id varchar(64) PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    business_id varchar(64) NOT NULL REFERENCES businesses(business_id) ON DELETE RESTRICT,
    assigned_at timestamp(6) NOT NULL,
    assigned_at_nano_remainder smallint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_tenant_membership_assigned_nanos
        CHECK (assigned_at_nano_remainder BETWEEN 0 AND 999)
);

CREATE INDEX ix_tenant_memberships_business_user
    ON tenant_memberships(business_id, user_id);

-- The reusable service catalogue is platform-owned. Business owners manage only
-- their tenant-owned service_offerings through branch-scoped operations.
DELETE FROM role_permissions
WHERE role_id = 'builtin:BUSINESS_OWNER'
  AND permission = 'SERVICE_MANAGE';

ALTER TABLE branches
    ADD CONSTRAINT uq_branch_tenant_scope UNIQUE(branch_id, business_id);

ALTER TABLE service_offerings
    ADD CONSTRAINT uq_offering_branch_scope UNIQUE(offering_id, branch_id);

ALTER TABLE bookings
    ADD CONSTRAINT uq_booking_branch_offering_scope UNIQUE(booking_id, branch_id, offering_id);

ALTER TABLE notifications
    ADD CONSTRAINT ck_notification_operational_scope
        CHECK (
            (booking_id IS NULL AND branch_id IS NULL AND offering_id IS NULL)
            OR (booking_id IS NOT NULL AND branch_id IS NOT NULL AND offering_id IS NOT NULL)
        ) NOT VALID,
    ADD CONSTRAINT fk_notification_offering_branch
        FOREIGN KEY(offering_id, branch_id)
        REFERENCES service_offerings(offering_id, branch_id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_notification_booking_scope
        FOREIGN KEY(booking_id, branch_id, offering_id)
        REFERENCES bookings(booking_id, branch_id, offering_id)
        ON DELETE RESTRICT NOT VALID;

ALTER TABLE notifications VALIDATE CONSTRAINT ck_notification_operational_scope;
ALTER TABLE notifications VALIDATE CONSTRAINT fk_notification_offering_branch;
ALTER TABLE notifications VALIDATE CONSTRAINT fk_notification_booking_scope;

CREATE INDEX ix_branches_tenant_status
    ON branches(business_id, branch_status, branch_id);
CREATE INDEX ix_offerings_branch_service_status
    ON service_offerings(branch_id, service_id, offering_status, offering_id);
CREATE INDEX ix_bookings_branch_status_time_tenant
    ON bookings(branch_id, booking_status, scheduled_at, booking_id);
CREATE INDEX ix_queue_branch_status_position_tenant
    ON queue_entries(branch_id, queue_status, active_position, queue_entry_id);
CREATE INDEX ix_notifications_branch_sent_tenant
    ON notifications(branch_id, sent_at DESC, notification_id DESC);

-- Deferred final-state validation permits atomic promotion+assignment and
-- removal+demotion while rejecting every newly-created invalid combination.
CREATE FUNCTION enforce_operational_tenant_membership()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    affected_user_id varchar(64);
    assigned_role varchar(32);
    has_membership boolean;
BEGIN
    affected_user_id := COALESCE(NEW.user_id, OLD.user_id);

    SELECT r.role_name INTO assigned_role
    FROM user_role_assignments ura
    JOIN roles r ON r.role_id = ura.role_id
    WHERE ura.user_id = affected_user_id;

    SELECT EXISTS(
        SELECT 1 FROM tenant_memberships tm WHERE tm.user_id = affected_user_id
    ) INTO has_membership;

    IF assigned_role IN ('STAFF', 'BUSINESS_OWNER') AND NOT has_membership THEN
        RAISE EXCEPTION 'operational user requires one tenant membership'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_operational_user_tenant_membership';
    END IF;

    IF assigned_role IS NOT NULL
       AND assigned_role NOT IN ('STAFF', 'BUSINESS_OWNER')
       AND has_membership THEN
        RAISE EXCEPTION 'non-operational user cannot have a tenant membership'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_non_operational_user_no_tenant';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER tenant_membership_role_guard
AFTER INSERT OR UPDATE OR DELETE ON tenant_memberships
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_operational_tenant_membership();

CREATE CONSTRAINT TRIGGER tenant_role_membership_guard
AFTER INSERT OR UPDATE OR DELETE ON user_role_assignments
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_operational_tenant_membership();
