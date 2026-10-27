-- =========================================================
-- Generated account numbers
-- Run once against payments_db (ddl-auto=validate will not create this)
-- =========================================================

-- Source of new 12-digit account numbers; each value is handed out only once
CREATE SEQUENCE account_number_seq START WITH 100000000000 MINVALUE 100000000000 MAXVALUE 999999999999;
