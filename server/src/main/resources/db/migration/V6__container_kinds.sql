-- Expand container kinds from {STACK, BOX} to {STACK, SHORT_BOX, LONG_BOX}.
-- Migrate existing BOX rows to SHORT_BOX before tightening the constraint.

DO $$
DECLARE
    con_name TEXT;
BEGIN
    SELECT conname INTO con_name
    FROM pg_constraint
    WHERE conrelid = 'container'::regclass AND contype = 'c' AND conname LIKE '%kind%';
    IF con_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE container DROP CONSTRAINT %I', con_name);
    END IF;
END $$;

UPDATE container SET kind = 'SHORT_BOX' WHERE kind = 'BOX';

ALTER TABLE container ADD CONSTRAINT container_kind_check
    CHECK (kind IN ('STACK', 'SHORT_BOX', 'LONG_BOX'));
