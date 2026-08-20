CREATE TABLE roles (
    role_id varchar(64) PRIMARY KEY,
    role_name varchar(32) NOT NULL UNIQUE,
    description varchar(255) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_roles_name CHECK (role_name IN ('CUSTOMER', 'STAFF', 'BUSINESS_OWNER', 'PLATFORM_ADMIN'))
);

CREATE TABLE role_permissions (
    role_id varchar(64) NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission varchar(64) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

INSERT INTO roles(role_id, role_name, description) VALUES
    ('builtin:CUSTOMER', 'CUSTOMER', 'Built-in customer'),
    ('builtin:STAFF', 'STAFF', 'Built-in staff'),
    ('builtin:BUSINESS_OWNER', 'BUSINESS_OWNER', 'Built-in business_owner'),
    ('builtin:PLATFORM_ADMIN', 'PLATFORM_ADMIN', 'Built-in platform_admin');

INSERT INTO role_permissions(role_id, permission)
SELECT 'builtin:PLATFORM_ADMIN', p.permission
FROM unnest(ARRAY[
    'USER_SELF_MANAGE','USER_ADMIN','VEHICLE_SELF_MANAGE','VEHICLE_OPERATE','SERVICE_READ',
    'SERVICE_MANAGE','BOOKING_SELF_MANAGE','BOOKING_OPERATE','QUEUE_SELF_READ','QUEUE_OPERATE',
    'NOTIFICATION_SELF_READ','REPORT_READ','MARKETPLACE_READ','MARKETPLACE_MANAGE','ROLE_ASSIGN'
]) AS p(permission);

INSERT INTO role_permissions(role_id, permission) VALUES
    ('builtin:CUSTOMER','USER_SELF_MANAGE'),('builtin:CUSTOMER','VEHICLE_SELF_MANAGE'),
    ('builtin:CUSTOMER','SERVICE_READ'),('builtin:CUSTOMER','BOOKING_SELF_MANAGE'),
    ('builtin:CUSTOMER','QUEUE_SELF_READ'),('builtin:CUSTOMER','NOTIFICATION_SELF_READ'),
    ('builtin:CUSTOMER','MARKETPLACE_READ'),
    ('builtin:STAFF','USER_SELF_MANAGE'),('builtin:STAFF','VEHICLE_SELF_MANAGE'),
    ('builtin:STAFF','VEHICLE_OPERATE'),('builtin:STAFF','SERVICE_READ'),
    ('builtin:STAFF','BOOKING_SELF_MANAGE'),('builtin:STAFF','BOOKING_OPERATE'),
    ('builtin:STAFF','QUEUE_SELF_READ'),('builtin:STAFF','QUEUE_OPERATE'),
    ('builtin:STAFF','NOTIFICATION_SELF_READ'),('builtin:STAFF','MARKETPLACE_READ'),
    ('builtin:BUSINESS_OWNER','USER_SELF_MANAGE'),('builtin:BUSINESS_OWNER','VEHICLE_SELF_MANAGE'),
    ('builtin:BUSINESS_OWNER','VEHICLE_OPERATE'),('builtin:BUSINESS_OWNER','SERVICE_READ'),
    ('builtin:BUSINESS_OWNER','SERVICE_MANAGE'),('builtin:BUSINESS_OWNER','BOOKING_SELF_MANAGE'),
    ('builtin:BUSINESS_OWNER','BOOKING_OPERATE'),('builtin:BUSINESS_OWNER','QUEUE_SELF_READ'),
    ('builtin:BUSINESS_OWNER','QUEUE_OPERATE'),('builtin:BUSINESS_OWNER','NOTIFICATION_SELF_READ'),
    ('builtin:BUSINESS_OWNER','REPORT_READ'),('builtin:BUSINESS_OWNER','MARKETPLACE_READ'),
    ('builtin:BUSINESS_OWNER','MARKETPLACE_MANAGE');

CREATE TABLE users (
    user_id varchar(64) PRIMARY KEY,
    full_name varchar(160) NOT NULL,
    email varchar(320) NOT NULL,
    phone varchar(32) NOT NULL,
    account_status varchar(24) NOT NULL,
    created_at timestamp(6) NOT NULL,
    created_at_nano_remainder smallint NOT NULL DEFAULT 0,
    last_login_at timestamp(6),
    last_login_at_nano_remainder smallint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_status CHECK (account_status IN ('PENDING','ACTIVE','SUSPENDED','DEACTIVATED')),
    CONSTRAINT ck_users_created_nanos CHECK (created_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_users_login_nanos CHECK (last_login_at_nano_remainder BETWEEN 0 AND 999)
);
CREATE UNIQUE INDEX uq_users_email_ci ON users (lower(email));

CREATE TABLE user_credentials (
    user_id varchar(64) PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    encoded_password varchar(255) NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE user_role_assignments (
    user_id varchar(64) PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    role_id varchar(64) NOT NULL REFERENCES roles(role_id) ON DELETE RESTRICT,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_user_roles_role ON user_role_assignments(role_id);

CREATE TABLE businesses (
    business_id varchar(64) PRIMARY KEY,
    business_name varchar(160) NOT NULL,
    contact_email varchar(320) NOT NULL,
    contact_phone varchar(32) NOT NULL,
    registration_number varchar(64),
    business_status varchar(16) NOT NULL,
    registered_at timestamp(6) NOT NULL,
    registered_at_nano_remainder smallint NOT NULL DEFAULT 0,
    updated_at timestamp(6) NOT NULL,
    updated_at_nano_remainder smallint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_business_status CHECK (business_status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_business_registered_nanos CHECK (registered_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_business_updated_nanos CHECK (updated_at_nano_remainder BETWEEN 0 AND 999)
);
CREATE UNIQUE INDEX uq_business_registration_ci ON businesses(lower(registration_number));

CREATE TABLE branches (
    branch_id varchar(64) PRIMARY KEY,
    business_id varchar(64) NOT NULL REFERENCES businesses(business_id) ON DELETE RESTRICT,
    branch_name varchar(160) NOT NULL,
    address_line1 varchar(255) NOT NULL,
    address_line2 varchar(255),
    city varchar(120) NOT NULL,
    province varchar(120) NOT NULL,
    postal_code varchar(32) NOT NULL,
    country_code varchar(2) NOT NULL,
    latitude numeric(11,8) NOT NULL,
    longitude numeric(11,8) NOT NULL,
    timezone varchar(64) NOT NULL,
    branch_status varchar(16) NOT NULL,
    public_discovery_enabled boolean NOT NULL,
    created_at timestamp(6) NOT NULL,
    created_at_nano_remainder smallint NOT NULL DEFAULT 0,
    updated_at timestamp(6) NOT NULL,
    updated_at_nano_remainder smallint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_branch_status CHECK (branch_status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_branch_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_branch_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_branch_created_nanos CHECK (created_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_branch_updated_nanos CHECK (updated_at_nano_remainder BETWEEN 0 AND 999)
);
CREATE INDEX ix_branches_business ON branches(business_id, branch_id);
CREATE INDEX ix_branches_discovery ON branches(public_discovery_enabled, branch_status, branch_id);

CREATE TABLE service_definitions (
    service_id varchar(64) PRIMARY KEY,
    service_name varchar(160) NOT NULL,
    description varchar(1000),
    legacy_price numeric(12,2) NOT NULL,
    legacy_duration_min integer NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) NOT NULL,
    created_at_nano_remainder smallint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_service_price CHECK (legacy_price >= 0),
    CONSTRAINT ck_service_duration CHECK (legacy_duration_min > 0),
    CONSTRAINT ck_service_created_nanos CHECK (created_at_nano_remainder BETWEEN 0 AND 999)
);

CREATE TABLE service_offerings (
    offering_id varchar(64) PRIMARY KEY,
    branch_id varchar(64) NOT NULL REFERENCES branches(branch_id) ON DELETE RESTRICT,
    service_id varchar(64) NOT NULL REFERENCES service_definitions(service_id) ON DELETE RESTRICT,
    price numeric(12,2) NOT NULL,
    estimated_duration_min integer NOT NULL,
    concurrent_capacity integer NOT NULL,
    offering_status varchar(16) NOT NULL,
    created_at timestamp(6) NOT NULL,
    created_at_nano_remainder smallint NOT NULL DEFAULT 0,
    updated_at timestamp(6) NOT NULL,
    updated_at_nano_remainder smallint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_offering_branch_service UNIQUE(branch_id, service_id),
    CONSTRAINT uq_offering_scope UNIQUE(offering_id, branch_id, service_id),
    CONSTRAINT ck_offering_price CHECK (price >= 0),
    CONSTRAINT ck_offering_duration CHECK (estimated_duration_min BETWEEN 1 AND 1440),
    CONSTRAINT ck_offering_capacity CHECK (concurrent_capacity BETWEEN 1 AND 1000),
    CONSTRAINT ck_offering_status CHECK (offering_status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_offering_created_nanos CHECK (created_at_nano_remainder BETWEEN 0 AND 999),
    CONSTRAINT ck_offering_updated_nanos CHECK (updated_at_nano_remainder BETWEEN 0 AND 999)
);
CREATE INDEX ix_offerings_branch_status ON service_offerings(branch_id, offering_status, offering_id);
CREATE INDEX ix_offerings_service ON service_offerings(service_id, offering_id);

CREATE TABLE vehicles (
    vehicle_id varchar(64) PRIMARY KEY,
    user_id varchar(64) NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    plate_number varchar(32) NOT NULL,
    vehicle_type varchar(64) NOT NULL,
    brand varchar(80),
    model varchar(80),
    color varchar(48),
    notes varchar(1000),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_vehicle_owner_scope UNIQUE(vehicle_id, user_id)
);
CREATE UNIQUE INDEX uq_vehicle_owner_plate_ci ON vehicles(user_id, lower(plate_number));
CREATE INDEX ix_vehicles_user ON vehicles(user_id, vehicle_id);
