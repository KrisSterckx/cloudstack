package com.cloud.network.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.cloud.util.NuageVspUtil;
import net.nuage.vsp.client.common.RequestType;
import net.nuage.vsp.client.common.model.NuageVspAPIParams;
import net.nuage.vsp.client.common.model.NuageVspAttribute;
import net.nuage.vsp.client.common.model.NuageVspEntity;
import net.nuage.vsp.client.rest.NuageVspApi;
import net.nuage.vsp.client.rest.NuageVspApiUtil;

import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.NuageVspDeviceVO;
import com.cloud.network.dao.NuageVspDao;
import com.cloud.utils.StringUtils;

@Component
public class NuageVspSyncImpl implements NuageVspSync {

    private static final Logger s_logger = Logger.getLogger(NuageVspSyncImpl.class);

    @Inject
    NuageVspDao _nuageVspDao;
    @Inject
    HostDao _hostDao;
    @Inject
    ConfigurationDao _configDao;

    public void syncWithNuageVsp(NuageVspEntity nuageVspEntity) {
        //get the NuageVspDevice and get the host information.
        //This information is used to query VSP and synch the corresponding
        //entities
        List<NuageVspDeviceVO> nuageVspDevices = _nuageVspDao.listAll();
        for (NuageVspDeviceVO nuageVspDevice : nuageVspDevices) {
            try {
                HostVO nuageVspHost = _hostDao.findById(nuageVspDevice.getHostId());
                _hostDao.loadDetails(nuageVspHost);
                if (nuageVspHost != null) {
                    String nuageVspCmsId = NuageVspUtil.findNuageVspDeviceCmsId(nuageVspDevice.getId(), _configDao);
                    NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(nuageVspHost, nuageVspCmsId);
                    if (nuageVspEntity.equals(NuageVspEntity.FLOATING_IP)) {
                        cleanUpFloatingIp(nuageVspAPIParamsAsCmsUser);
                    } else if (nuageVspEntity.equals(NuageVspEntity.GROUP)) {
                        cleanUpUserAndGroup(nuageVspAPIParamsAsCmsUser);
                    } else if (nuageVspEntity.equals(NuageVspEntity.ENTERPRISE_NTWK_MACRO)) {
                        cleanUpMacro(nuageVspAPIParamsAsCmsUser);
                    } else if (nuageVspEntity.equals(NuageVspEntity.ENTERPRISE)) {
                        cleanUpEnterprise(nuageVspAPIParamsAsCmsUser);
                    }
                } else {
                    s_logger.info("NuageVspDevice " + nuageVspDevice.getDeviceName() + " and phsysicalNetworkId " + nuageVspDevice.getPhysicalNetworkId()
                            + " is not configured. So, sync is not done.");
                }
            } catch (Exception e) {
                s_logger.info("Exception happen during sync of " + nuageVspEntity + " for nuageVspHost " + nuageVspDevice
                        + ". So, skipping the sync and proceeding to next nuageVspHost", e);
            }
        }
    }

