package com.argus.containment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContainmentActionRepository extends JpaRepository<ContainmentAction, Long> {

    List<ContainmentAction> findTop100ByOrderByTsDesc();

    List<ContainmentAction> findByAgentIdAndActiveTrue(String agentId);
}
