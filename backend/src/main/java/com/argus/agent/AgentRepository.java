package com.argus.agent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, String> {

    List<Agent> findAllByOrderByIdAsc();

    Optional<Agent> findByNameIgnoreCase(String name);

    long countByStatus(Agent.Status status);
}
