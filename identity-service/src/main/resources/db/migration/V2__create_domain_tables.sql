-- ClassDiageamV3 + technical identity support. IDs are UUID; external IDs have no cross-DB FK.
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_name VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    phone VARCHAR(30) NOT NULL UNIQUE,
    email VARCHAR(254),
    avatar_url TEXT,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','STAFF','ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','BLOCKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email)) WHERE email IS NOT NULL;
CREATE TABLE admins (
    id UUID PRIMARY KEY REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE customers (
    id UUID PRIMARY KEY REFERENCES users(id),
    date_of_birth DATE,
    gender VARCHAR(10) CHECK (gender IN ('MALE','FEMALE','OTHER')),
    total_bookings INTEGER NOT NULL DEFAULT 0 CHECK (total_bookings >= 0),
    reward_points BIGINT NOT NULL DEFAULT 0 CHECK (reward_points >= 0)
);
CREATE TABLE staffs (
    id UUID PRIMARY KEY REFERENCES users(id),
    date_of_birth DATE,
    gender VARCHAR(10) CHECK (gender IN ('MALE','FEMALE','OTHER')),
    identity_number VARCHAR(30) UNIQUE,
    profile_description TEXT,
    average_rating NUMERIC(3,2) NOT NULL DEFAULT 0 CHECK (average_rating BETWEEN 0 AND 5),
    total_reviews INTEGER NOT NULL DEFAULT 0 CHECK (total_reviews >= 0),
    experience_years NUMERIC(4,1) NOT NULL DEFAULT 0 CHECK (experience_years >= 0),
    completion_rate NUMERIC(5,2) CHECK (completion_rate BETWEEN 0 AND 100),
    satisfaction_rate NUMERIC(5,2) CHECK (satisfaction_rate BETWEEN 0 AND 100),
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE'
        CHECK (status IN ('AVAILABLE','BUSY','OFFLINE','SUSPENDED','RESTRICTED')),
    is_online BOOLEAN NOT NULL DEFAULT false,
    approved_at TIMESTAMPTZ,
    approved_by UUID REFERENCES admins(id)
);
CREATE TABLE addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    title VARCHAR(100),
    receiver_name VARCHAR(160) NOT NULL,
    receiver_phone VARCHAR(30) NOT NULL,
    province VARCHAR(100) NOT NULL,
    district VARCHAR(100),
    ward VARCHAR(100) NOT NULL,
    detail_address TEXT NOT NULL,
    latitude NUMERIC(10,7) CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(10,7) CHECK (longitude BETWEEN -180 AND 180),
    note TEXT,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_customer_default_address ON addresses(customer_id) WHERE is_default;
CREATE INDEX ix_addresses_customer ON addresses(customer_id);
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    reference_id UUID,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ
);
CREATE INDEX ix_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at)
);
