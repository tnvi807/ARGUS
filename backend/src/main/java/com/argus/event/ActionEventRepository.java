package com.argus.event;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionEventRepository extends JpaRepository<ActionEvent, Long> {

    @Query("""
            select e from ActionEvent e
            where (:decision is null or e.decision = :decision)
              and (:q is null or lower(e.agentName) like lower(concat('%', :q, '%'))
                or lower(coalesce(e.target, '')) like lower(concat('%', :q, '%'))
                or lower(e.kind) like lower(concat('%', :q, '%'))
                or lower(e.decision) like lower(concat('%', :q, '%'))
                or lower(coalesce(e.category, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(e.tool, '')) like lower(concat('%', :q, '%')))
            order by e.ts desc, e.id desc
            """)
    Page<ActionEvent> search(@Param("decision") String decision, @Param("q") String q, Pageable pageable);

    List<ActionEvent> findTop50ByOrderByTsDesc();

    long countByTsAfter(Instant since);

    @Query("select coalesce(avg(e.riskScore), 0) from ActionEvent e where e.ts >= :since")
    double avgRiskSince(@Param("since") Instant since);

    List<ActionEvent> findTop200ByOrderByTsDesc();

    long countByAgentIdAndTsAfter(String agentId, Instant since);

    long countByDecision(String decision);

    long countByDecisionAndTsAfter(String decision, Instant since);

    @Modifying
    @Query(value = "delete from action_events where id not in "
            + "(select id from action_events order by ts desc, id desc limit :keep)", nativeQuery = true)
    void trim(@Param("keep") int keep);
}
