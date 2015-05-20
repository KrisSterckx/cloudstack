package com.cloud.agent.api;

import com.cloud.host.Host;

public class PingNuageVspCommand extends PingCommand {

    private boolean shouldAudit;

    public PingNuageVspCommand(Host.Type type, long id, boolean shouldAudit) {
        super(type, id);
        this.shouldAudit = shouldAudit;
    }

    public boolean shouldAudit() {
        return shouldAudit;
    }
}
