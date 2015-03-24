package com.cloud.agent.api;

public class UpdateNuageVspDeviceAnswer extends Answer {

    public UpdateNuageVspDeviceAnswer(UpdateNuageVspDeviceCommand cmd, boolean sucess, String details) {
        super(cmd, sucess, details);
    }

    public UpdateNuageVspDeviceAnswer(UpdateNuageVspDeviceCommand cmd, Exception e) {
        super(cmd, e);
    }
}
