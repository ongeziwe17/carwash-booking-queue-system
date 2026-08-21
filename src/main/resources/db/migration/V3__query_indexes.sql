CREATE INDEX ix_users_status ON users(account_status, user_id);
CREATE INDEX ix_schedules_updated ON branch_operating_schedules(updated_at, branch_id);
CREATE INDEX ix_bookings_status_branch_time
    ON bookings(booking_status, branch_id, scheduled_at, booking_id);
CREATE INDEX ix_queue_status_branch_order
    ON queue_entries(queue_status, branch_id, active_position, queue_entry_id);
