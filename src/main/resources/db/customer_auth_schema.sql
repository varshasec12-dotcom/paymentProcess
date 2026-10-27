-- =========================================================
-- Customer online-banking login
-- Run once against payments_db (ddl-auto=validate will not create these)
-- =========================================================

-- NULL for customers created by the bank (POST /api/customers) who have not
-- registered for online banking yet
ALTER TABLE customers ADD COLUMN password_hash VARCHAR(100);

-- Lockout after repeated wrong passwords
ALTER TABLE customers ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN locked_until TIMESTAMP(6);

ALTER TABLE customers ADD CONSTRAINT ck_customers_failed_login_attempts CHECK (failed_login_attempts >= 0);
