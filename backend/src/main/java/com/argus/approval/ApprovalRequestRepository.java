package com.argus.approval;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findTop100ByOrderByTsDesc();

    List<ApprovalRequest> findTop50ByStatusOrderByTsDesc(ApprovalRequest.Status status);

    long countByStatus(ApprovalRequest.Status status);
}
