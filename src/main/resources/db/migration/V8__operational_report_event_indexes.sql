-- Reporting aggregates scan bounded event windows by canonical branch before local-day grouping.
CREATE INDEX ix_queue_report_joined ON queue_entries(branch_id, joined_at);
CREATE INDEX ix_queue_report_started ON queue_entries(branch_id, started_at) WHERE started_at IS NOT NULL;
CREATE INDEX ix_queue_report_completed ON queue_entries(branch_id, completed_at) WHERE completed_at IS NOT NULL;
