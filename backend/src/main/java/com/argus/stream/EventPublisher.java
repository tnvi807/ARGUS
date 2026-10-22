package com.argus.stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes SSE events only after the surrounding transaction commits, so subscribers that react
 * immediately by re-querying REST always observe the new state.
 */
@Component
public class EventPublisher {

    private final EventHub eventHub;

    public EventPublisher(EventHub eventHub) {
        this.eventHub = eventHub;
    }

    public void publish(String event, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventHub.publish(event, payload);
                }
            });
        } else {
            eventHub.publish(event, payload);
        }
    }
}
