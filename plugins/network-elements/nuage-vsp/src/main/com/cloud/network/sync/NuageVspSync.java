package com.cloud.network.sync;

import net.nuage.vsp.client.common.model.NuageVspEntity;

public interface NuageVspSync {

    public void syncWithNuageVsp(NuageVspEntity nuageVspEntity);
}
