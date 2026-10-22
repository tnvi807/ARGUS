package com.argus.stream;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events hub backing the real-time dashboard feed.
 *
 * <p>SSE was chosen over WebSockets because it travels over plain HTTP with the normal
 * Authorization header (no token in query strings / logs), auto-reconnects in the browser and
 * needs no extra protocol on top of Spring MVC.
 */
@Component
public class EventHub {

    private static final Logger log = LoggerFactory.getLogger(EventHub.class);
    private static final long HEARTBEAT_MS = 15_000L;

    private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(e -> clients.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true)));
        } catch (IOException e) {
            clients.remove(emitter);
        }
        return emitter;
    }

    public void publish(String event, Object payload) {
        if (clients.isEmpty()) {
            return;
        }
        for (SseEmitter client : clients) {
            try {
                client.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                clients.remove(client);
                log.debug("Dropped SSE client after send failure");
            }
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = HEARTBEAT_MS)
    public void heartbeat() {
        for (SseEmitter client : clients) {
            try {
                client.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                clients.remove(client);
            }
        }
    }

    public int subscriberCount() {
        return clients.size();
    }
}
