package com.cloud.util;

import com.cloud.network.NuageVspDeviceVO;
import com.cloud.network.dao.NuageVspDao;
import com.cloud.utils.StringUtils;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;

import java.util.List;

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

}
