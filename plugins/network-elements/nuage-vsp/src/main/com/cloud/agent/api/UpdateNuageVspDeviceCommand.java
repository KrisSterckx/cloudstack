package com.cloud.agent.api;

import java.util.Map;

public class UpdateNuageVspDeviceCommand extends Command {

    Map<String, String> _parametersToBeUpdated;

    public UpdateNuageVspDeviceCommand(Map<String, String> parametersToBeUpdated) {
        super();
        this._parametersToBeUpdated = parametersToBeUpdated;
    }

    public Map<String, String> getParametersToBeUpdated() {
        return _parametersToBeUpdated;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }

}