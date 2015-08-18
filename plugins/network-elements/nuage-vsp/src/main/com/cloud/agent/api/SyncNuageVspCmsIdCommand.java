package com.cloud.agent.api;

import java.util.Map;

public class SyncNuageVspCmsIdCommand extends Command {

    public enum SyncType { AUDIT, AUDIT_ONLY, AUDIT_WITH_CORRECTION, REGISTER, UNREGISTER }

    private long _nuageVspDeviceId;
    private String _currentCmsIdConfigValue;
    private Map<String, String> _hostDetails;
    private SyncType _syncType;

    public SyncNuageVspCmsIdCommand(long nuageVspDeviceId, String currentCmsIdConfigValue, Map<String, String> hostDetails, SyncType syncType) {
        super();
        this._nuageVspDeviceId = nuageVspDeviceId;
        this._currentCmsIdConfigValue = currentCmsIdConfigValue;
        this._hostDetails = hostDetails;
        this._syncType = syncType;
    }

    public long getNuageVspDeviceId() {
        return _nuageVspDeviceId;
    }

    public String getCurrentCmsIdConfigValue() {
        return _currentCmsIdConfigValue;
    }

    public Map<String, String> getHostDetails() {
        return _hostDetails;
    }

    public SyncType getSyncType() {
        return _syncType;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}
