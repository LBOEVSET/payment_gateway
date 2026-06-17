-- =====================================================================
-- Hospital Payment Records table
-- Run this ONCE against the payment_gateway PostgreSQL database.
-- JPA ddl-auto is set to 'none' (Prisma owns the schema), so this
-- table must be created manually.
-- =====================================================================

CREATE TABLE IF NOT EXISTS hospital_payment_records (
    id              VARCHAR(36)     PRIMARY KEY,
    booking_number  VARCHAR(32)     NOT NULL UNIQUE,
    user_id         VARCHAR(64)     NOT NULL,
    patient_name    VARCHAR(128),
    service_name    VARCHAR(256),
    amount          NUMERIC(12, 2)  NOT NULL,
    payment_method  VARCHAR(20),
    payment_status  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    charge_id       VARCHAR(64),
    omise_status    VARCHAR(32),
    failure_message VARCHAR(512),
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_hpr_booking_number ON hospital_payment_records (booking_number);
CREATE INDEX IF NOT EXISTS idx_hpr_charge_id      ON hospital_payment_records (charge_id);
CREATE INDEX IF NOT EXISTS idx_hpr_user_id        ON hospital_payment_records (user_id);

-- Trigger to keep updated_at current automatically
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_hpr_updated_at ON hospital_payment_records;
CREATE TRIGGER trg_hpr_updated_at
  BEFORE UPDATE ON hospital_payment_records
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
