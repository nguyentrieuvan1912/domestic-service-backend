CREATE TABLE service_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_code VARCHAR(60) NOT NULL UNIQUE,
    category_name VARCHAR(160) NOT NULL,
    description TEXT,
    icon_url TEXT,
    category_group VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES service_categories(id),
    service_code VARCHAR(60) NOT NULL UNIQUE,
    service_name VARCHAR(160) NOT NULL,
    description TEXT,
    short_description TEXT,
    service_type VARCHAR(60) NOT NULL,
    price_unit VARCHAR(30) NOT NULL,
    base_price BIGINT NOT NULL DEFAULT 0 CHECK (base_price >= 0),
    estimated_duration_minutes INTEGER NOT NULL CHECK (estimated_duration_minutes > 0),
    requires_qualification BOOLEAN NOT NULL DEFAULT false,
    image_url TEXT,
    highlights JSONB NOT NULL DEFAULT '[]',
    workflow JSONB NOT NULL DEFAULT '[]',
    benefits JSONB NOT NULL DEFAULT '[]',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_services_category ON services(category_id);
CREATE TABLE service_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL REFERENCES services(id),
    package_name VARCHAR(160) NOT NULL,
    description TEXT,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    base_price BIGINT NOT NULL CHECK (base_price >= 0),
    default_staff_count INTEGER NOT NULL DEFAULT 1 CHECK (default_staff_count > 0),
    max_area NUMERIC(10,2) CHECK (max_area > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(service_id, package_name)
);
CREATE TABLE add_ons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL REFERENCES services(id),
    name VARCHAR(160) NOT NULL,
    description TEXT,
    price BIGINT NOT NULL CHECK (price >= 0),
    extra_duration_minutes INTEGER NOT NULL DEFAULT 0 CHECK (extra_duration_minutes >= 0),
    image_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE service_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL REFERENCES services(id),
    field_key VARCHAR(80) NOT NULL,
    label VARCHAR(160) NOT NULL,
    field_type VARCHAR(30) NOT NULL CHECK (field_type IN ('TEXT','NUMBER','BOOLEAN','SELECT','MULTI_SELECT','DATE')),
    required BOOLEAN NOT NULL DEFAULT false,
    options JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(options) = 'array'),
    validation_rules JSONB NOT NULL DEFAULT '{}',
    display_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    UNIQUE(service_id, field_key)
);
CREATE TABLE promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_code VARCHAR(60) NOT NULL UNIQUE,
    promotion_name VARCHAR(160) NOT NULL,
    description TEXT,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('PERCENTAGE','FIXED_AMOUNT')),
    discount_value BIGINT NOT NULL CHECK (discount_value >= 0),
    max_discount_amount BIGINT CHECK (max_discount_amount >= 0),
    minimum_booking_amount BIGINT NOT NULL DEFAULT 0 CHECK (minimum_booking_amount >= 0),
    usage_limit INTEGER CHECK (usage_limit > 0),
    used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    CHECK (end_at > start_at),
    CHECK (discount_type <> 'PERCENTAGE' OR discount_value <= 100),
    CHECK (usage_limit IS NULL OR used_count <= usage_limit)
);
CREATE TABLE promotion_services (
    promotion_id UUID NOT NULL REFERENCES promotions(id),
    service_id UUID NOT NULL REFERENCES services(id),
    PRIMARY KEY(promotion_id, service_id)
);
CREATE TABLE price_quotes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    service_id UUID NOT NULL REFERENCES services(id),
    package_id UUID REFERENCES service_packages(id),
    snapshot JSONB NOT NULL,
    base_amount BIGINT NOT NULL CHECK (base_amount >= 0),
    add_on_amount BIGINT NOT NULL DEFAULT 0 CHECK (add_on_amount >= 0),
    discount_amount BIGINT NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    total_amount BIGINT NOT NULL CHECK (total_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    quote_version BIGINT NOT NULL DEFAULT 1,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (total_amount = base_amount + add_on_amount - discount_amount),
    CHECK (expires_at > created_at)
);
