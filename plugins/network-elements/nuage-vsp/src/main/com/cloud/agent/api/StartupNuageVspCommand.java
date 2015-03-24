package com.cloud.agent.api;

import com.cloud.host.Host;

public class StartupNuageVspCommand extends StartupCommand {

    public StartupNuageVspCommand() {
        super(Host.Type.L2Networking);
    }
}
