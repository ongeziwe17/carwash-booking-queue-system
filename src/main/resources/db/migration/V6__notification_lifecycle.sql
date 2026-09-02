-- NOTIFY-001: read-state consistency and exact-nanosecond keyset inbox access.
-- Historical migrations V1-V5 remain immutable.

ALTER TABLE notifications
    ADD CONSTRAINT ck_notification_read_state
        CHECK (
            (delivery_status = 'READ' AND read_at IS NOT NULL)
            OR
            (delivery_status <> 'READ' AND read_at IS NULL AND read_at_nano_remainder = 0)
        ) NOT VALID;

ALTER TABLE notifications VALIDATE CONSTRAINT ck_notification_read_state;

CREATE INDEX ix_notifications_user_cursor
    ON notifications(user_id, sent_at DESC, sent_at_nano_remainder DESC, notification_id DESC);

CREATE INDEX ix_notifications_user_unread_cursor
    ON notifications(user_id, sent_at DESC, sent_at_nano_remainder DESC, notification_id DESC)
    WHERE delivery_status = 'SENT';

CREATE INDEX ix_notifications_branch_user_cursor
    ON notifications(branch_id, user_id, sent_at DESC, sent_at_nano_remainder DESC, notification_id DESC);
