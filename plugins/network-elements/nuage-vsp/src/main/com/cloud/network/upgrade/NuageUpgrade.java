package com.cloud.network.upgrade;

import net.nuage.vsp.client.common.model.NuageVspAPIParams;
import net.nuage.vsp.client.exception.NuageVspException;
import net.nuage.vsp.client.rest.NuageVspApiVersion;

/**
 * Created by maximusf on 5/29/15.
 */
public interface NuageUpgrade {
    void upgrade(NuageVspAPIParams nuageVspHostParams)  throws NuageVspException;

    NuageVspApiVersion getUpgradeVersion();
}
