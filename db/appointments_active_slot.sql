-- MySQL 8 / InnoDB. Apply once to the intended schema, with appointment writes stopped.
-- Preflight: MUST return zero rows. Do not delete or modify duplicates automatically.
SELECT doctor_id, scheduled_at, COUNT(*) AS active_count
FROM appointments WHERE status = 'SCHEDULED'
GROUP BY doctor_id, scheduled_at HAVING COUNT(*) > 1;

-- Check SHOW CREATE TABLE appointments before applying. If either object exists,
-- verify its definition instead of blindly reapplying this migration.
-- DATETIME(6) preserves scheduled_at's precision. A single atomic ALTER prevents
-- a partial column-only migration if index creation rejects duplicate data.
ALTER TABLE appointments
    ADD COLUMN active_slot_at DATETIME(6)
        GENERATED ALWAYS AS (CASE WHEN status = 'SCHEDULED' THEN scheduled_at ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_active_doctor_slot (doctor_id, active_slot_at);
