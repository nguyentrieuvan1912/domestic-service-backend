CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL UNIQUE,
    order_code VARCHAR(60) NOT NULL UNIQUE,
    subtotal BIGINT NOT NULL CHECK (subtotal >= 0),
    add_on_amount BIGINT NOT NULL DEFAULT 0 CHECK (add_on_amount >= 0),
    extra_cost BIGINT NOT NULL DEFAULT 0 CHECK (extra_cost >= 0),
    discount_amount BIGINT NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    total_amount BIGINT NOT NULL CHECK (total_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    payment_method VARCHAR(30) NOT NULL CHECK (payment_method IN ('BANK_TRANSFER_QR','CASH')),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','FINALIZED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finalized_at TIMESTAMPTZ,
    CHECK (total_amount = subtotal + add_on_amount + extra_cost - discount_amount)
);
CREATE TABLE order_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    item_type VARCHAR(20) NOT NULL CHECK (item_type IN ('SERVICE','PACKAGE','ADD_ON','EXTRA_COST')),
    reference_id UUID,
    item_name VARCHAR(160) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
    total_price BIGINT NOT NULL CHECK (total_price = quantity * unit_price)
);
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    order_id UUID REFERENCES orders(id),
    payment_code VARCHAR(60) NOT NULL UNIQUE,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    payment_method VARCHAR(30) NOT NULL CHECK (payment_method IN ('BANK_TRANSFER_QR','CASH')),
    provider VARCHAR(60),
    provider_reference VARCHAR(160),
    transaction_code VARCHAR(160),
    qr_content TEXT,
    expires_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROCESSING','PAID','FAILED','PARTIALLY_REFUNDED','REFUNDED','CANCELLED')),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_payment_provider_transaction ON payments(provider, transaction_code)
    WHERE transaction_code IS NOT NULL;
