package com.argus.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    @Query("""
            select a from AuditEntry a
            where (:q is null or lower(a.description) like lower(concat('%', :q, '%'))
                or lower(coalesce(a.agent, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(a.actor, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(a.kind, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(a.decision, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(a.target, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(a.reason, '')) like lower(concat('%', :q, '%')))
            order by a.ts desc, a.id desc
            """)
    Page<AuditEntry> search(@Param("q") String q, Pageable pageable);

    @Query("select a from AuditEntry a where a.ts >= :since order by a.ts desc")
    List<AuditEntry> findSince(@Param("since") Instant since);

    @Modifying
    @Query(value = "delete from audit_entries where id not in "
            + "(select id from audit_entries order by ts desc, id desc limit :keep)", nativeQuery = true)
    void trim(@Param("keep") int keep);

    /**
     * PostgreSQL retention path (V3): the append-only trigger refuses row-level
     * DELETEs, so the trim runs as the audit_trim() database function instead
     * (copy newest rows out, TRUNCATE, copy back - never a row-level DELETE).
     * Returns the resulting row count.
     */
    @Query(value = "select audit_trim(:keep)", nativeQuery = true)
    Integer trimOnPostgres(@Param("keep") int keep);
}
