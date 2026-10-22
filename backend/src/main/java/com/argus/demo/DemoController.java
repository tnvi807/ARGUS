package com.argus.demo;

import com.argus.security.Permissions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demos")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_READ + "')")
    public Map<String, Object> catalog() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("demos", DemoService.catalog());
        body.put("running", demoService.isBusy());
        return body;
    }

    @PostMapping("/{key}")
    @PreAuthorize("hasAuthority('" + Permissions.DEMOS_RUN + "')")
    public ResponseEntity<Map<String, Object>> run(@PathVariable String key) {
        boolean started = demoService.start(key);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", key);
        if (!started) {
            body.put("started", false);
            body.put("message", "Another demo is still running");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        body.put("started", true);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @PostMapping("/reset")
    @PreAuthorize("hasAuthority('" + Permissions.DEMOS_RUN + "')")
    public Map<String, Object> reset() {
        return demoService.reset();
    }
}
