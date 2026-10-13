-- Original reviews are retained even when resources become inactive. No cascading deletes.
CREATE TABLE feedback_reviews (
    review_id varchar(64) PRIMARY KEY,
    booking_id varchar(64) NOT NULL UNIQUE,
    customer_id varchar(64) NOT NULL,
    business_id varchar(64) NOT NULL,
    branch_id varchar(64) NOT NULL,
    offering_id varchar(64) NOT NULL,
    service_id varchar(64) NOT NULL,
    rating smallint NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment varchar(1000),
    created_at timestamptz NOT NULL,
    publication_state varchar(16) NOT NULL CHECK (publication_state IN ('PUBLISHED','HIDDEN')),
    moderation_reason varchar(500),
    moderator_id varchar(64) REFERENCES users(user_id) ON DELETE RESTRICT,
    moderated_at timestamptz,
    CONSTRAINT uq_feedback_review_business UNIQUE(review_id, business_id),
    CONSTRAINT fk_feedback_booking FOREIGN KEY (booking_id, customer_id, branch_id, offering_id, service_id)
        REFERENCES bookings(booking_id, user_id, branch_id, offering_id, service_id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_business FOREIGN KEY (branch_id, business_id)
        REFERENCES branches(branch_id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_feedback_moderation CHECK
        ((moderation_reason IS NULL AND moderator_id IS NULL AND moderated_at IS NULL)
         OR (moderation_reason IS NOT NULL AND moderator_id IS NOT NULL AND moderated_at IS NOT NULL))
);
CREATE INDEX ix_feedback_tenant_page ON feedback_reviews(business_id, created_at DESC, review_id);
CREATE INDEX ix_feedback_customer ON feedback_reviews(customer_id, created_at DESC);
CREATE INDEX ix_feedback_branch_rating ON feedback_reviews(branch_id, publication_state, rating);
CREATE INDEX ix_feedback_offering_rating ON feedback_reviews(offering_id, publication_state, rating);

CREATE TABLE feedback_reports (
    report_id varchar(64) PRIMARY KEY,
    review_id varchar(64) NOT NULL,
    business_id varchar(64) NOT NULL,
    actor_id varchar(64) NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    reason varchar(500) NOT NULL,
    created_at timestamptz NOT NULL,
    report_state varchar(16) NOT NULL CHECK (report_state IN ('OPEN','RESOLVED')),
    resolution_reason varchar(500),
    resolved_by varchar(64) REFERENCES users(user_id) ON DELETE RESTRICT,
    resolved_at timestamptz,
    CONSTRAINT uq_feedback_report_actor UNIQUE(review_id, actor_id),
    CONSTRAINT fk_feedback_report_tenant FOREIGN KEY (review_id, business_id)
        REFERENCES feedback_reviews(review_id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_feedback_resolution CHECK
        ((report_state = 'OPEN' AND resolution_reason IS NULL AND resolved_by IS NULL AND resolved_at IS NULL)
         OR (report_state = 'RESOLVED' AND resolution_reason IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL))
);
CREATE INDEX ix_feedback_report_queue ON feedback_reports(business_id, report_state, created_at DESC, report_id);
