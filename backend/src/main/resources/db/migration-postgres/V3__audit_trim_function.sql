-- ============================================================================
-- V3 - audit ledger retention that respects the V2 append-only trigger (PostgreSQL only)
--
-- V2 refuses every row-level UPDATE/DELETE on audit_entries, which would also
-- block the application's retention trim: once the ledger passes
-- argus.feed.max-events * 2 (800 rows), AuditWriter drops the oldest rows.
-- Rather than weaken the trigger, retention is expressed as a database
-- function the application calls instead of DELETE:
--
--   1. copy the newest keep_rows rows into a temp table,
--   2. TRUNCATE audit_entries  - statement level; row-level triggers do not
--      fire, so audit_entries_append_only never raises,
--   3. copy the kept rows back.
--
-- Ids are preserved and TRUNCATE does not restart identities, so the ledger
-- continues seamlessly. The trigger still refuses UPDATE and DELETE from every
-- other path - only this function may remove rows, and only by retention.
--
-- Like V2 this script lives in db/migration-postgres/: H2 (the `local` profile
-- and the test suite, both MODE=PostgreSQL) flies only with
-- classpath:db/migration and skips it; on H2 the trim runs as a native DELETE
-- against a table that has no trigger.
-- ============================================================================

CREATE OR REPLACE FUNCTION audit_trim(keep_rows integer) RETURNS integer AS $$
DECLARE
    total integer;
BEGIN
    SELECT count(*) INTO total FROM audit_entries;
    IF total <= keep_rows THEN
        RETURN total;
    END IF;

    -- Block concurrent inserts before the copy so no row is lost between
    -- copying and truncating; held until the calling transaction commits.
    LOCK TABLE audit_entries IN EXCLUSIVE MODE;

    DROP TABLE IF EXISTS pg_temp.audit_trim_keep;

    CREATE TEMP TABLE audit_trim_keep ON COMMIT PRESERVE ROWS AS
        SELECT * FROM audit_entries ORDER BY ts DESC, id DESC LIMIT keep_rows;

    TRUNCATE audit_entries;

    INSERT INTO audit_entries SELECT * FROM audit_trim_keep;

    DROP TABLE pg_temp.audit_trim_keep;

    RETURN keep_rows;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
