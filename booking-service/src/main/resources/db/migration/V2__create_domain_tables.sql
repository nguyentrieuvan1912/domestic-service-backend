CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_code VARCHAR(60) NOT NULL UNIQUE,
    booking_mode VARCHAR(10) NOT NULL CHECK (booking_mode IN ('MODE_A','MODE_B')),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN
      ('PENDING','MATCHING','CONFIRMED','STAFF_ASSIGNED','ACCEPTED','IN_PROGRESS','COMPLETED',
       'CANCELLED','REFUNDING','REFUNDED','NO_STAFF_FOUND','ABSENT','REJECTED')),
    customer_id UUID NOT NULL,
    service_id UUID NOT NULL,
    package_id UUID,
    address_id UUID NOT NULL,
    quote_id UUID,
    quote_version BIGINT,
    service_snapshot JSONB NOT NULL,
    address_snapshot JSONB NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    required_staff_count INTEGER NOT NULL DEFAULT 1 CHECK (required_staff_count > 0),
    base_amount BIGINT NOT NULL CHECK (base_amount >= 0),
    add_on_amount BIGINT NOT NULL DEFAULT 0 CHECK (add_on_amount >= 0),
    discount_amount BIGINT NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    total_amount BIGINT NOT NULL CHECK (total_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    promotion_id UUID,
    promotion_code VARCHAR(60),
    payment_method VARCHAR(30) NOT NULL CHECK (payment_method IN ('BANK_TRANSFER_QR','CASH')),
    payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (payment_status IN ('PENDING','PROCESSING','PAID','FAILED','REFUNDED','CANCELLED')),
    notes TEXT,
    cancel_reason TEXT,
    cancelled_by UUID,
    cancelled_by_type VARCHAR(20) CHECK (cancelled_by_type IN ('CUSTOMER','STAFF','ADMIN','SYSTEM')),
    cancelled_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (ends_at > starts_at),
    CHECK (total_amount = base_amount + add_on_amount - discount_amount)
);
CREATE INDEX ix_bookings_customer ON bookings(customer_id, created_at DESC);
CREATE INDEX ix_bookings_status_start ON bookings(status, starts_at);
CREATE TABLE booking_add_ons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    add_on_id UUID NOT NULL,
    name_snapshot VARCHAR(160) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
    extra_duration_minutes INTEGER NOT NULL DEFAULT 0 CHECK (extra_duration_minutes >= 0),
    total_price BIGINT NOT NULL CHECK (total_price = quantity * unit_price),
    UNIQUE(booking_id, add_on_id)
);
CREATE TABLE booking_requirement_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    requirement_id UUID NOT NULL,
    field_key VARCHAR(80) NOT NULL,
    label_snapshot VARCHAR(160) NOT NULL,
    answer_value JSONB NOT NULL,
    UNIQUE(booking_id, requirement_id)
);
CREATE TABLE booking_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    staff_id UUID NOT NULL,
    matching_score NUMERIC(8,4),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ASSIGNED','ACCEPTED','REJECTED','CANCELLED','COMPLETED')),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    cancellation_reason TEXT
);
CREATE UNIQUE INDEX uq_booking_active_staff ON booking_assignments(booking_id, staff_id)
    WHERE status IN ('ASSIGNED','ACCEPTED','COMPLETED');
CREATE INDEX ix_assignments_staff ON booking_assignments(staff_id, assigned_at DESC);
CREATE TABLE booking_status_histories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_by UUID,
    changed_by_type VARCHAR(20) NOT NULL CHECK (changed_by_type IN ('CUSTOMER','STAFF','ADMIN','SYSTEM')),
    reason TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    customer_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    service_id UUID NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    tags JSONB NOT NULL DEFAULT '[]',
    images JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(booking_id, staff_id)
);
CREATE TABLE staff_areas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL,
    province VARCHAR(100) NOT NULL,
    district VARCHAR(100),
    ward VARCHAR(100),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_staff_areas_staff ON staff_areas(staff_id);
CREATE TABLE staff_availabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL,
    available_date DATE,
    day_of_week SMALLINT CHECK (day_of_week BETWEEN 0 AND 6),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','UNAVAILABLE')),
    CHECK ((available_date IS NULL) <> (day_of_week IS NULL)),
    CHECK (end_time > start_time)
);
CREATE TABLE staff_service_capabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL,
    service_id UUID NOT NULL,
    skill_level VARCHAR(30) NOT NULL CHECK (skill_level IN ('BEGINNER','INTERMEDIATE','ADVANCED','EXPERT')),
    experience_years NUMERIC(4,1) NOT NULL DEFAULT 0 CHECK (experience_years >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','INACTIVE')),
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    verified_at TIMESTAMPTZ,
    verified_by UUID,
    note TEXT,
    UNIQUE(staff_id, service_id)
);
CREATE TABLE staff_restrictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL,
    restriction_type VARCHAR(30) NOT NULL CHECK (restriction_type IN ('WARNING','SERVICE_LIMIT','SUSPENSION')),
    service_id UUID,
    reason TEXT NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    customer_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    UNIQUE(booking_id, staff_id)
);
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    sender_id UUID,
    sender_type VARCHAR(20) NOT NULL CHECK (sender_type IN ('CUSTOMER','STAFF','SYSTEM')),
    message_type VARCHAR(20) NOT NULL CHECK (message_type IN ('TEXT','IMAGE','LOCATION')),
    content TEXT NOT NULL,
    media_url TEXT,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ
);
CREATE INDEX ix_messages_conversation ON messages(conversation_id, sent_at);
CREATE TABLE staff_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL,
    booking_id UUID REFERENCES bookings(id),
    hold_token UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL CHECK (status IN ('HELD','CONFIRMED','IN_PROGRESS','COMPLETED','RELEASED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at),
    CHECK (status <> 'HELD' OR expires_at IS NOT NULL),
    EXCLUDE USING gist (staff_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
        WHERE (status IN ('HELD','CONFIRMED','IN_PROGRESS'))
);
-- Expired HELD rows must be released by application/job; time does not automatically change status.
CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL,
    operation VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INTEGER CHECK (response_status BETWEEN 100 AND 599),
    response_body JSONB,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING','COMPLETED','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE(actor_id, operation, idempotency_key),
    CHECK (expires_at > created_at)
);
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox_events(created_at) WHERE status IN ('PENDING','FAILED');
