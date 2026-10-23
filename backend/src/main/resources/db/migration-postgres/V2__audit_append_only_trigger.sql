-- ============================================================================
-- V2 - database-enforced append-only audit ledger (PostgreSQL only)
--
-- The audit ledger is append-only: rows are inserted by the application and are
-- never updated or deleted by it. V1 creates the table, and the application
-- layer already rejects UPDATEs (AuditEntry.@PreUpdate). This migration adds the
-- database-side guarantee so a direct SQL client cannot tamper with it either:
-- any UPDATE or DELETE on audit_entries raises an exception and rolls back.
--
-- This script lives in db/migration-postgres/ because H2 (the `local` profile
-- and the test suite, both MODE=PostgreSQL) cannot run PL/pgSQL triggers. Those
-- profiles fly only with classpath:db/migration and therefore skip V2; on H2 the
-- append-only guarantee stops at the application layer.
-- ============================================================================

CREATE OR REPLACE FUNCTION audit_entries_forbid_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_entries is append-only: % is not permitted', TG_OP
        USING ERRCODE = 'raise_exception',
              HINT = 'Audit rows can only be inserted; UPDATE and DELETE are refused by the database.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_entries_append_only
    BEFORE UPDATE OR DELETE ON audit_entries
    FOR EACH ROW
    EXECUTE FUNCTION audit_entries_forbid_mutation();
