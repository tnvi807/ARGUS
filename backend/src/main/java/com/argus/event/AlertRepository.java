package com.argus.event;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findTop100ByOrderByTsDesc();

    List<Alert> findTop50ByStatusOrderByTsDesc(Alert.Status status);

    Optional<Alert> findById(Long id);

    long countByStatus(Alert.Status status);
}
