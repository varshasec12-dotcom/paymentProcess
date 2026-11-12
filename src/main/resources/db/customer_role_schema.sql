-- =========================================================
-- Customer roles (bank staff vs. customers)
-- Run once against payments_db (ddl-auto=validate will not create this)
-- =========================================================

-- Everyone is a CUSTOMER. ADMIN can create, list and deactivate customers and view all accounts.
ALTER TABLE customers ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE customers ADD CONSTRAINT ck_customers_role CHECK (role IN ('CUSTOMER', 'ADMIN'));

-- To make someone an admin: they register normally first (so they have a password), then run
--   UPDATE customers SET role = 'ADMIN' WHERE email = 'staff@example.com';
-- The new role applies from their next login.
