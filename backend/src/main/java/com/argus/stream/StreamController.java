package com.argus.stream;

import com.argus.security.Permissions;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events stream for push updates (agents, feed, alerts, approvals, containment).
 * Clients connect with a normal Authorization header - no tokens appear in query strings/logs.
 */
@RestController
@RequestMapping("/api/events")
public class StreamController {

    private final EventHub eventHub;

    public StreamController(EventHub eventHub) {
        this.eventHub = eventHub;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.EVENTS_READ + "')")
    public SseEmitter stream() {
        return eventHub.subscribe();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('" + Permissions.EVENTS_READ + "')")
    public java.util.Map<String, Object> stats() {
        return java.util.Map.of("subscribers", eventHub.subscriberCount());
    }
}
