package com.cloud.util;

import java.util.List;

import com.cloud.domain.Domain;
import com.cloud.domain.dao.DomainDao;
import com.cloud.network.manager.NuageVspManager;
import com.cloud.user.Account;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.Pair;
import net.nuage.vsp.client.common.model.NuageVspAPIParams;
import net.nuage.vsp.client.exception.NuageVspAPIUtilException;
import net.nuage.vsp.client.rest.NuageVspApiUtil;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import com.cloud.network.NuageVspDeviceVO;
import com.cloud.network.dao.NuageVspDao;
import com.cloud.utils.StringUtils;
import com.google.common.collect.Maps;
import net.nuage.vsp.client.common.model.NuageVspEntity;
import net.nuage.vsp.client.rest.NuageVspConstants;
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

    public static boolean containsNuageVspDeviceCmsId(long nuageVspDeviceId, String configValue) {
        return org.apache.commons.lang.StringUtils.isNotBlank(configValue) &&
                (configValue.startsWith(nuageVspDeviceId + ":") || configValue.contains(";" + nuageVspDeviceId + ":"));
    }

    public static String getRegisteredNuageVspDevice(long nuageVspDeviceId, String cmsId) {
        return nuageVspDeviceId + ":" + cmsId + ";";
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

    public static String getEnterpriseId(Domain domain, DomainDao domainDao, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        if (NuageVspManager.NuageVspMultiTenancy.value() == Boolean.FALSE) {
            Domain rootDomain = domainDao.findById(Domain.ROOT_DOMAIN);
            return NuageVspApiUtil.getOrCreateVSPEnterprise(rootDomain.getUuid(), rootDomain.getName(), rootDomain.getPath(), nuageVspAPIParams);
        }
        return NuageVspApiUtil.getOrCreateVSPEnterprise(domain.getUuid(), domain.getName(), domain.getPath(), nuageVspAPIParams);
    }

    public static Pair<String, String> getEnterpriseAndGroupId(Domain domain, DomainDao domainDao, Account account, AccountDao accountDao, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        if (NuageVspManager.NuageVspMultiTenancy.value() == Boolean.FALSE) {
            Domain rootDomain = domainDao.findById(Domain.ROOT_DOMAIN);
            Account systemAccount = accountDao.findById(Account.ACCOUNT_ID_SYSTEM);
            String[] enterpriseAndGroupId = NuageVspApiUtil.getOrCreateVSPEnterpriseAndGroup(rootDomain.getName(), rootDomain.getPath(), rootDomain.getUuid(),
                    systemAccount.getAccountName(), systemAccount.getUuid(), nuageVspAPIParams);
            return new Pair<>(enterpriseAndGroupId[0], enterpriseAndGroupId[1]);
        }
        String[] enterpriseAndGroupId = NuageVspApiUtil.getOrCreateVSPEnterpriseAndGroup(domain.getName(), domain.getPath(), domain.getUuid(),
                account.getAccountName(), account.getUuid(), nuageVspAPIParams);
        return new Pair<>(enterpriseAndGroupId[0], enterpriseAndGroupId[1]);
    }
}
