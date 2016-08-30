package com.cloud.network.upgrade;

import net.nuage.vsp.client.common.model.NuageVspAPIParams;
import net.nuage.vsp.client.exception.NuageVspException;
import net.nuage.vsp.client.rest.NuageVspApiVersion;

/**
 *
 */
public class NuageApi32To40Upgrade implements NuageUpgrade {

    @Override
    public void upgrade(NuageVspAPIParams nuageVspHostParams)  throws NuageVspException {
    }

    @Override
    public NuageVspApiVersion getUpgradeVersion() {
        return NuageVspApiVersion.V4_0;
    }

}
