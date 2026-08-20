CREATE TABLE branch_operating_schedules (
    branch_id varchar(64) PRIMARY KEY REFERENCES branches(branch_id) ON DELETE RESTRICT,
    created_at timestamp(6) NOT NULL,
    created_at_nano_remainder smallint NOT NULL DEFAULT 0,
    updated_at timestamp(6) NOT NULL,
    updated_at_nano_remainder smallint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_schedule_created_nanos CHECK (created_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_schedule_updated_nanos CHECK (updated_at_nano_remainder BETWEEN 0 AND 999)
);

CREATE TABLE weekly_operating_intervals (
    branch_id varchar(64) NOT NULL REFERENCES branch_operating_schedules(branch_id) ON DELETE CASCADE,
    interval_order integer NOT NULL,
    day_of_week smallint NOT NULL,
    opens_nano_of_day bigint NOT NULL,
    closes_nano_of_day bigint NOT NULL,
    PRIMARY KEY(branch_id, interval_order),
    CONSTRAINT ck_interval_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_interval_open_time CHECK (opens_nano_of_day BETWEEN 0 AND 86399999999999),
    CONSTRAINT ck_interval_close_time CHECK (closes_nano_of_day BETWEEN 0 AND 86399999999999),
    CONSTRAINT ck_interval_distinct_times CHECK (opens_nano_of_day <> closes_nano_of_day)
);

CREATE TABLE temporary_branch_closures (
    closure_id varchar(64) PRIMARY KEY,
    branch_id varchar(64) NOT NULL REFERENCES branches(branch_id) ON DELETE RESTRICT,
    start_epoch_second bigint NOT NULL,
    start_nano integer NOT NULL,
    end_epoch_second bigint NOT NULL,
    end_nano integer NOT NULL,
    reason varchar(1000) NOT NULL,
    closure_status varchar(16) NOT NULL,
    created_at timestamp(6) NOT NULL,
    created_at_nano_remainder smallint NOT NULL DEFAULT 0,
    updated_at timestamp(6) NOT NULL,
    updated_at_nano_remainder smallint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_closure_status CHECK (closure_status IN ('ACTIVE','CANCELLED')),
    CONSTRAINT ck_closure_start_nano CHECK (start_nano BETWEEN 0 AND 999999999),
    CONSTRAINT ck_closure_end_nano CHECK (end_nano BETWEEN 0 AND 999999999),
    CONSTRAINT ck_closure_range CHECK ((end_epoch_second, end_nano) > (start_epoch_second, start_nano)),
    CONSTRAINT ck_closure_created_nanos CHECK (created_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_closure_updated_nanos CHECK (updated_at_nano_remainder BETWEEN 0 AND 999)
);
CREATE INDEX ix_closures_branch_status_time ON temporary_branch_closures(
    branch_id, closure_status, start_epoch_second, end_epoch_second, closure_id);

CREATE TABLE bookings (
    booking_id varchar(64) PRIMARY KEY,
    user_id varchar(64) NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    vehicle_id varchar(64) NOT NULL,
    branch_id varchar(64) NOT NULL,
    offering_id varchar(64) NOT NULL,
    service_id varchar(64) NOT NULL REFERENCES service_definitions(service_id) ON DELETE RESTRICT,
    scheduled_at timestamp(6) NOT NULL,
    scheduled_at_nano_remainder smallint NOT NULL DEFAULT 0,
    booking_status varchar(24) NOT NULL,
    created_at timestamp(6) NOT NULL,
    created_at_nano_remainder smallint NOT NULL DEFAULT 0,
    special_request varchar(2000),
    queue_entry_id varchar(64),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_booking_status CHECK (booking_status IN ('CREATED','CONFIRMED','CANCELLED','IN_SERVICE','COMPLETED')),
    CONSTRAINT ck_booking_scheduled_nanos CHECK (scheduled_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_booking_created_nanos CHECK (created_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT fk_booking_vehicle_owner FOREIGN KEY(vehicle_id, user_id)
        REFERENCES vehicles(vehicle_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_offering_scope FOREIGN KEY(offering_id, branch_id, service_id)
        REFERENCES service_offerings(offering_id, branch_id, service_id) ON DELETE RESTRICT,
    CONSTRAINT uq_booking_owner UNIQUE(booking_id, user_id),
    CONSTRAINT uq_booking_scope UNIQUE(booking_id, user_id, branch_id, offering_id, service_id)
);
CREATE INDEX ix_bookings_user_time ON bookings(user_id, scheduled_at, booking_id);
CREATE INDEX ix_bookings_vehicle_time ON bookings(vehicle_id, scheduled_at, booking_id);
CREATE INDEX ix_bookings_offering_time_status ON bookings(offering_id, scheduled_at, booking_status, booking_id);
CREATE INDEX ix_bookings_branch_time ON bookings(branch_id, scheduled_at, booking_id);

CREATE TABLE queue_entries (
    queue_entry_id varchar(64) PRIMARY KEY,
    booking_id varchar(64) NOT NULL,
    user_id varchar(64) NOT NULL,
    branch_id varchar(64) NOT NULL,
    offering_id varchar(64) NOT NULL,
    service_id varchar(64) NOT NULL,
    position integer NOT NULL,
    active_position integer,
    queue_status varchar(24) NOT NULL,
    joined_at timestamp(6) NOT NULL,
    joined_at_nano_remainder smallint NOT NULL DEFAULT 0,
    called_at timestamp(6),
    called_at_nano_remainder smallint NOT NULL DEFAULT 0,
    started_at timestamp(6),
    started_at_nano_remainder smallint NOT NULL DEFAULT 0,
    completed_at timestamp(6),
    completed_at_nano_remainder smallint NOT NULL DEFAULT 0,
    estimated_wait_min integer NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_queue_booking_scope FOREIGN KEY(booking_id, user_id, branch_id, offering_id, service_id)
        REFERENCES bookings(booking_id, user_id, branch_id, offering_id, service_id) ON DELETE RESTRICT,
    CONSTRAINT ck_queue_status CHECK (queue_status IN ('WAITING','CALLED','IN_PROGRESS','COMPLETED','EXITED')),
    CONSTRAINT ck_queue_position CHECK (position > 0),
    CONSTRAINT ck_queue_active_position CHECK (
        (queue_status IN ('WAITING','CALLED','IN_PROGRESS') AND active_position = position)
        OR (queue_status IN ('COMPLETED','EXITED') AND active_position IS NULL)),
    CONSTRAINT ck_queue_wait CHECK (estimated_wait_min >= 0),
    CONSTRAINT ck_queue_joined_nanos CHECK (joined_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_queue_called_nanos CHECK (called_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_queue_started_nanos CHECK (started_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_queue_completed_nanos CHECK (completed_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT uq_queue_branch_active_position UNIQUE(branch_id, active_position) DEFERRABLE INITIALLY DEFERRED
);
ALTER TABLE bookings ADD CONSTRAINT fk_booking_queue_entry
    FOREIGN KEY(queue_entry_id) REFERENCES queue_entries(queue_entry_id)
    ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED;
CREATE UNIQUE INDEX uq_queue_active_booking ON queue_entries(booking_id)
    WHERE queue_status IN ('WAITING','CALLED','IN_PROGRESS');
CREATE INDEX ix_queue_branch_order ON queue_entries(branch_id, active_position, joined_at, queue_entry_id);
CREATE INDEX ix_queue_service ON queue_entries(service_id, queue_entry_id);
CREATE INDEX ix_queue_offering ON queue_entries(offering_id, queue_entry_id);

CREATE TABLE notifications (
    notification_id varchar(128) PRIMARY KEY,
    user_id varchar(64) NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    booking_id varchar(64),
    branch_id varchar(64) REFERENCES branches(branch_id) ON DELETE RESTRICT,
    offering_id varchar(64) REFERENCES service_offerings(offering_id) ON DELETE RESTRICT,
    notification_type varchar(64) NOT NULL,
    message varchar(4000) NOT NULL,
    channel varchar(32) NOT NULL,
    sent_at timestamp(6),
    sent_at_nano_remainder smallint NOT NULL DEFAULT 0,
    read_at timestamp(6),
    read_at_nano_remainder smallint NOT NULL DEFAULT 0,
    delivery_status varchar(16) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_notification_booking_owner FOREIGN KEY(booking_id, user_id)
        REFERENCES bookings(booking_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_notification_status CHECK (delivery_status IN ('PENDING','SENT','FAILED','READ')),
    CONSTRAINT ck_notification_sent_nanos CHECK (sent_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_notification_read_nanos CHECK (read_at_nano_remainder BETWEEN 0 AND 999)
);
CREATE INDEX ix_notifications_user_sent ON notifications(user_id, sent_at DESC, notification_id DESC);
CREATE INDEX ix_notifications_booking ON notifications(booking_id, notification_id);
