package com.argus.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentCommsRepository extends JpaRepository<AgentComms, Long> {

    List<AgentComms> findTop14ByOrderByTsDesc();

    List<AgentComms> findTop60ByOrderByTsDesc();
}
