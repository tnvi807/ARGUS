package com.argus.containment;

import com.argus.security.Permissions;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/containments")
public class ContainmentController {

    private final ContainmentActionRepository repository;

    public ContainmentController(ContainmentActionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ALERTS_READ + "')")
    public List<ContainmentAction> list() {
        return repository.findTop100ByOrderByTsDesc();
    }
}
