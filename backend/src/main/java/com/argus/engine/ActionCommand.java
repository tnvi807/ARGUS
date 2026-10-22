package com.argus.engine;

import java.util.List;

/** Immutable input for one agent action submitted to the gateway. */
public record ActionCommand(
        String agentId,
        String kind,
        String tool,
        String category,
        String target,
        String message,
        String toAgent,
        Double sensitivity,
        String demo) {

    public ActionCommand {
        if (kind == null || kind.isBlank()) {
            kind = "read";
        }
    }
}
