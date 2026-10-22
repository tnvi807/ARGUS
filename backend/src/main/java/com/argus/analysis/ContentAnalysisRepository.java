package com.argus.analysis;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentAnalysisRepository extends JpaRepository<ContentAnalysis, Long> {

    Page<ContentAnalysis> findAllByOrderByTsDesc(Pageable pageable);
}