    private void cleanUpFloatingIp(NuageVspAPIParams nuageVspAPIParamsAsCmsUser) throws Exception {
        List<String> activeFIPSubnets = new ArrayList<String>();
        //Get all the Floating IP Subnets from VSP
        String floatingIPSubnetJson = NuageVspApi.executeRestApi(RequestType.GETALL, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.SHARED_NETWORK, NuageVspAttribute.SHARED_RESOURCE_TYPE.getAttributeName() + " == 'FLOATING'",
                nuageVspAPIParamsAsCmsUser.getRestRelativePath(), nuageVspAPIParamsAsCmsUser.getCmsUserInfo(), nuageVspAPIParamsAsCmsUser.getNoofRetry(),
                nuageVspAPIParamsAsCmsUser.getRetryInterval(), nuageVspAPIParamsAsCmsUser.isCmsUser(), nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
        List<Map<String, Object>> floatingIpSubnets = NuageVspApi.parseJsonString(NuageVspEntity.SHARED_NETWORK, floatingIPSubnetJson);
        if (floatingIpSubnets.size() > 0) {
            //Get all then domains in the enterprise and check if any of the domain holds an FloatingIp that is derived from
            //the floatingIpSubnetId
            String enterpriseJson = NuageVspApi.executeRestApi(RequestType.GETALL, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                    nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.ENTERPRISE, null, nuageVspAPIParamsAsCmsUser.getRestRelativePath(),
                    nuageVspAPIParamsAsCmsUser.getCmsUserInfo(), nuageVspAPIParamsAsCmsUser.getNoofRetry(), nuageVspAPIParamsAsCmsUser.getRetryInterval(),
                    nuageVspAPIParamsAsCmsUser.isCmsUser(), nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
            List<Map<String, Object>> enterprises = NuageVspApi.parseJsonString(NuageVspEntity.ENTERPRISE, enterpriseJson);
            if (enterprises.size() > 0) {
                for (Map<String, Object> enterprise : enterprises) {
                    String enterpriseId = (String)enterprise.get(NuageVspAttribute.ID.getAttributeName());
                    //Get all the domain of the Enterprise and add it to the domainIds list
                    String domainsJson = NuageVspApi.executeRestApi(RequestType.GETRELATED, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                            nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, null,
                            nuageVspAPIParamsAsCmsUser.getRestRelativePath(), nuageVspAPIParamsAsCmsUser.getCmsUserInfo(), nuageVspAPIParamsAsCmsUser.getNoofRetry(),
                            nuageVspAPIParamsAsCmsUser.getRetryInterval(), nuageVspAPIParamsAsCmsUser.isCmsUser(), nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
                    List<Map<String, Object>> domains = NuageVspApi.parseJsonString(NuageVspEntity.DOMAIN, domainsJson);
                    for (Map<String, Object> domain : domains) {
                        String domainId = (String)domain.get(NuageVspAttribute.ID.getAttributeName());
                        for (Map<String, Object> floatingIpSubnet : floatingIpSubnets) {
                            String floatingIpSubnetId = (String)floatingIpSubnet.get(NuageVspAttribute.ID.getAttributeName());
                            if (!activeFIPSubnets.contains(floatingIpSubnetId)) {
                                //Get all then domains in the enterprise and check if any of the domain holds an FloatingIp that is derived from
                                //the floatingIpSubnetId
                                String floatingIpOnDomainJson = NuageVspApi.executeRestApi(RequestType.GETRELATED, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                                        nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.DOMAIN, domainId, NuageVspEntity.FLOATING_IP, null,
                                        nuageVspAPIParamsAsCmsUser.getRestRelativePath(), nuageVspAPIParamsAsCmsUser.getCmsUserInfo(), nuageVspAPIParamsAsCmsUser.getNoofRetry(),
                                        nuageVspAPIParamsAsCmsUser.getRetryInterval(), nuageVspAPIParamsAsCmsUser.isCmsUser(), nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
                                List<Map<String, Object>> floatingIpOnDomain = NuageVspApi.parseJsonString(NuageVspEntity.FLOATING_IP, floatingIpOnDomainJson);
                                for (Map<String, Object> floatingIp : floatingIpOnDomain) {
                                    String sharedResourceNetworkId = (String)floatingIp.get(NuageVspAttribute.FLOATING_IP_ASSOC_SHARED_NTWK_ID.getAttributeName());
                                    if (org.apache.commons.lang.StringUtils.equals(sharedResourceNetworkId, floatingIpSubnetId)) {
                                        activeFIPSubnets.add(floatingIpSubnetId);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (Map<String, Object> floatingIpSubnet : floatingIpSubnets) {
                String floatingIpSubnetId = (String)floatingIpSubnet.get(NuageVspAttribute.ID.getAttributeName());
                if (!activeFIPSubnets.contains(floatingIpSubnetId)) {
                    //Delete the floating IP that is not active any more
                    NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.SHARED_NETWORK, floatingIpSubnetId, nuageVspAPIParamsAsCmsUser);
                }
            }
        } else {
            s_logger.debug("No FloatingIP subnet are present in VSP. So, there is nothing to cleanup");
        }
    }

    private void cleanUpMacro(NuageVspAPIParams nuageVspAPIParamsAsCmsUser) throws Exception {
        //Get all the Enterprise and get the NetworkMacro associated to it
        String enterpriseJson = NuageVspApi.executeRestApi(RequestType.GETALL, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.ENTERPRISE, null, nuageVspAPIParamsAsCmsUser.getRestRelativePath(),
                nuageVspAPIParamsAsCmsUser.getCmsUserInfo(), nuageVspAPIParamsAsCmsUser.getNoofRetry(), nuageVspAPIParamsAsCmsUser.getRetryInterval(),
                nuageVspAPIParamsAsCmsUser.isCmsUser(), nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
        List<Map<String, Object>> enterprises = NuageVspApi.parseJsonString(NuageVspEntity.ENTERPRISE, enterpriseJson);
        if (enterprises.size() > 0) {
            //get all the NetworkMacros associated to the network
            List<String> activeNetwrkMacroIds = new ArrayList<String>();
            for (Map<String, Object> enterprise : enterprises) {
                String enterpriseId = (String)enterprise.get(NuageVspAttribute.ID.getAttributeName());
                //Get all the domain of the Enterprise and add it to the domainIds list
                String networkMacrosJson = NuageVspApi.executeRestApi(RequestType.GETRELATED, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                        nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.ENTERPRISE_NTWK_MACRO, null,
                        nuageVspAPIParamsAsCmsUser.getRestRelativePath(), nuageVspAPIParamsAsCmsUser.getCmsUserInfo(), nuageVspAPIParamsAsCmsUser.getNoofRetry(),
                        nuageVspAPIParamsAsCmsUser.getRetryInterval(), nuageVspAPIParamsAsCmsUser.isCmsUser(), nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
                List<Map<String, Object>> networkMacros = NuageVspApi.parseJsonString(NuageVspEntity.ENTERPRISE_NTWK_MACRO, networkMacrosJson);
                if (networkMacros.size() > 0) {
                    //Get all the domain of the Enterprise and add it to the domainIds list
                    String domainsJson = NuageVspApi.executeRestApi(RequestType.GETRELATED, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                            nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, null,
                            nuageVspAPIParamsAsCmsUser.getRestRelativePath(), nuageVspAPIParamsAsCmsUser.getCmsUserInfo(), nuageVspAPIParamsAsCmsUser.getNoofRetry(),
                            nuageVspAPIParamsAsCmsUser.getRetryInterval(), nuageVspAPIParamsAsCmsUser.isCmsUser(), nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
                    List<Map<String, Object>> domains = NuageVspApi.parseJsonString(NuageVspEntity.DOMAIN, domainsJson);
                    for (Map<String, Object> domain : domains) {
                        String domainId = (String)domain.get(NuageVspAttribute.ID.getAttributeName());
                        String ingressACLTemplateJson = NuageVspApi.executeRestApi(RequestType.GETRELATED, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                                nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.DOMAIN, domainId, NuageVspEntity.INGRESS_ACLTEMPLATES, null,
                                nuageVspAPIParamsAsCmsUser.getRestRelativePath(), nuageVspAPIParamsAsCmsUser.getCmsUserInfo(), nuageVspAPIParamsAsCmsUser.getNoofRetry(),
                                nuageVspAPIParamsAsCmsUser.getRetryInterval(), nuageVspAPIParamsAsCmsUser.isCmsUser(), nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
                        List<Map<String, Object>> ingressACLTemplates = NuageVspApi.parseJsonString(NuageVspEntity.INGRESS_ACLTEMPLATES, ingressACLTemplateJson);
                        for (Map<String, Object> ingressACLTemplate : ingressACLTemplates) {
                            String ingressACLTemplateId = (String)ingressACLTemplate.get(NuageVspAttribute.ID.getAttributeName());
                            for (Map<String, Object> networkMacro : networkMacros) {
                                String networkMacroId = (String)networkMacro.get(NuageVspAttribute.ID.getAttributeName());
                                if (!activeNetwrkMacroIds.contains(networkMacroId)) {
                                    String ingressAclEntryTemp = NuageVspApi.executeRestApi(RequestType.GETRELATED, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                                            nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.INGRESS_ACLTEMPLATES, ingressACLTemplateId,
                                            NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_NETWORK_ID.getAttributeName() + " == " + "'"
                                                    + networkMacroId + "'", nuageVspAPIParamsAsCmsUser.getRestRelativePath(), nuageVspAPIParamsAsCmsUser.getCmsUserInfo(),
                                            nuageVspAPIParamsAsCmsUser.getNoofRetry(), nuageVspAPIParamsAsCmsUser.getRetryInterval(), nuageVspAPIParamsAsCmsUser.isCmsUser(),
                                            nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
                                    if (StringUtils.isNotBlank(ingressAclEntryTemp)) {
                                        activeNetwrkMacroIds.add(networkMacroId);
                                    }
                                }
                            }
                        }
                        String egressACLTemplateJson = NuageVspApi.executeRestApi(RequestType.GETRELATED, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                                nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.DOMAIN, domainId, NuageVspEntity.EGRESS_ACLTEMPLATES, null,
                                nuageVspAPIParamsAsCmsUser.getRestRelativePath(), nuageVspAPIParamsAsCmsUser.getCmsUserInfo(), nuageVspAPIParamsAsCmsUser.getNoofRetry(),
                                nuageVspAPIParamsAsCmsUser.getRetryInterval(), nuageVspAPIParamsAsCmsUser.isCmsUser(), nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
                        List<Map<String, Object>> egressACLTemplates = NuageVspApi.parseJsonString(NuageVspEntity.EGRESS_ACLTEMPLATES, egressACLTemplateJson);
                        for (Map<String, Object> egressACLTemplate : egressACLTemplates) {
                            String egressACLTemplateId = (String)egressACLTemplate.get(NuageVspAttribute.ID.getAttributeName());
                            for (Map<String, Object> networkMacro : networkMacros) {
                                String networkMacroId = (String)networkMacro.get(NuageVspAttribute.ID.getAttributeName());
                                if (!activeNetwrkMacroIds.contains(networkMacroId)) {
                                    String egressAclEntryTemp = NuageVspApi.executeRestApi(RequestType.GETRELATED, nuageVspAPIParamsAsCmsUser.getCloudstackDomainName(),
                                            nuageVspAPIParamsAsCmsUser.getCurrentUserName(), NuageVspEntity.EGRESS_ACLTEMPLATES, egressACLTemplateId,
                                            NuageVspEntity.EGRESS_ACLTEMPLATES_ENTRIES, NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_NETWORK_ID.getAttributeName() + " == " + "'"
                                                    + networkMacroId + "'", nuageVspAPIParamsAsCmsUser.getRestRelativePath(), nuageVspAPIParamsAsCmsUser.getCmsUserInfo(),
                                            nuageVspAPIParamsAsCmsUser.getNoofRetry(), nuageVspAPIParamsAsCmsUser.getRetryInterval(), nuageVspAPIParamsAsCmsUser.isCmsUser(),
                                            nuageVspAPIParamsAsCmsUser.getNuageVspCmsId());
                                    if (StringUtils.isNotBlank(egressAclEntryTemp)) {
                                        activeNetwrkMacroIds.add(networkMacroId);
                                    }
                                }
                            }
                        }
                    }
                    for (Map<String, Object> networkMacro : networkMacros) {
                        String networkMacroId = (String)networkMacro.get(NuageVspAttribute.ID.getAttributeName());
                        if (!activeNetwrkMacroIds.contains(networkMacroId)) {
                            //Delete the floating IP that is not active any more
                            NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.ENTERPRISE_NTWK_MACRO, networkMacroId, nuageVspAPIParamsAsCmsUser);
                        }
                    }
                } else {
                    s_logger.debug("There are no network Macros associated with the Enterprise " + enterprise.get(NuageVspAttribute.ENTERPRISE_NAME)
                            + ". So, there is nothing to cleanup");
                }
            }
        }
    }

    private void cleanUpUserAndGroup(NuageVspAPIParams nuageVspAPIParamsAsCmsUser) {

    }

    private void cleanUpEnterprise(NuageVspAPIParams nuageVspAPIParamsAsCmsUser) {

    }
}