CREATE INDEX ix_payments_booking ON payments(booking_id);
CREATE TABLE refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments(id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','PROCESSING','PROCESSED','REJECTED','FAILED')),
    transaction_code VARCHAR(160),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by UUID,
    completed_at TIMESTAMPTZ,
    admin_note TEXT
);
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    booking_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    invoice_number VARCHAR(60) NOT NULL UNIQUE,
    customer_snapshot JSONB NOT NULL,
    service_snapshot JSONB NOT NULL,
    subtotal BIGINT NOT NULL CHECK (subtotal >= 0),
    discount_amount BIGINT NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    vat_amount BIGINT NOT NULL DEFAULT 0 CHECK (vat_amount >= 0),
    total_amount BIGINT NOT NULL CHECK (total_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ISSUED','PAID','CANCELLED')),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (total_amount = subtotal - discount_amount + vat_amount)
);
CREATE TABLE invoice_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    name VARCHAR(160) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
    total_price BIGINT NOT NULL CHECK (total_price = quantity * unit_price)
);
CREATE TABLE staff_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL UNIQUE,
    available_balance BIGINT NOT NULL DEFAULT 0 CHECK (available_balance >= 0),
    pending_balance BIGINT NOT NULL DEFAULT 0 CHECK (pending_balance >= 0),
    reserved_balance BIGINT NOT NULL DEFAULT 0 CHECK (reserved_balance >= 0),
    minimum_balance BIGINT NOT NULL DEFAULT 0 CHECK (minimum_balance >= 0),
    total_earned BIGINT NOT NULL DEFAULT 0 CHECK (total_earned >= 0),
    withdrawn_amount BIGINT NOT NULL DEFAULT 0 CHECK (withdrawn_amount >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE staff_incomes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL,
    booking_id UUID NOT NULL,
    balance_id UUID NOT NULL REFERENCES staff_balances(id),
    working_hours NUMERIC(6,2) NOT NULL CHECK (working_hours >= 0),
    service_amount BIGINT NOT NULL CHECK (service_amount >= 0),
    platform_fee BIGINT NOT NULL CHECK (platform_fee >= 0),
    staff_amount BIGINT NOT NULL CHECK (staff_amount >= 0),
    tip_amount BIGINT NOT NULL DEFAULT 0 CHECK (tip_amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SETTLED','REVERSED')),
    income_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled_at TIMESTAMPTZ,
    CHECK (staff_amount = service_amount - platform_fee + tip_amount),
    UNIQUE(booking_id, staff_id)
);
CREATE TABLE rewards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL,
    reward_type VARCHAR(40) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    rank INTEGER CHECK (rank > 0),
    revenue BIGINT NOT NULL DEFAULT 0 CHECK (revenue >= 0),
    amount BIGINT NOT NULL CHECK (amount >= 0),
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','APPLIED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (period_end >= period_start)
);
CREATE TABLE penalties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL,
    booking_id UUID,
    penalty_type VARCHAR(40) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount >= 0),
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','APPLIED','APPEALED','WAIVED')),
    approved_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE staff_bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL,
    bank_code VARCHAR(30) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    account_holder VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','VERIFIED','INACTIVE')),
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(staff_id, bank_code, account_number)
);
CREATE UNIQUE INDEX uq_staff_default_bank ON staff_bank_accounts(staff_id) WHERE is_default AND status <> 'INACTIVE';
CREATE TABLE withdrawals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id UUID NOT NULL,
    balance_id UUID NOT NULL REFERENCES staff_balances(id),
    bank_account_id UUID NOT NULL REFERENCES staff_bank_accounts(id),
    bank_account_snapshot JSONB NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','PROCESSING','PAID','REJECTED','CANCELLED','FAILED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    transfer_reference VARCHAR(160) UNIQUE,
    rejection_reason TEXT
);
CREATE INDEX ix_withdrawals_staff ON withdrawals(staff_id, requested_at DESC);
CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    balance_id UUID NOT NULL REFERENCES staff_balances(id),
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('CREDIT','DEBIT')),
    bucket VARCHAR(15) NOT NULL CHECK (bucket IN ('AVAILABLE','PENDING','RESERVED')),
    transaction_type VARCHAR(40) NOT NULL CHECK (transaction_type IN
        ('EARNING','TIP','REWARD','PENALTY','WITHDRAWAL','HOLD','RELEASE','REVERSAL')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    reference_id UUID NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    reverses_transaction_id UUID REFERENCES wallet_transactions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_wallet_transactions_balance ON wallet_transactions(balance_id, created_at);
CREATE FUNCTION reject_wallet_transaction_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Wallet ledger is append-only; create a reversal entry instead';
END;
$$;
CREATE TRIGGER wallet_transactions_append_only BEFORE UPDATE OR DELETE ON wallet_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_wallet_transaction_mutation();
CREATE TABLE payment_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(60) NOT NULL,
    provider_event_id VARCHAR(160) NOT NULL,
    payment_id UUID REFERENCES payments(id),
    payload JSONB NOT NULL,
    signature_verified BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED','PROCESSED','REJECTED','FAILED')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    UNIQUE(provider, provider_event_id)
);
ALTER TABLE staff_balances ADD CONSTRAINT uq_balance_id_staff UNIQUE (id, staff_id);
ALTER TABLE staff_bank_accounts ADD CONSTRAINT uq_bank_id_staff UNIQUE (id, staff_id);
ALTER TABLE staff_incomes ADD CONSTRAINT fk_income_staff_wallet
    FOREIGN KEY (balance_id, staff_id) REFERENCES staff_balances(id, staff_id);
ALTER TABLE withdrawals ADD CONSTRAINT fk_withdrawal_staff_wallet
    FOREIGN KEY (balance_id, staff_id) REFERENCES staff_balances(id, staff_id);
ALTER TABLE withdrawals ADD CONSTRAINT fk_withdrawal_staff_bank
    FOREIGN KEY (bank_account_id, staff_id) REFERENCES staff_bank_accounts(id, staff_id);
CREATE FUNCTION validate_refund_total() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE payment_amount BIGINT; payment_state VARCHAR(30); reserved_amount NUMERIC;
BEGIN
    SELECT amount, status INTO payment_amount, payment_state
      FROM payments WHERE id = NEW.payment_id FOR UPDATE;
    IF NEW.status IN ('PENDING','APPROVED','PROCESSING','PROCESSED') THEN
        IF payment_state NOT IN ('PAID','PARTIALLY_REFUNDED','REFUNDED') THEN
            RAISE EXCEPTION 'Only collected payments can be refunded';
        END IF;
        SELECT coalesce(sum(amount), 0) INTO reserved_amount FROM refunds
          WHERE payment_id = NEW.payment_id AND id <> NEW.id
            AND status IN ('PENDING','APPROVED','PROCESSING','PROCESSED');
        IF reserved_amount + NEW.amount > payment_amount THEN
            RAISE EXCEPTION 'Total refunds exceed the collected payment amount';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER refund_total_guard BEFORE INSERT OR UPDATE ON refunds
    FOR EACH ROW EXECUTE FUNCTION validate_refund_total();
-- Service layer must lock wallets and reserve/settle withdrawals and ledger entries atomically.
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
