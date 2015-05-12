package com.cloud.agent.api;

import static com.cloud.agent.api.SyncNuageVspCmsIdCommand.SyncType;

public class SyncNuageVspCmsIdAnswer extends Answer {

    private boolean _success;
    private String _registeredNuageVspDevice;
    private SyncType _syncType;

    public SyncNuageVspCmsIdAnswer(boolean success, String registeredNuageVspDevice, SyncType syncType) {
        super();
        this._success = success;
        this._registeredNuageVspDevice = registeredNuageVspDevice;
        this._syncType = syncType;
    }

    public boolean getSuccess() {
        return _success;
    }

    public String getRegisteredNuageVspDevice() {
        return _registeredNuageVspDevice;
    }

    public SyncType getSyncType() {
        return _syncType;
    }

}
