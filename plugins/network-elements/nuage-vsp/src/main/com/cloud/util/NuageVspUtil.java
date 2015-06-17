package com.cloud.util;

import com.cloud.network.NuageVspDeviceVO;
import com.cloud.network.dao.NuageVspDao;
import com.cloud.utils.StringUtils;
import com.google.common.collect.Maps;
import net.nuage.vsp.client.common.model.NuageVspEntity;
import net.nuage.vsp.client.rest.NuageVspConstants;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;

import java.util.List;
import java.util.Map;

public class NuageVspUtil {

    public static String findNuageVspDeviceCmsId(long nuageVspDeviceId, String configValue) {
        if (StringUtils.isNotBlank(configValue)) {
            String[] configuredNuageVspDevices = configValue.split(";");
            for (String configuredNuageVspDevice : configuredNuageVspDevices) {
                if (configuredNuageVspDevice.startsWith(nuageVspDeviceId + ":")) {
                    return configuredNuageVspDevice.split(":")[1];
                }
            }
        }
        return null;
    }

    public static String findNuageVspDeviceCmsId(long nuageVspDeviceId, ConfigurationDao configDao) {
        String configValue = configDao.getValue("nuagevsp.cms.id");
        return findNuageVspDeviceCmsId(nuageVspDeviceId, configValue);
    }

    public static String findNuageVspDeviceCmsIdByPhysNet(long physicalNetworkId, NuageVspDao nuageVspDao, ConfigurationDao configDao) {
        List<NuageVspDeviceVO> nuageVspDevices = nuageVspDao.listByPhysicalNetwork(physicalNetworkId);
        if (nuageVspDevices != null && (!nuageVspDevices.isEmpty())) {
            NuageVspDeviceVO nuageVspDevice = nuageVspDevices.iterator().next();
            return findNuageVspDeviceCmsId(nuageVspDevice.getId(), configDao);
        }
        return null;
    }

    public static String findNuageVspDeviceCmsIdByHost(long hostId, NuageVspDao nuageVspDao, ConfigurationDao configDao) {
        List<NuageVspDeviceVO> nuageVspDevices = nuageVspDao.listByHost(hostId);
        if (nuageVspDevices != null && (!nuageVspDevices.isEmpty())) {
            NuageVspDeviceVO nuageVspDevice = nuageVspDevices.iterator().next();
            return findNuageVspDeviceCmsId(nuageVspDevice.getId(), configDao);
        }
        return null;
    }


    public static Map<String, String> constructNetworkDetails(NuageVspEntity entity, String vsdEnterpriseId, String vsdDomainId, String vsdSubnetId) {
        Map<String, String> networkDetails = Maps.newHashMap();
        networkDetails.put(NuageVspConstants.NETWORK_METADATA_TYPE, entity.name());
        networkDetails.put(NuageVspConstants.NETWORK_METADATA_VSD_ENTERPRISE_ID, vsdEnterpriseId);
        networkDetails.put(NuageVspConstants.NETWORK_METADATA_VSD_DOMAIN_ID, vsdDomainId);
        if (vsdSubnetId != null) {
            networkDetails.put(NuageVspConstants.NETWORK_METADATA_VSD_SUBNET_ID, vsdSubnetId);
        }
        return networkDetails;
    }
}
