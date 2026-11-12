-- =========================================================
-- One account of each type (SAVINGS, CURRENT, SALARY) per customer
-- Run once against payments_db (ddl-auto=validate will not create this)
-- =========================================================

-- Customers who already have more than one account of a type; deal with the extras first,
-- or the index below cannot be created
--   SELECT customer_id, account_type, array_agg(account_number) FROM accounts
--   GROUP BY customer_id, account_type HAVING count(*) > 1;

-- Replaces the earlier salary-only index, if it was created
DROP INDEX IF EXISTS ux_accounts_one_salary_per_customer;

CREATE UNIQUE INDEX ux_accounts_one_per_type_per_customer
    ON accounts (customer_id, account_type);
