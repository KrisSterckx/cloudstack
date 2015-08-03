package net.nuage.vsp.client.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import com.cloud.utils.Pair;
import net.nuage.vsp.client.common.RequestType;
import net.nuage.vsp.client.common.model.ACLRule;
import net.nuage.vsp.client.common.model.ACLRule.ACLAction;
import net.nuage.vsp.client.common.model.ACLRule.ACLType;
import net.nuage.vsp.client.common.model.NuageVspAPIParams;
import net.nuage.vsp.client.common.model.NuageVspAttribute;
import net.nuage.vsp.client.common.model.NuageVspEntity;
import net.nuage.vsp.client.exception.NuageVspAPIUtilException;
import net.nuage.vsp.client.exception.NuageVspException;

import org.apache.cloudstack.api.InternalIdentity;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.amazonaws.util.json.JSONArray;
import com.cloud.host.HostVO;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.vpc.NetworkACLItem;
import com.cloud.network.vpc.NetworkACLItem.Action;
import com.cloud.network.vpc.NetworkACLItemVO;
import com.cloud.utils.net.NetUtils;

public class NuageVspApiUtil {

    private static final Logger s_logger = Logger.getLogger(NuageVspApiUtil.class);

    private static Configuration configProps = null;
    static {
        try {
            configProps = new PropertiesConfiguration("META-INF/cloudstack/vsp/" + NuageVspConstants.VSP_DEFAULT_PROPERTIES);
        } catch (ConfigurationException e) {
            s_logger.warn("Failed to lead " + NuageVspConstants.VSP_DEFAULT_PROPERTIES + " file. Default values" + " will be used to create VSP entities", e);
        }
    }

    public static String createVMInVSP(String vmInstanceName, String vmUuid, List<Map<String, String>> vmInterfaceList, Object[] attachedNetworkDetails,
            NuageVspAPIParams nuageVspAPIParamsAsCmsUser, NuageVspAPIParams nuageVspAPIParamsAsAccUser) throws NuageVspAPIUtilException {

        s_logger.debug("VM with UUID " + vmUuid + " does not exist in VSP. So, just add the new VM");

        createVportInVsp(vmInstanceName, vmUuid, vmInterfaceList, attachedNetworkDetails, nuageVspAPIParamsAsCmsUser);

        Map<String, Object> vmEntity = new HashMap<String, Object>();
        vmEntity.put(NuageVspAttribute.VM_NAME.getAttributeName(), vmInstanceName);
        vmEntity.put(NuageVspAttribute.VM_UUID.getAttributeName(), vmUuid);
        vmEntity.put(NuageVspAttribute.VM_INTERFACES.getAttributeName(), vmInterfaceList);
        vmEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), vmUuid);

        try {
            String vmJsonString = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParamsAsAccUser.getCloudstackDomainName(),
                    nuageVspAPIParamsAsAccUser.getCurrentUserName(), NuageVspEntity.VM, vmEntity, nuageVspAPIParamsAsAccUser.getRestRelativePath(),
                    nuageVspAPIParamsAsAccUser.getCmsUserInfo(), nuageVspAPIParamsAsAccUser.getNoofRetry(), nuageVspAPIParamsAsAccUser.getRetryInterval(),
                    nuageVspAPIParamsAsAccUser.isCmsUser(), nuageVspAPIParamsAsAccUser.getNuageVspCmsId());
            List<Map<String, Object>> vmDetails = NuageVspApi.parseJsonString(NuageVspEntity.VM, vmJsonString);
            s_logger.debug("Created VM in Nuage. Response from VSP is " + vmJsonString);
            return (String)vmDetails.iterator().next().get(NuageVspAttribute.VM_INTERFACES.getAttributeName());
        } catch (Exception e) {
            String errorMessage = "Failed to create VM in VSP using REST API. Json response from VSP REST API is  " + e.getMessage();
            s_logger.error(errorMessage, e);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static String addVMInterfaceToVM(String networkUuid, String vmInstanceName, String vmUuid, List<Map<String, String>> vmInterfaceList, String macAddress,
            String vmJsonString, Object[] attachedNetworkDetails, NuageVspAPIParams nuageVspAPIParamsAsCmsUser, NuageVspAPIParams nuageVspAPIParamsAsAccUser)
            throws NuageVspAPIUtilException {

        s_logger.debug("VM with UUID " + vmUuid + " already exists in VSP. So, just add the VM new VM interface");

        List<Map<String, Object>> vmDetails = parseJson(vmJsonString, NuageVspEntity.VM);
        String vmId = (String)vmDetails.iterator().next().get(NuageVspAttribute.ID.getAttributeName());
        String vmInterfacesFromNuageVSP = (String)vmDetails.iterator().next().get(NuageVspAttribute.VM_INTERFACES.getAttributeName());
        if (!isVMInterfacePresent(vmInterfacesFromNuageVSP, macAddress)) {
            try {
                createVportInVsp(vmInstanceName, vmUuid, vmInterfaceList, attachedNetworkDetails, nuageVspAPIParamsAsCmsUser);
                String vmInterface = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParamsAsAccUser.getCloudstackDomainName(),
                        nuageVspAPIParamsAsAccUser.getCurrentUserName(), NuageVspEntity.VM, vmId, NuageVspEntity.VM_INTERFACE, vmInterfaceList.iterator().next(), null,
                        nuageVspAPIParamsAsAccUser.getRestRelativePath(), nuageVspAPIParamsAsAccUser.getCmsUserInfo(), nuageVspAPIParamsAsAccUser.getNoofRetry(),
                        nuageVspAPIParamsAsAccUser.getRetryInterval(), true, nuageVspAPIParamsAsAccUser.isCmsUser(), nuageVspAPIParamsAsAccUser.getNuageVspCmsId());
                s_logger.debug("Added VM interface to VM in Nuage. Response from VSP is " + vmJsonString);
                return vmInterface;
            } catch (Exception exception) {
                String errorMessage = "Failed to add VM Interface for the VM with UUID " + vmUuid + " for network " + networkUuid + ".  Json response from VSP REST API is  "
                        + exception.getMessage();
                s_logger.error(errorMessage, exception);
                throw new NuageVspAPIUtilException(errorMessage);
            }
        }

        return null;
    }

    public static void deleteVmInterface(String vmUuid, String macAddr, String vmInterfaceID, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        try {
            NuageVspApi.executeRestApi(RequestType.DELETE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), NuageVspEntity.VM_INTERFACE,
                    vmInterfaceID, null, null, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                    nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
            s_logger.debug("VM Interface is getting destroyed for VM with UUID " + vmUuid + " and it exists in NuageVSP. Deleted the VM interface " + vmInterfaceID
                    + " from Nuage VSP");
        } catch (Exception exception) {
            String errorMessage = "Failed to delete VM Interface with MAC " + macAddr + " for the VM with UUID " + vmUuid + " from NUAGE VSP. Json response from VSP REST API is  "
                    + exception.getMessage();
            s_logger.error(errorMessage, exception);
        }
    }

    public static void deleteVM(String vmUuid, NuageVspAPIParams nuageVspAPIParams, String vmId) {
        try {
            NuageVspApi.executeRestApi(RequestType.DELETE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), NuageVspEntity.VM, vmId, null,
                    null, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                    nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
            s_logger.debug("VM " + vmUuid + " is getting destroyed and it exists in NuageVSP. Deleted the VM " + vmId + " from Nuage VSP");
        } catch (Exception exception) {
            s_logger.error(
                    "VM " + vmUuid + " is getting destroyed. REST API failed to delete the VM " + vmId + " from NuageVsp. Json response from REST API is " + exception.getMessage(),
                    exception);
        }
    }

    public static String getVMDetails(String networkUuid, String vmUuid, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        String vmJsonString = null;
        try {
            vmJsonString = NuageVspApi.executeRestApi(RequestType.GETALL, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), NuageVspEntity.VM,
                    NuageVspAttribute.VM_UUID.getAttributeName() + " == '" + vmUuid + "'", nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(),
                    nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
        } catch (Exception exception) {
            String errorMessage = "Failed to execute REST API call to VSP to get VM with UUID " + vmUuid
                    + ". So, Failed to get IP for the VM from VSP address for network or enterprise " + networkUuid + ".  Json response from VSP REST API is  "
                    + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
        return vmJsonString;
    }

    public static String getOrCreateVSPEnterprise(String domainUuid, String domainName, String domainPath,
                                                  NuageVspAPIParams nuageVspAPIParamsAsCmsUser) throws NuageVspAPIUtilException {
        String enterpriseId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, null, null, domainUuid, nuageVspAPIParamsAsCmsUser);
        if (StringUtils.isBlank(enterpriseId)) {
            //Create a Enterprise corresponding to networksDomain
            enterpriseId = createEnterpriseInVSP(domainUuid, getEnterpriseName(domainName, domainPath), nuageVspAPIParamsAsCmsUser);
        }
        return enterpriseId;
    }

    public static String[] getOrCreateVSPEnterpriseAndGroup(String networksDomainName, String networksDomainPath, String networksDomainUuid, String networksAccountName,
            String networksAccountUuid, NuageVspAPIParams nuageVspAPIParamsAsCmsUser) throws NuageVspAPIUtilException {
        String enterpriseId = getOrCreateVSPEnterprise(networksDomainUuid, networksDomainName, networksDomainPath, nuageVspAPIParamsAsCmsUser);

        //Check if user exists. If no then create an user under enterprise
        String userId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.USER, networksAccountUuid, nuageVspAPIParamsAsCmsUser);
        String userGroupId = null;
        if (StringUtils.isBlank(userId)) {
            userId = createUserInEnterprise(enterpriseId, networksAccountUuid, NuageVspConstants.USER_FIRST_NAME + "_" + networksAccountUuid, NuageVspConstants.USER_LAST_NAME
                    + "_" + networksAccountUuid, NuageVspConstants.USER_EMAIL, DigestUtils.shaHex(NuageVspConstants.USER_PASSWORD), nuageVspAPIParamsAsCmsUser);
        }

        userGroupId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.GROUP, networksAccountUuid, nuageVspAPIParamsAsCmsUser);
        if (StringUtils.isBlank(userGroupId)) {
            //Create a Group and User that corresponds to networkAccount
            userGroupId = createGroupInEnterprise(networksAccountName, networksAccountUuid, enterpriseId, true, nuageVspAPIParamsAsCmsUser);
        }
        //Add the user in to group
        JSONArray jsonArray = new JSONArray();
        jsonArray.put(userId);
        addUsersInGroup(userGroupId, jsonArray, nuageVspAPIParamsAsCmsUser);

        return new String[] {enterpriseId, userGroupId};
    }

    private static String getOrCreatePublicMacroInEnterprise(String vsdEnterpriseId, String sourceCidr, String ruleUuid, NuageVspAPIParams nuageVspAPIParams)
            throws NuageVspAPIUtilException {
        try {
            String netmask = "";
            String subnet = NetUtils.getCidrSubNet(sourceCidr);
            long cidrSize = new Long(sourceCidr.substring(sourceCidr.indexOf("/") + 1));
            if (cidrSize == 0) {
                netmask = "0.0.0.0";
            } else {
                netmask = NetUtils.getCidrNetmask(sourceCidr);
            }

            //check to find is the Public Macro exists in enterprise
            String macroJsonString = findEntityUsingFilter(
                    NuageVspEntity.ENTERPRISE,
                    vsdEnterpriseId,
                    NuageVspEntity.ENTERPRISE_NTWK_MACRO,
                    NuageVspAttribute.ENTERPRISE_NTWK_MACRO_ADDRESS.getAttributeName() + " == '" + subnet + "'" + " and "
                            + NuageVspAttribute.ENTERPRISE_NTWK_MACRO_NETMASK.getAttributeName() + " == '" + netmask + "'", nuageVspAPIParams);

            if (StringUtils.isBlank(macroJsonString)) {
                try {
                    //create a new public network macro with the given netmask and address
                    Map<String, Object> macroEntity = new HashMap<String, Object>();
                    macroEntity.put(NuageVspAttribute.ENTERPRISE_NTWK_MACRO_NAME.getAttributeName(), ruleUuid);
                    macroEntity.put(NuageVspAttribute.ENTERPRISE_NTWK_MACRO_ADDRESS.getAttributeName(), subnet);
                    macroEntity.put(NuageVspAttribute.ENTERPRISE_NTWK_MACRO_NETMASK.getAttributeName(), netmask);
                    String macroJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                            NuageVspEntity.ENTERPRISE, vsdEnterpriseId, NuageVspEntity.ENTERPRISE_NTWK_MACRO, macroEntity, null, nuageVspAPIParams.getRestRelativePath(),
                            nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), true, nuageVspAPIParams.isCmsUser(),
                            nuageVspAPIParams.getNuageVspCmsId());
                    s_logger.debug("Created Enterprise Network Macro in VSP. Response from VSP is " + macroJson);
                    return getEntityId(macroJson, NuageVspEntity.ENTERPRISE_NTWK_MACRO);
                } catch (Exception exception) {
                    String errorMessage = "Failed to create Enterprise network macro " + sourceCidr + " in VSP enterprise " + vsdEnterpriseId
                            + ".  Json response from VSP REST API is  " + exception.getMessage();
                    s_logger.error(errorMessage, exception);
                    throw new NuageVspAPIUtilException(errorMessage);
                }
            }
            //If Macro already exists then just return the macroId
            return getEntityId(macroJsonString, NuageVspEntity.ENTERPRISE_NTWK_MACRO);
        } catch (Exception exception) {
            String errorMessage = "Failed to read Public network macro " + sourceCidr + " in VSP enterprise " + vsdEnterpriseId + ".  Json response from VSP REST API is  "
                    + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static String createEnterpriseInVSP(String enterpriseExternalUuid, String enterpriseDescription, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {

        s_logger.debug("Enterprise with domainUuid " + enterpriseDescription + " does not exist in VSP. So, just create the new Enterprise");

        String enterpriseProfileId = createEnterpriseProfileInVsp(enterpriseExternalUuid, enterpriseDescription, nuageVspAPIParams);

        Map<String, Object> enterpriseEntity = new HashMap<String, Object>();
        enterpriseEntity.put(NuageVspAttribute.ENTERPRISE_NAME.getAttributeName(), enterpriseExternalUuid);
        enterpriseEntity.put(NuageVspAttribute.ENTERPRISE_DESCRIPTION.getAttributeName(), enterpriseDescription);
        enterpriseEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), enterpriseExternalUuid);
        enterpriseEntity.put(NuageVspAttribute.ENTERPRISE_PROFILE_ID.getAttributeName(), enterpriseProfileId);

        try {
            String enterpriseJsonString = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                    NuageVspEntity.ENTERPRISE, enterpriseEntity, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                    nuageVspAPIParams.getRetryInterval(), nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
            return getEntityId(enterpriseJsonString, NuageVspEntity.ENTERPRISE);
        } catch (Exception e) {
            String errorMessage = "Failed to create Enterprise in VSP using REST API. So, Enterprise could not be created in VSP " + " for domain " + enterpriseExternalUuid
                    + ".  Json response from VSP REST API is  " + e.getMessage();
            s_logger.error(errorMessage, e);
            //clean the enterprise profile id
            cleanUpVspStaleObjects(NuageVspEntity.ENTERPRISE_PROFILE, enterpriseProfileId, nuageVspAPIParams);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    private static String createEnterpriseProfileInVsp(String enterpriseExternalUuid, String enterpriseDescription, NuageVspAPIParams nuageVspAPIParams)
            throws NuageVspAPIUtilException {
        //Create the enterprise profile and then associate the profile to the enterprise
        String enterpriseProfileId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE_PROFILE, null, null, enterpriseExternalUuid, nuageVspAPIParams);
        if (StringUtils.isBlank(enterpriseProfileId)) {
            Map<String, Object> enterpriseProfileEntity = new HashMap<String, Object>();
            enterpriseProfileEntity.put(NuageVspAttribute.ENTERPRISE_PROFILE_NAME.getAttributeName(), enterpriseExternalUuid);
            enterpriseProfileEntity.put(NuageVspAttribute.ENTERPRISE_PROFILE_DESCRIPTION.getAttributeName(), enterpriseDescription);
            enterpriseProfileEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), enterpriseExternalUuid);
            Integer floatingIpQuota = null;
            Boolean allowGatewayMgmt = null;
            Boolean allowAdvancedQOS = null;
            String availableFwdClass = null;
            if (configProps != null) {
                try {
                    floatingIpQuota = configProps.getInt("floatingIPQuota", NuageVspConstants.ENTERPRISE_PROFILE_FLOATING_IP_QUOTA);
                    allowGatewayMgmt = configProps.getBoolean("allowGatewayMgmt", NuageVspConstants.ENTERPRISE_PROFILE_GATEWAY_MGMT);
                    allowAdvancedQOS = configProps.getBoolean("allowAdvancedQOS", NuageVspConstants.ENTERPRISE_PROFILE_ADV_QOS);
                    availableFwdClass = configProps.getString("availableFwdClass", NuageVspConstants.ENTERPRISE_PROFILE_FWD_CLASSES);
                } catch (Exception e) {
                    s_logger.error("vsp-default.properties file has invalid values. Please specify proper value and retry", e);
                }
            } else {
                floatingIpQuota = NuageVspConstants.ENTERPRISE_PROFILE_FLOATING_IP_QUOTA;
                allowGatewayMgmt = NuageVspConstants.ENTERPRISE_PROFILE_GATEWAY_MGMT;
                allowAdvancedQOS = NuageVspConstants.ENTERPRISE_PROFILE_ADV_QOS;
                availableFwdClass = NuageVspConstants.ENTERPRISE_PROFILE_FWD_CLASSES;
            }
            enterpriseProfileEntity.put(NuageVspAttribute.ENTERPRISE_PROFILE_ADV_QOS.getAttributeName(), allowAdvancedQOS);
            enterpriseProfileEntity.put(NuageVspAttribute.ENTERPRISE_PROFILE_FLOATING_IP_QUOTA.getAttributeName(), floatingIpQuota);
            enterpriseProfileEntity.put(NuageVspAttribute.ENTERPRISE_PROFILE_GATEWAY_MGMT.getAttributeName(), allowGatewayMgmt);
            JSONArray fwdClassJsonArray = new JSONArray();
            for (String fwdClass : availableFwdClass.split(";")) {
                fwdClassJsonArray.put(fwdClass);
            }
            enterpriseProfileEntity.put(NuageVspAttribute.ENTERPRISE_PROFILE_FWD_CLASSES.getAttributeName(), fwdClassJsonArray);

            try {
                String enterpriseJsonString = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                        NuageVspEntity.ENTERPRISE_PROFILE, enterpriseProfileEntity, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(),
                        nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                return getEntityId(enterpriseJsonString, NuageVspEntity.ENTERPRISE_PROFILE);
            } catch (Exception e) {
                String errorMessage = "Failed to create Enterprise Profile in VSP using REST API. So, Enterprise could not be created in VSP " + " for domain "
                        + enterpriseExternalUuid + ".  Json response from VSP REST API is  " + e.getMessage();
                s_logger.error(errorMessage, e);
                throw new NuageVspAPIUtilException(errorMessage);
            }
        }
        return enterpriseProfileId;
    }

    public static void deleteEnterpriseInVsp(String enterpriseExternalUuid, String enterpriseDescription, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        try {
            String enterpriseId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, null, null, enterpriseExternalUuid, nuageVspAPIParams);
            if (StringUtils.isNotBlank(enterpriseId)) {
                NuageVspApi.executeRestApi(RequestType.DELETE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), NuageVspEntity.ENTERPRISE,
                        enterpriseId, null, null, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                        nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                s_logger.debug("Enterprise " + enterpriseDescription + " is getting removed and it exists in NuageVSP. Deleted the enterprise " + enterpriseId + " from Nuage VSP");

                deleteEnterpriseProfileInVsp(enterpriseExternalUuid, enterpriseDescription, nuageVspAPIParams);
            }
        } catch (Exception e) {
            String errorMessage = "Failed to delete Enterprise in VPS using REST API. Json response from VSP REST API is "  + e.getMessage();
            s_logger.error(errorMessage, e);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    private static void deleteEnterpriseProfileInVsp(String enterpriseExternalUuid, String enterpriseDescription, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        try {
            String enterpriseProfileId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE_PROFILE, null, null, enterpriseExternalUuid, nuageVspAPIParams);
            if (StringUtils.isNotBlank(enterpriseProfileId)) {
                NuageVspApi.executeRestApi(RequestType.DELETE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), NuageVspEntity.ENTERPRISE_PROFILE,
                        enterpriseProfileId, null, null, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                        nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                s_logger.debug("Enterprise Profile " + enterpriseDescription + " is getting removed and it exists in NuageVSP. " +
                        "Deleted the enterprise profile " + enterpriseProfileId + " from Nuage VSP");
            }
        } catch (Exception e) {
            String errorMessage = "Failed to delete Enterprise Profile in VPS using REST API. Json response from VSP REST API is "  + e.getMessage();
            s_logger.error(errorMessage, e);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static String createUserInEnterprise(String vsdEnterpriseId, String userNameUuid, String firstName, String lastName, String email, String password,
            NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        try {
            //create User
            Map<String, Object> groupEntity = new HashMap<String, Object>();
            groupEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), userNameUuid);
            //Hack to make the user name less than 32 characters...
            groupEntity.put(NuageVspAttribute.USER_USERNAME.getAttributeName(), userNameUuid.replaceAll("-", ""));
            groupEntity.put(NuageVspAttribute.USER_EMAIL.getAttributeName(), email);
            groupEntity.put(NuageVspAttribute.USER_PASSWORD.getAttributeName(), password);
            groupEntity.put(NuageVspAttribute.USER_FIRSTNAME.getAttributeName(), firstName);
            groupEntity.put(NuageVspAttribute.USER_LASTNAME.getAttributeName(), lastName);

            String userJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                    NuageVspEntity.ENTERPRISE, vsdEnterpriseId, NuageVspEntity.USER, groupEntity, null, nuageVspAPIParams.getRestRelativePath(),
                    nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), true, nuageVspAPIParams.isCmsUser(),
                    nuageVspAPIParams.getNuageVspCmsId());
            s_logger.debug("Created user in VSP. Response from VSP is " + userJson);
            return getEntityId(userJson, NuageVspEntity.USER);
        } catch (Exception exception) {
            String errorMessage = "Failed to create User for VSP enterprise " + vsdEnterpriseId + ".  Json response from VSP REST API is  " + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static void addPermission(NuageVspEntity entityType, String entityId, JSONArray groupIds, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        try {
            //Add users to the group
            NuageVspApi.executeRestApi(RequestType.MODIFYRELATED, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), entityType, entityId,
                    NuageVspEntity.GROUP, groupIds, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                    nuageVspAPIParams.getRetryInterval(), true, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
            s_logger.debug("Added permission for entity " + entityType + " id " + entityId + " with groups " + groupIds);
        } catch (Exception exception) {
            String errorMessage = "Failed to add permission for entity " + entityType + " id " + entityId + ".  Json response from VSP REST API is  " + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static String createGroupInEnterprise(String projectOrAccountName, String projectOrAccountUuid, String vsdEnterpriseId, boolean isPrivateGroup,
            NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        try {
            //create group
            Map<String, Object> groupEntity = new HashMap<String, Object>();
            groupEntity.put(NuageVspAttribute.GROUP_NAME.getAttributeName(), projectOrAccountUuid);
            groupEntity.put(NuageVspAttribute.GROUP_DESCRIPTION.getAttributeName(), projectOrAccountName);
            groupEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), projectOrAccountUuid);

            String groupJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                    NuageVspEntity.ENTERPRISE, vsdEnterpriseId, NuageVspEntity.GROUP, groupEntity, null, nuageVspAPIParams.getRestRelativePath(),
                    nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), true, nuageVspAPIParams.isCmsUser(),
                    nuageVspAPIParams.getNuageVspCmsId());
            s_logger.debug("Created group for project or account " + projectOrAccountUuid + " in VSP . Response from VSP is " + groupJson);
            return getEntityId(groupJson, NuageVspEntity.GROUP);
        } catch (Exception exception) {
            String errorMessage = "Failed to create Group for project or account " + projectOrAccountUuid + ".  Json response from VSP REST API is  " + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static void addUsersInGroup(String vsdGroupId, JSONArray userIds, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        try {
            //Add users to the group
            NuageVspApi.executeRestApi(RequestType.MODIFYRELATED, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), NuageVspEntity.GROUP,
                    vsdGroupId, NuageVspEntity.USER, userIds, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                    nuageVspAPIParams.getRetryInterval(), true, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
        } catch (Exception exception) {
            String errorMessage = "Failed to add Users for project or account in VSD " + vsdGroupId + ".  Json response from VSP REST API is  " + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static Pair<String, String> createSharedNetworkWithDefaultACLs(String domainUuid, String enterpriseId, String networkName, String netmask, String address, String gateway,
            Long networkAclId, List<String> dnsServers, List<String> gatewaySystemIds, Collection<String[]> ipAddressRanges, boolean defaultCSEgressPolicy, String networkUuid,
            JSONArray groupId, NuageVspAPIParams nuageVspAPIParams, String preConfiguredDomainTemplateName) throws NuageVspAPIUtilException {
        s_logger.debug("Create or find a subnet associated to shared network " + networkName + " in VSP");
        return createNetworkConfigurationWithDefaultACLS(true, false, domainUuid, networkName, enterpriseId, networkName, netmask, address, gateway, networkAclId, dnsServers,
                gatewaySystemIds, ipAddressRanges, defaultCSEgressPolicy, networkUuid, groupId, nuageVspAPIParams, preConfiguredDomainTemplateName);
    }

    public static Pair<String, String> createIsolatedL3NetworkWithDefaultACLs(String enterpriseId, String networkName, long networkId, String netmask, String address, String gateway,
            Long networkAclId, List<String> dnsServers, List<String> gatewaySystemIds, Collection<String[]> ipAddressRanges, boolean defaultCSEgressPolicy, String networkUuid,
            JSONArray groupId, NuageVspAPIParams nuageVspAPIParams, String preConfiguredDomainTemplateName) throws NuageVspAPIUtilException {
        return createVPCOrL3NetworkWithDefaultACLs(enterpriseId, networkName, networkId, netmask, address, gateway, networkAclId, dnsServers, gatewaySystemIds, ipAddressRanges,
                defaultCSEgressPolicy, networkUuid, groupId, nuageVspAPIParams, null, null, preConfiguredDomainTemplateName);
    }

    public static Pair<String, String> createVPCOrL3NetworkWithDefaultACLs(String enterpriseId, String networkName, long networkId, String netmask, String address, String gateway,
            Long networkAclId, List<String> dnsServers, Collection<String> gatewaySystemIds, Collection<String[]> ipAddressRanges, boolean defaultCSEgressPolicy, String networkUuid,
            JSONArray groupId, NuageVspAPIParams nuageVspAPIParams, String vpcName, String vpcUuid, String preConfiguredDomainTemplateName) throws NuageVspAPIUtilException {

        s_logger.debug("Create or find a VPC/Isolated network associated to network " + networkName + " in VSP");
        boolean isVpc = StringUtils.isNotBlank(vpcName);
        String vpcOrSubnetUuid;
        String vpcOrSubnetName;
        if (isVpc) {
            vpcOrSubnetUuid = vpcUuid;
            vpcOrSubnetName = "VPC_" + vpcName;
        } else {
            vpcOrSubnetUuid = networkUuid;
            vpcOrSubnetName = networkName;
        }

        return createNetworkConfigurationWithDefaultACLS(isVpc, isVpc, vpcOrSubnetUuid, vpcOrSubnetName, enterpriseId, networkName, netmask, address, gateway, networkAclId, dnsServers,
                gatewaySystemIds, ipAddressRanges, defaultCSEgressPolicy, networkUuid, groupId, nuageVspAPIParams, preConfiguredDomainTemplateName);
    }

    private static Pair<String, String> createNetworkConfigurationWithDefaultACLS(boolean reuseDomain, boolean isVpc, String uuid, String name, String enterpriseId, String networkName, String netmask,
              String address, String gateway, Long networkAclId, List<String> dnsServers, Collection<String> gatewaySystemIds, Collection<String[]> ipAddressRanges,
              boolean defaultCSEgressPolicy, String networkUuid, JSONArray groupId, NuageVspAPIParams nuageVspAPIParams,
              String preConfiguredDomainTemplateName) throws NuageVspAPIUtilException {
        String domainTemplateId = null;
        String domainId = null;
        String subnetId = null;
        StringBuffer errorMessage = new StringBuffer();
        String debugMessage = "This is a " + (reuseDomain ? (isVpc ? "VPC" : "Shared") : "Isolated") + " Network.";

        String vsdDomainTemplateEntity = NuageVspApiUtil.findEntityUsingFilter(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN_TEMPLATE,
                "name", preConfiguredDomainTemplateName, nuageVspAPIParams);
        String vsdDomainTemplateId = NuageVspApiUtil.getEntityId(vsdDomainTemplateEntity, NuageVspEntity.DOMAIN_TEMPLATE);
        String vsdDomainTemplateName = NuageVspApiUtil.getFieldValue(vsdDomainTemplateEntity, NuageVspEntity.DOMAIN_TEMPLATE, NuageVspAttribute.DOMAIN_TEMPLATE_NAME.getAttributeName());

        if (StringUtils.isNotBlank(preConfiguredDomainTemplateName) &&
                (StringUtils.isBlank(vsdDomainTemplateId) || !StringUtils.equals(vsdDomainTemplateName, preConfiguredDomainTemplateName))) {
            errorMessage.append(debugMessage).append(" Preconfigured DomainTemplate '").append(preConfiguredDomainTemplateName).append("' could not be found.");
            if (isVpc) {
                errorMessage.append(" Please remove the VPC Tier before trying again.");
            }
            s_logger.error(errorMessage);
            throw new NuageVspAPIUtilException(errorMessage.toString());
        }

        boolean predefinedDomainTemplateSet = StringUtils.isNotBlank(vsdDomainTemplateId);
        boolean setDefaultAcls = !predefinedDomainTemplateSet; // don't set defaults if domain template set

        if (predefinedDomainTemplateSet) {
            domainId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, uuid, nuageVspAPIParams);
            if (StringUtils.isNotBlank(domainId)) {
                subnetId = validateDomain(reuseDomain, errorMessage, debugMessage, domainId, networkUuid, networkName, uuid, netmask, address, gateway, dnsServers,
                        ipAddressRanges, nuageVspAPIParams);
            } else {
                Pair<String, String> domainAndSubnetId = createDomainZoneAndSubnet(reuseDomain, vsdDomainTemplateId, networkName, uuid, name, gatewaySystemIds, setDefaultAcls,
                        defaultCSEgressPolicy, groupId, netmask, address, gateway, dnsServers, ipAddressRanges, networkUuid, enterpriseId, errorMessage, debugMessage, nuageVspAPIParams);
                domainId = domainAndSubnetId.first();
                subnetId = domainAndSubnetId.second();
            }
        } else {
            domainTemplateId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN_TEMPLATE, uuid, nuageVspAPIParams);
            if (StringUtils.isNotBlank(domainTemplateId)) {
                s_logger.debug(debugMessage + " Domain Template " + domainTemplateId + " already exists for network " + networkName + " in VSP");
                domainId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, uuid, nuageVspAPIParams);
                if (StringUtils.isNotBlank(domainId)) {
                    subnetId = validateDomain(reuseDomain, errorMessage, debugMessage, domainId, networkUuid, networkName, uuid,
                            netmask, address, gateway, dnsServers, ipAddressRanges, nuageVspAPIParams);
                } else {
                    errorMessage.append(debugMessage).append(" Domain is not found under the DomainTemplate ").append(domainTemplateId).append(" for network ").append(networkName)
                            .append(" in VSP. There is a network sync issue with VSD");
                }
            } else {

                Map<String, Object> domainTemplateEntity = new HashMap<String, Object>();
                domainTemplateEntity.put(NuageVspAttribute.DOMAIN_TEMPLATE_NAME.getAttributeName(), uuid);
                domainTemplateEntity.put(NuageVspAttribute.DOMAIN_TEMPLATE_DESCRIPTION.getAttributeName(), name);
                domainTemplateEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), uuid);

                try {
                    String domainTemplateJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                            NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN_TEMPLATE, domainTemplateEntity, null, nuageVspAPIParams.getRestRelativePath(),
                            nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(),
                            nuageVspAPIParams.getNuageVspCmsId());
                    s_logger.debug(debugMessage + " Created DomainTemplate for network " + networkName + " in VSP . Response from VSP is " + domainTemplateJson);
                    domainTemplateId = getEntityId(domainTemplateJson, NuageVspEntity.DOMAIN_TEMPLATE);
                } catch (Exception exception) {
                    errorMessage.append(debugMessage).append(" Failed to create DomainTemplate for network ").append(networkName).append(". Json response from VSP REST API is ")
                            .append(exception.getMessage());
                }

                Pair<String, String> domainAndSubnetId = createDomainZoneAndSubnet(reuseDomain, domainTemplateId, networkName, uuid, name, gatewaySystemIds, setDefaultAcls,
                        defaultCSEgressPolicy, groupId, netmask, address, gateway, dnsServers, ipAddressRanges, networkUuid, enterpriseId, errorMessage, debugMessage, nuageVspAPIParams);
                domainId = domainAndSubnetId.first();
                subnetId = domainAndSubnetId.second();
            }
        }

        if (errorMessage.length() != 0) {
            if (isVpc) {
                errorMessage.append(" Please remove the VPC Tier before trying again.");
            }
            s_logger.error(errorMessage);
            cleanUpVspStaleObjects(NuageVspEntity.DOMAIN_TEMPLATE, domainTemplateId, nuageVspAPIParams);
            throw new NuageVspAPIUtilException(errorMessage.toString());
        }
        return new Pair<String, String>(domainId, subnetId);
    }

    private static String validateDomain(boolean reuseDomain, StringBuffer errorMessage, String debugMessage, String domainId, String networkUuid, String networkName, String zoneUuid,
                                       String netmask, String address, String gateway, List<String> dnsServers, Collection<String[]> ipAddressRanges, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        String subnetId = findEntityIdByExternalUuid(NuageVspEntity.DOMAIN, domainId, NuageVspEntity.SUBNET, networkUuid, nuageVspAPIParams);
        if ((!reuseDomain) && StringUtils.isBlank(subnetId)) {
            errorMessage.append(debugMessage).append(" and Subnet is not found under the Domain ").append(domainId).append(" for network ").append(networkName)
                    .append(" in VSP. There is a network sync issue with VSD");
        } else if (reuseDomain) {
            String zoneId = findEntityIdByExternalUuid(NuageVspEntity.DOMAIN, domainId, NuageVspEntity.ZONE, zoneUuid, nuageVspAPIParams);
            if (StringUtils.isBlank(zoneId)) {
                errorMessage.append(debugMessage).append(" and Zone corresponding to network ").append(zoneUuid)
                        .append(" does not exist in VSP. There is a data sync issue. Please a check VSP or create a new network");
            } else {
                if (StringUtils.isBlank(subnetId)) {
                    subnetId = createL3Subnet(networkName, netmask, address, gateway, dnsServers, ipAddressRanges, networkUuid, nuageVspAPIParams, zoneId, subnetId,
                            errorMessage, debugMessage);
                } else {
                    s_logger.debug(debugMessage + " Subnet " + subnetId + " already exists for network " + networkName + " in VSP");
                }
            }
        } else {
            s_logger.debug(debugMessage + " Domain and Subnet " + subnetId + " already exists for network " + networkName + " in VSP");
        }
        return subnetId;
    }

    private static Pair<String, String> createDomainZoneAndSubnet(boolean reuseDomain, String domainTemplateId, String networkName, String vpcOrSubnetUuid,
                                                  String vpcOrSubnetName, Collection<String> gatewaySystemIds, boolean createDefaultAcls, boolean defaultCSEgressPolicy, JSONArray groupId,
                                                  String netmask, String address, String gateway, List<String> dnsServers, Collection<String[]> ipAddressRanges, String networkUuid,
                                                  String enterpriseId, StringBuffer errorMessage, String debugMessage, NuageVspAPIParams nuageVspAPIParams) {

        String domainId = null;
        String ingressAclTemplId = null;
        String egressAclTemplId = null;
        try {
            if (errorMessage.length() == 0) {
                //Now instantiate the domain template
                Map<String, Object> domainEntity = new HashMap<String, Object>();
                domainEntity.put(NuageVspAttribute.DOMAIN_NAME.getAttributeName(), vpcOrSubnetUuid);
                domainEntity.put(NuageVspAttribute.DOMAIN_DESCRIPTION.getAttributeName(), vpcOrSubnetName);
                domainEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), vpcOrSubnetUuid);
                domainEntity.put(NuageVspAttribute.DOMAIN_TEMPLATE_ID.getAttributeName(), domainTemplateId);

                String domainJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                        NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, domainEntity, null, nuageVspAPIParams.getRestRelativePath(),
                        nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(),
                        nuageVspAPIParams.getNuageVspCmsId());
                s_logger.debug(debugMessage + " Created Domain for network " + networkName + " in VSP . Response from VSP is " + domainJson);
                domainId = getEntityId(domainJson, NuageVspEntity.DOMAIN);
            }
        } catch (Exception exception) {
            errorMessage.append(debugMessage).append(" Failed to instantiate DomainTemplate for network ").append(networkName).append(".  Json response from VSP REST API is  ")
                    .append(exception.getMessage());
        }

        //Attach Domain with the Gateway service
        attachDomainWithGatewayService(enterpriseId, networkName, gatewaySystemIds, nuageVspAPIParams, domainId, errorMessage);

        //Create default ingress and egress ACLs
        if (createDefaultAcls) {
            errorMessage = createDefaultIngressAndEgressAcls(reuseDomain, vpcOrSubnetUuid, defaultCSEgressPolicy, NuageVspEntity.DOMAIN, domainId, errorMessage, ingressAclTemplId,
                    new HashMap<Integer, Map<String, Object>>(0), egressAclTemplId, new HashMap<Integer, Map<String, Object>>(0), networkName, nuageVspAPIParams);
        }

        String zoneId = null;
        try {
            if (errorMessage.length() == 0) {
                //Now create the Zone under the domain
                Map<String, Object> zoneEntity = new HashMap<String, Object>();
                zoneEntity.put(NuageVspAttribute.ZONE_NAME.getAttributeName(), NuageVspConstants.ZONE_NAME + "_" + vpcOrSubnetUuid);
                zoneEntity.put(NuageVspAttribute.ZONE_DESCRIPTION.getAttributeName(), vpcOrSubnetName);
                zoneEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), vpcOrSubnetUuid);

                String zoneJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                        NuageVspEntity.DOMAIN, domainId, NuageVspEntity.ZONE, zoneEntity, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(),
                        nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                zoneId = getEntityId(zoneJson, NuageVspEntity.ZONE);
                s_logger.debug(debugMessage + "Created Zone for network " + networkName + " in VSP . Response from VSP is " + zoneJson);
                //set permission to use the Zone
                if (groupId != null) {
                    try {
                        addPermission(NuageVspEntity.ZONE, zoneId, groupId, nuageVspAPIParams);
                    } catch (Exception e) {
                        errorMessage.append(e.getMessage());
                    }
                }
            }
        } catch (Exception exception) {
            errorMessage.append(debugMessage).append(" Failed to create Zone for network ").append(networkName).append(".  Json response from VSP REST API is  ")
                    .append(exception.getMessage());
        }

        String subnetId = createL3Subnet(networkName, netmask, address, gateway, dnsServers, ipAddressRanges, networkUuid, nuageVspAPIParams, zoneId, null, errorMessage,
                debugMessage);
        return new Pair<String, String>(domainId, subnetId);
    }

    private static void attachDomainWithGatewayService(String enterpriseId, String networkName, Collection<String> gatewaySystemIds, NuageVspAPIParams nuageVspAPIParams,
            String domainId, StringBuffer errorMessage) {
        if (gatewaySystemIds != null && gatewaySystemIds.size() > 0) {
            //This is a use case where the Gateway is configured with WAN services
            //Get the Gateway details using serviceID filter.
            try {
                if (errorMessage.length() == 0) {
                    for (String gatewaySystemId : gatewaySystemIds) {
                        String gatewayJson = findEntityUsingFilter(NuageVspEntity.GATEWAY, null, null, NuageVspAttribute.GATEWAY_SYSTEMID.getAttributeName(), gatewaySystemId, nuageVspAPIParams);
                        if (StringUtils.isNotBlank(gatewayJson)) {
                            String gatewayId = getEntityId(gatewayJson, NuageVspEntity.GATEWAY);
                            //Check if the gateway has the enterprisePermission
                            String enterprisePermissionJson = NuageVspApi.executeRestApi(RequestType.GETALL, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), NuageVspEntity.GATEWAY, gatewayId,
                                    NuageVspEntity.ENTERPRISEPERMISSION, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                                    nuageVspAPIParams.getRetryInterval(), nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                            List<Map<String, Object>> permissionDetails = parseJson(enterprisePermissionJson, NuageVspEntity.ENTERPRISEPERMISSION);
                            boolean isPermitted = false;
                            for (Map<String, Object> permission : permissionDetails) {
                                if (permission.get(NuageVspAttribute.ENTERPRISEPERMISSION_PERMITTED_ENTITYID.getAttributeName()).equals(enterpriseId)) {
                                    isPermitted = true;
                                    break;
                                }
                            }
                            if (!isPermitted) {
                                //Add the enterprise permission to the Gateway before using it
                                Map<String, Object> permission = new HashMap<String, Object>();
                                permission.put(NuageVspAttribute.ENTERPRISEPERMISSION_PERMITTED_ENTITYYPE.getAttributeName(), "enterprise");
                                permission.put(NuageVspAttribute.ENTERPRISEPERMISSION_PERMITTED_ENTITYID.getAttributeName(), enterpriseId);
                                permission.put(NuageVspAttribute.ENTERPRISEPERMISSION_PERMITTED_ACTION.getAttributeName(), "USE");
                                NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                                        NuageVspEntity.GATEWAY, gatewayId, NuageVspEntity.ENTERPRISEPERMISSION, permission, null, nuageVspAPIParams.getRestRelativePath(),
                                        nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(),
                                        nuageVspAPIParams.getNuageVspCmsId());
                            }
                            // Then get all the services that is not attached to the gateway
                            String wanServiceJson = NuageVspApi.executeRestApi(RequestType.GETALL, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), NuageVspEntity.GATEWAY, gatewayId,
                                    NuageVspEntity.WAN_SERVICES, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                                    nuageVspAPIParams.getRetryInterval(), nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                            if (StringUtils.isNotBlank(wanServiceJson)) {
                                List<Map<String, Object>> wanServices = parseJson(wanServiceJson, NuageVspEntity.WAN_SERVICES);
                                String availableWanserviceId = null;
                                for (Map<String, Object> wanService : wanServices) {
                                    String vpcConnectId = (String)wanService.get(NuageVspAttribute.WAN_SERVICE_VPN_CONNECT_ID.getAttributeName());
                                    if (vpcConnectId == null) {
                                        availableWanserviceId = (String)wanService.get(NuageVspAttribute.ID.getAttributeName());
                                        break;
                                    }
                                }
                                if (availableWanserviceId != null) {
                                    //Found the WANService that is free. Now now create a VPNConnection for the Domain with this WANService
                                    s_logger.debug("Found a free WAN service " + availableWanserviceId + " and trying to associate with VSP domain with id " + domainId);
                                    Map<String, Object> vpnConnection = new HashMap<String, Object>();
                                    vpnConnection.put(NuageVspAttribute.VPN_CONNECTION_WANSERVICE_NAME.getAttributeName(), availableWanserviceId);
                                    vpnConnection.put(NuageVspAttribute.VPN_CONNECTION_WANSERVICE_ID.getAttributeName(), availableWanserviceId);
                                    NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                                            NuageVspEntity.DOMAIN, domainId, NuageVspEntity.VPN_CONNECTION, vpnConnection, null, nuageVspAPIParams.getRestRelativePath(),
                                            nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(),
                                            nuageVspAPIParams.getNuageVspCmsId());
                                    break;
                                } else {
                                    s_logger.debug("There are no free WAN Services available on the Gateway with systemID " + gatewaySystemId + " in VSP");
                                }
                            } else {
                                s_logger.debug("Could not find any WAN Services attached with Gateway with systemID " + gatewaySystemId + " in VSP");
                            }
                        } else {
                            s_logger.debug("Gateway with systemID " + gatewaySystemId + " is not found in VSP");
                        }
                    }
                }
            } catch (Exception exception) {
                errorMessage.append("Failed to associate Gateway service to VSP Domain ").append(networkName).append(".  Json response from VSP REST API is  ")
                .append(exception.getMessage());
            }
            //Then pick one service and attach it to a new VPCConnect object under the domain
        }
    }

    private static String createL3Subnet(String networkName, String netmask, String address, String gateway, List<String> dnsServers, Collection<String[]> ipAddressRanges,
            String networkUuid, NuageVspAPIParams nuageVspAPIParams, String zoneId, String subnetId, StringBuffer errorMessage, String debugMessage) {
        try {
            if (errorMessage.length() == 0) {
                //Now create the Subnet under the Zone
                Map<String, Object> subnetEntity = new HashMap<String, Object>();
                subnetEntity.put(NuageVspAttribute.SUBNET_NAME.getAttributeName(), networkUuid);
                subnetEntity.put(NuageVspAttribute.SUBNET_DESCRIPTION.getAttributeName(), networkName);
                subnetEntity.put(NuageVspAttribute.SUBNET_ADDRESS.getAttributeName(), address);
                subnetEntity.put(NuageVspAttribute.SUBNET_NETMASK.getAttributeName(), netmask);
                subnetEntity.put(NuageVspAttribute.SUBNET_GATEWAY.getAttributeName(), gateway);
                subnetEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), networkUuid);

                String subnetJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                        NuageVspEntity.ZONE, zoneId, NuageVspEntity.SUBNET, subnetEntity, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(),
                        nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                subnetId = getEntityId(subnetJson, NuageVspEntity.SUBNET);
                s_logger.debug(debugMessage + " Created subnet for network " + networkName + " in VSP. Response from VSP is " + subnetJson);

                for (String[] ipAddressRange : ipAddressRanges) {
                    createAddressRange(networkName, ipAddressRange, networkUuid, nuageVspAPIParams, subnetId, NuageVspEntity.SUBNET, debugMessage, errorMessage);
                }
                createDhcpOptions(networkName, dnsServers, networkUuid, nuageVspAPIParams, subnetId, NuageVspEntity.SUBNET, debugMessage, true, errorMessage);
            }
        } catch (Exception exception) {
            errorMessage.append(debugMessage + " Failed to create Subnet for network ").append(networkName).append(".  Json response from VSP REST API is  ")
                    .append(exception.getMessage());
        }
        return subnetId;
    }

    private static void createAddressRange(String networkName, String[] ipAddressRange, String networkUuid, NuageVspAPIParams nuageVspAPIParams, String networkId,
            NuageVspEntity nuageVspEntity, String debugMessage, StringBuffer errorMessage) throws Exception {
        try {
            if (errorMessage.length() == 0) {
                //Create the Address Range
                Map<String, Object> subnetAddressRange = new HashMap<String, Object>();
                subnetAddressRange.put(NuageVspAttribute.ADDRESS_RANGE_MIN.getAttributeName(), ipAddressRange[0]);
                subnetAddressRange.put(NuageVspAttribute.ADDRESS_RANGE_MAX.getAttributeName(), ipAddressRange[1]);
                subnetAddressRange.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), networkUuid);
                String addressRangeJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                        nuageVspEntity, networkId, NuageVspEntity.ADDRESS_RANGE, subnetAddressRange, null, nuageVspAPIParams.getRestRelativePath(),
                        nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(),
                        nuageVspAPIParams.getNuageVspCmsId());
                s_logger.debug(debugMessage + " Created subnet Address Range for network " + networkName + " in VSP. Response from VSP is " + addressRangeJson);
            }
        } catch (Exception exception) {
            errorMessage.append(debugMessage + " Failed to create Subnet Address Range for network ").append(networkName).append(".  Json response from VSP REST API is  ")
                    .append(exception.getMessage());
        }
    }

    public static void createDhcpOptions(String networkName, List<String> dnsServers, String networkUuid, NuageVspAPIParams nuageVspAPIParams, String networkId,
            NuageVspEntity nuageVspEntity, String debugMessage, boolean isCreateDhcpOption, StringBuffer errorMessage) throws Exception {
        try {
            Map<String, Object> existingDhcpOptions = null;
            if (dnsServers != null && dnsServers.size() > 0) {
                existingDhcpOptions = new HashMap<String, Object>();
                String type = "06";
                String value = null;
                String length = null;
                if (dnsServers.size() == 2) {
                    length = "08";
                    value = getPaddedHexValue(dnsServers.get(0)) + getPaddedHexValue(dnsServers.get(1));
                } else {
                    length = "04";
                    value = getPaddedHexValue(dnsServers.get(0));
                }
                existingDhcpOptions.put(NuageVspAttribute.DHCP_OPTIONS_LENGTH.getAttributeName(), length);
                existingDhcpOptions.put(NuageVspAttribute.DHCP_OPTIONS_TYPE.getAttributeName(), type);
                existingDhcpOptions.put(NuageVspAttribute.DHCP_OPTIONS_VALUE.getAttributeName(), value);
                existingDhcpOptions.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), networkUuid);
            }

            if (!isCreateDhcpOption) {
                //get the DHCP option information and check if exists. If yes, then see if the DHCP option is modified
                String dhcpOptionsJson = findEntityByExternalUuid(nuageVspEntity, networkId, NuageVspEntity.DHCP_OPTIONS, networkUuid, nuageVspAPIParams);
                if (!StringUtils.isBlank(dhcpOptionsJson)) {
                    Map<String, Object> dhcpOption = parseJson(dhcpOptionsJson, NuageVspEntity.DHCP_OPTIONS).iterator().next();
                    String dhcpOptionValue = (String)dhcpOption.get(NuageVspAttribute.DHCP_OPTIONS_VALUE.getAttributeName());
                    String dhcpOptionId = (String)dhcpOption.get(NuageVspAttribute.ID.getAttributeName());
                    if (existingDhcpOptions == null) {
                        s_logger.debug("Network (" + networkUuid + ") DNS server's setting " + dhcpOptionValue + " is removed. So, delete the DHCPOption for the network");
                        cleanUpVspStaleObjects(NuageVspEntity.DHCP_OPTIONS, dhcpOptionId, nuageVspAPIParams);
                        return;
                    } else {
                        if (!existingDhcpOptions.get(NuageVspAttribute.DHCP_OPTIONS_VALUE.getAttributeName()).equals(dhcpOptionValue)) {
                            NuageVspApi.executeRestApi(RequestType.MODIFY, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                                    NuageVspEntity.DHCP_OPTIONS, dhcpOptionId, null, existingDhcpOptions, null, nuageVspAPIParams.getRestRelativePath(),
                                    nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false,
                                    nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                            s_logger.debug("Network (" + networkName + ") DNS server's setting " + dhcpOptionValue + " is changed to new value "
                                    + existingDhcpOptions.get(NuageVspAttribute.DHCP_OPTIONS_VALUE.getAttributeName()) + ". So, the DHCPOption for the network is updated");
                            return;
                        } else {
                            s_logger.debug("Network (" + networkName + ") DNS server's setting " + dhcpOptionValue + " is not changed");
                            return;
                        }
                    }
                }
            }
            //Create the DHCP Option if it is Create case or if the DHCP option was not already associated with the network
            if (existingDhcpOptions != null) {
                String addressRangeJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                        nuageVspEntity, networkId, NuageVspEntity.DHCP_OPTIONS, existingDhcpOptions, null, nuageVspAPIParams.getRestRelativePath(),
                        nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(),
                        nuageVspAPIParams.getNuageVspCmsId());
                s_logger.debug(debugMessage + " Created DHCP options for network " + networkName + " in VSP. Response from VSP is " + addressRangeJson);
            }
        } catch (Exception exception) {
            errorMessage.append(debugMessage + " Failed to create DHCP options for network ").append(networkName).append(".  Json response from VSP REST API is  ")
                    .append(exception.getMessage());
        }
    }

    private static String getPaddedHexValue(String dnsServer) {
        String value = Long.toHexString(NetUtils.ip2Long(dnsServer));
        int valueLength = 8 - value.length();
        if (valueLength > 0) {
            StringBuffer pad = new StringBuffer();
            for (int i = 0; i < valueLength; i++) {
                pad.append("0");
            }
            value = pad + value;
        }
        return value;
    }

    public static String createIsolatedL2NetworkWithDefaultACLs(String entepriseId, String networkName, String netmask, String address, String gateway,
            Collection<String[]> ipAddressRanges, boolean defaultCSEgressPolicy, String networkUuid, JSONArray groupId, NuageVspAPIParams nuageVspAPIParams)
            throws NuageVspAPIUtilException {

        s_logger.debug("Create or find Isolated L2 Domain for network " + networkUuid + " in VSP");
        String l2domainTemplateId = null;
        String l2DomainId = null;
        StringBuffer errorMessage = new StringBuffer();

        l2domainTemplateId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, entepriseId, NuageVspEntity.L2DOMAIN_TEMPLATE, networkUuid, nuageVspAPIParams);
        if (StringUtils.isNotBlank(l2domainTemplateId)) {
            s_logger.debug("L2Domain Template " + l2domainTemplateId + " already exists.");
            l2DomainId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, entepriseId, NuageVspEntity.L2DOMAIN, networkUuid, nuageVspAPIParams);
            if (StringUtils.isNotBlank(l2DomainId)) {
                s_logger.debug("L2Domain " + l2DomainId + " already exists for network " + networkName + " in VSP.");
            } else {
                errorMessage.append("L2Domain is not found under the L2DomainTemplate ").append(l2domainTemplateId).append(" for network ").append(networkName)
                        .append(" in VSP. There is a network sync issue with VSD");
            }
        } else {

            Map<String, Object> l2domainTemplateEntity = new HashMap<String, Object>();
            l2domainTemplateEntity.put(NuageVspAttribute.L2DOMAIN_TEMPLATE_NAME.getAttributeName(), networkUuid);
            l2domainTemplateEntity.put(NuageVspAttribute.L2DOMAIN_TEMPLATE_DESCRIPTION.getAttributeName(), networkName);
            l2domainTemplateEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), networkUuid);
            l2domainTemplateEntity.put(NuageVspAttribute.L2DOMAIN_TEMPLATE_ADDRESS.getAttributeName(), address);
            l2domainTemplateEntity.put(NuageVspAttribute.L2DOMAIN_TEMPLATE_NETMASK.getAttributeName(), netmask);
            l2domainTemplateEntity.put(NuageVspAttribute.L2DOMAIN_TEMPLATE_GATEWAY.getAttributeName(), gateway);

            try {
                String l2domainTemplateJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                        NuageVspEntity.ENTERPRISE, entepriseId, NuageVspEntity.L2DOMAIN_TEMPLATE, l2domainTemplateEntity, null, nuageVspAPIParams.getRestRelativePath(),
                        nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(),
                        nuageVspAPIParams.getNuageVspCmsId());
                s_logger.debug("Created L2DomainTemplate for network " + networkName + " in VSP . Response from VSP is " + l2domainTemplateJson);
                l2domainTemplateId = getEntityId(l2domainTemplateJson, NuageVspEntity.L2DOMAIN_TEMPLATE);
                //create Address Ranges
                for (String[] ipAddressRange : ipAddressRanges) {
                    createAddressRange(networkName, ipAddressRange, networkUuid, nuageVspAPIParams, l2domainTemplateId, NuageVspEntity.L2DOMAIN_TEMPLATE, "", errorMessage);
                }
            } catch (Exception exception) {
                String error = "Failed to create L2DomainTemplate for network " + networkName + ".  Json response from VSP REST API is  " + exception.getMessage();
                s_logger.error(error, exception);
                throw new NuageVspAPIUtilException(error);
            }

            try {
                if (errorMessage.length() == 0) {
                    //Now instantiate the L2domain template
                    Map<String, Object> L2domainEntity = new HashMap<String, Object>();
                    L2domainEntity.put(NuageVspAttribute.L2DOMAIN_NAME.getAttributeName(), networkUuid);
                    L2domainEntity.put(NuageVspAttribute.L2DOMAIN_DESCRIPTION.getAttributeName(), networkName);
                    L2domainEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), networkUuid);
                    L2domainEntity.put(NuageVspAttribute.L2DOMAIN_TEMPLATE_ID.getAttributeName(), l2domainTemplateId);

                    String l2DomainJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                            NuageVspEntity.ENTERPRISE, entepriseId, NuageVspEntity.L2DOMAIN, L2domainEntity, null, nuageVspAPIParams.getRestRelativePath(),
                            nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(),
                            nuageVspAPIParams.getNuageVspCmsId());
                    s_logger.debug("Created L2Domain for network " + networkName + " in VSP . Response from VSP is " + l2DomainJson);
                    l2DomainId = getEntityId(l2DomainJson, NuageVspEntity.L2DOMAIN);

                    //set permission to use the Subnet
                    if (groupId != null) {
                        try {
                            addPermission(NuageVspEntity.L2DOMAIN, l2DomainId, groupId, nuageVspAPIParams);
                        } catch (Exception e) {
                            errorMessage.append(e.getMessage());
                        }
                    }
                }
            } catch (Exception exception) {
                errorMessage.append("Failed to instantiate L2DomainTemplate for network ").append(networkName).append(".  Json response from VSP REST API is  ")
                        .append(exception.getMessage());
            }

            //Create default ingress and egress ACLs
            errorMessage = createDefaultIngressAndEgressAcls(false, networkUuid, defaultCSEgressPolicy, NuageVspEntity.L2DOMAIN, l2DomainId, errorMessage, null,
                    new HashMap<Integer, Map<String, Object>>(0), null, new HashMap<Integer, Map<String, Object>>(0), networkName, nuageVspAPIParams);
        }

        if (errorMessage.length() != 0) {
            s_logger.error(errorMessage);
            cleanUpVspStaleObjects(NuageVspEntity.L2DOMAIN_TEMPLATE, l2domainTemplateId, nuageVspAPIParams);
            throw new NuageVspAPIUtilException(errorMessage.toString());
        }

        return l2DomainId;
    }

    public static StringBuffer createDefaultIngressAndEgressAcls(boolean reuseDomain, String vpcOrSubnetUuid, boolean defaultCSEgressPolicy, NuageVspEntity nuageVspEntity, String nuageVspEntityId,
            StringBuffer errorMessage, String ingressACLTempId, Map<Integer, Map<String, Object>> defaultVspIngressAclEntries, String egressACLTempId,
            Map<Integer, Map<String, Object>> defaultVspEgressAclEntries, String networkName, NuageVspAPIParams nuageVspAPIParams) {
        try {
            if (errorMessage.length() == 0) {
                createDefaultIngressAcl(vpcOrSubnetUuid, nuageVspEntity, nuageVspEntityId, defaultCSEgressPolicy, ingressACLTempId, defaultVspIngressAclEntries,
                        networkName, nuageVspAPIParams);
            }
        } catch (Exception exception) {
            errorMessage.append("Failed to create default Ingress ACL for network ").append(vpcOrSubnetUuid).append(".  Json response from VSP REST API is  ")
                    .append(exception.getMessage());
        }

        try {
            if (errorMessage.length() == 0) {
                createDefaultEgressAcl(reuseDomain, vpcOrSubnetUuid, nuageVspEntity, nuageVspEntityId, defaultCSEgressPolicy, egressACLTempId, defaultVspEgressAclEntries, networkName,
                        nuageVspAPIParams);
            }
        } catch (Exception exception) {
            errorMessage.append("Failed to create default Egress ACL for network ").append(vpcOrSubnetUuid).append(".  Json response from VSP REST API is  ")
                    .append(exception.getMessage());
        }

        return errorMessage;
    }

    public static String getEnterprise(String domainUuid, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        String enterpriseId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, null, null, domainUuid, nuageVspAPIParams);
        if (StringUtils.isBlank(enterpriseId)) {
            String errorMessage = "Enterprise corresponding to CS domain " + domainUuid
                    + " does not exist in VSP. There is a data sync issue. Please a check VSP or create a new network";
            s_logger.error(errorMessage);
            throw new NuageVspAPIUtilException(errorMessage);
        }
        return enterpriseId;
    }

    public static Pair<String, String> getIsolatedSubNetwork(String entepriseId, String networkUuid, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        return getIsolatedSubNetwork(entepriseId, networkUuid, nuageVspAPIParams, null);
    }

    public static Pair<String, String> getIsolatedSubNetwork(String enterpriseId, String networkUuid, NuageVspAPIParams nuageVspAPIParams, String vpcUuid) throws NuageVspAPIUtilException {

        String domainId = null;
        String zoneId = null;
        String subnetId = null;

        boolean isVpc = StringUtils.isNotBlank(vpcUuid);
        String vpcOrSubnetUuid;
        if (isVpc) {
            vpcOrSubnetUuid = vpcUuid;
        } else {
            vpcOrSubnetUuid = networkUuid;
        }

        //Check if L3 DomainTemplate exists
        try {
            domainId = getIsolatedDomain(enterpriseId, vpcOrSubnetUuid, NuageVspEntity.DOMAIN, nuageVspAPIParams);

            zoneId = findEntityIdByExternalUuid(NuageVspEntity.DOMAIN, domainId, NuageVspEntity.ZONE, vpcOrSubnetUuid, nuageVspAPIParams);
            if (StringUtils.isBlank(zoneId)) {
                throw new NuageVspAPIUtilException("Zone corresponding to network " + vpcOrSubnetUuid
                        + " does not exist in VSP. There is a data sync issue. Please a check VSP or create a new network");
            }
            subnetId = findEntityIdByExternalUuid(NuageVspEntity.ZONE, zoneId, NuageVspEntity.SUBNET, networkUuid, nuageVspAPIParams);
            if (StringUtils.isBlank(subnetId)) {
                throw new NuageVspAPIUtilException("Subnet corresponding to network " + networkUuid
                        + " does not exist in VSP. There is a data sync issue. Please a check VSP or create a new network");
            }
            return new Pair<String, String>(domainId, subnetId);
        } catch (Exception exception) {
            String errorMessage = "Failed to get Subnet corresponding to network " + networkUuid + ".  Json response from VSP REST API is  " + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static String getIsolatedDomain(String entepriseId, String vpcOrSubnetUuid, NuageVspEntity attachedNetworkType, NuageVspAPIParams nuageVspAPIParams)
            throws NuageVspAPIUtilException {
        String domainId;
        domainId = findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, entepriseId, attachedNetworkType, vpcOrSubnetUuid, nuageVspAPIParams);
        if (StringUtils.isBlank(domainId)) {
            throw new NuageVspAPIUtilException(attachedNetworkType + " corresponding to network " + vpcOrSubnetUuid
                    + " does not exist in VSP. There is a data sync issue. Please a check VSP or create a new network");
        }
        return domainId;
    }

    public static List<Map<String, Object>> getACLAssociatedToDomain(String networkUuid, String attachedTemplateId, NuageVspEntity attachedNetworkType, NuageVspEntity aclType,
            NuageVspAPIParams nuageVspAPIParams, boolean throwExceptionIfNotPresent) throws Exception {
        String aclTemplates = NuageVspApi.executeRestApi(RequestType.GETALL, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                attachedNetworkType, attachedTemplateId, aclType, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(),
                nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
        if (StringUtils.isNotBlank(aclTemplates)) {
            //Get the ACLEntries...
            return parseJson(aclTemplates, aclType);
        } else {
            if (throwExceptionIfNotPresent) {
                throw new NuageVspAPIUtilException(aclType + " ACL corresponding to network " + networkUuid
                        + " does not exist in VSP. There is a data sync issue. Please a check VSP or create a new network");
            }
        }
        return null;
    }

    public static List<Map<String, Object>> getACLEntriesAssociatedToLocation(String aclNetworkLocationId, NuageVspEntity aclTemplateType, String aclTemplateId,
            NuageVspAPIParams nuageVspAPIParams) throws Exception {
        NuageVspEntity aclEntryType = aclTemplateType.equals(NuageVspEntity.INGRESS_ACLTEMPLATES) ? NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES
                : NuageVspEntity.EGRESS_ACLTEMPLATES_ENTRIES;
        String aclTemplateEntries = null;
        if (aclNetworkLocationId != null) {
            aclTemplateEntries = findEntityUsingFilter(aclTemplateType, aclTemplateId, aclEntryType, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_LOCATION_ID.getAttributeName(),
                    aclNetworkLocationId, nuageVspAPIParams);
        } else {
            aclTemplateEntries = findEntityUsingFilter(aclTemplateType, aclTemplateId, aclEntryType, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_LOCATION_TYPE.getAttributeName(),
                    NuageVspConstants.ANY, nuageVspAPIParams);
        }

        if (StringUtils.isNotBlank(aclTemplateEntries)) {
            //Get the ACLEntries...
            return parseJson(aclTemplateEntries, aclEntryType);
        } else {
            return Collections.emptyList();
        }
    }

    public static String createSharedResourceInVSP(String sourceNatNetworkUuid, String sourceNatVanGateway, String sourceNatVlanNetmask, NuageVspAPIParams nuageVspAPIParams)
            throws NuageVspAPIUtilException {

        String sharedResourceId = "";
        String cidr = NetUtils.getCidrFromGatewayAndNetmask(sourceNatVanGateway, sourceNatVlanNetmask);

        Map<String, Object> sharedNtwkEntity = new HashMap<String, Object>();
        sharedNtwkEntity.put(NuageVspAttribute.SHARED_RESOURCE_NAME.getAttributeName(), sourceNatNetworkUuid);
        sharedNtwkEntity.put(NuageVspAttribute.SHARED_RESOURCE_GATEWAY.getAttributeName(), sourceNatVanGateway);
        sharedNtwkEntity.put(NuageVspAttribute.SHARED_RESOURCE_NETMASK.getAttributeName(), sourceNatVlanNetmask);
        sharedNtwkEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), sourceNatNetworkUuid);
        sharedNtwkEntity.put(NuageVspAttribute.SHARED_RESOURCE_ADRESS.getAttributeName(), NetUtils.getCidrSubNet(cidr));
        sharedNtwkEntity.put(NuageVspAttribute.SHARED_RESOURCE_TYPE.getAttributeName(), "FLOATING");

        try {
            String sharedResourceJsonString = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                    NuageVspEntity.SHARED_NETWORK, sharedNtwkEntity, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                    nuageVspAPIParams.getRetryInterval(), nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
            sharedResourceId = getEntityId(sharedResourceJsonString, NuageVspEntity.SHARED_NETWORK);
            s_logger.debug("Nuage Vsp Shared Network is not available. So, created a new Shared Network " + sourceNatNetworkUuid);
        } catch (Exception e) {
            String errorMessage = "Failed to create SharedResource in VSP using REST API. Json response from VSP REST API is " + e.getMessage();
            s_logger.error(errorMessage);
            throw new NuageVspAPIUtilException(errorMessage);
        }
        return sharedResourceId;
    }

    public static String allocateFIPToVPortInVsp(String sourceNatIp, String sourceNatIpUuid, String networkUuid, String sharedResourceId, String domainId,
            String vportId, NuageVspEntity attachedNetworkType, NuageVspAPIParams nuageVspAPIParams, String vpcOrSubnetUuid, boolean isVpc) throws NuageVspAPIUtilException {
        String egressFipAclEntryId = null;
        String errorMessage = "";
        String fipExternalId = networkUuid + ":" + sourceNatIpUuid;
        //Check if the floating IP is already associated to the Domain/L2Domain
        String floatingIpId = findEntityIdByExternalUuid(attachedNetworkType.equals(NuageVspEntity.L2DOMAIN) ? NuageVspEntity.L2DOMAIN : NuageVspEntity.DOMAIN, domainId,
                NuageVspEntity.FLOATING_IP, fipExternalId, nuageVspAPIParams);
        if (StringUtils.isBlank(floatingIpId)) {
            Map<String, Object> floatingIPEntity = new HashMap<String, Object>();
            floatingIPEntity.put(NuageVspAttribute.FLOATING_IP_ADDRESS.getAttributeName(), sourceNatIp);
            floatingIPEntity.put(NuageVspAttribute.FLOATING_IP_ASSOC_SHARED_NTWK_ID.getAttributeName(), sharedResourceId);
            floatingIPEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), fipExternalId);

            try {

                String floatingIpJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                        attachedNetworkType.equals(NuageVspEntity.L2DOMAIN) ? NuageVspEntity.L2DOMAIN : NuageVspEntity.DOMAIN, domainId, NuageVspEntity.FLOATING_IP,
                        floatingIPEntity, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                        nuageVspAPIParams.getRetryInterval(), true, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                floatingIpId = getEntityId(floatingIpJson, NuageVspEntity.FLOATING_IP);
                s_logger.debug("Created a new FloatingIP in Vsp " + floatingIpJson + " in FLoatingIP shared resource " + sharedResourceId);
            } catch (Exception e1) {
                errorMessage = "Failed to create Floating in VSP using REST API " + e1.getMessage();
            }
        }

        //Create a Floating IP with the given IP under the domain
        if (StringUtils.isBlank(errorMessage)) {
            errorMessage = updateVPortWithFloatingIPId(vportId, nuageVspAPIParams, floatingIpId);
            s_logger.debug("Associated the new FloatingIP " + sourceNatIp + " to VM with VPort " + vportId);
        }

        if (StringUtils.isNotBlank(errorMessage)) {
            s_logger.error(errorMessage);
            throw new NuageVspAPIUtilException(errorMessage);
        }

        return floatingIpId;
    }

    public static String updateVPortWithFloatingIPId(String vportId, NuageVspAPIParams nuageVspAPIParams, String floatingIPId) {

        String errorMessage = "";
        //set the floating IP to the VPort
        Map<String, Object> vportEntity = new HashMap<String, Object>();
        vportEntity.put(NuageVspAttribute.VPORT_FLOATING_IP_ID.getAttributeName(), floatingIPId);

        try {
            NuageVspApi.executeRestApi(RequestType.MODIFY, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), NuageVspEntity.VPORT, vportId,
                    null, vportEntity, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                    nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
        } catch (Exception e) {
            if (!isNoChangeInEntityException(e)) {
                errorMessage = "Failed to associated the FloatingIP " + floatingIPId + " to the VPort " + vportId + e.getMessage();
            }
        }
        return errorMessage;
    }

    public static void releaseFIPFromVsp(String networkUuid, String staticNatUuid, String domainId, String vportId, String vspNetworkId,
            NuageVspEntity attachedNetworkType, NuageVspAPIParams nuageVspAPIParams, boolean isVpc) throws Exception {
        //get the FIP
        String fipExternalId = networkUuid + ":" + staticNatUuid;
        String floatingIpId = findEntityIdByExternalUuid(attachedNetworkType.equals(NuageVspEntity.L2DOMAIN) ? NuageVspEntity.L2DOMAIN : NuageVspEntity.DOMAIN, domainId,
                NuageVspEntity.FLOATING_IP, fipExternalId, nuageVspAPIParams);
        String vportVspId = null;
        if (StringUtils.isBlank(vportId)) {
            if (StringUtils.isNotBlank(floatingIpId)) {
                NuageVspEntity subnetType = attachedNetworkType.equals(NuageVspEntity.L2DOMAIN) ? NuageVspEntity.L2DOMAIN : NuageVspEntity.SUBNET;
                s_logger.debug("vportId is null. This could be case where VM interface is not present in VSP. So, finding the VPort in " + subnetType
                        + " that has the FIP with externalId " + staticNatUuid);
                //get the all VPorts and check for the FIP associated to it
                String vportJson = null;
                vportJson = NuageVspApi.executeRestApi(RequestType.GETALL, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                        NuageVspEntity.FLOATING_IP, floatingIpId, NuageVspEntity.VPORT, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(),
                        nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                if (StringUtils.isNotBlank(vportJson)) {
                    List<Map<String, Object>> vports = NuageVspApiUtil.parseJson(vportJson, NuageVspEntity.VPORT);
                    for (Map<String, Object> vport : vports) {
                        if (StringUtils.equals((String)vport.get(NuageVspAttribute.VPORT_FLOATING_IP_ID.getAttributeName()), floatingIpId)) {
                            vportVspId = (String)vport.get(NuageVspAttribute.ID.getAttributeName());
                            if (s_logger.isDebugEnabled()) {
                                s_logger.debug("Found a VPort " + vport + " that is associated the stale FIP " + fipExternalId + "in network " + networkUuid);
                            }
                            break;
                        }
                    }
                }
            } else {
                s_logger.debug("vportId is null and also FIP with ACS ID " + staticNatUuid + " does not exists in VSP");
            }
        } else {
            vportVspId = vportId;
        }

        boolean rollbackFIPACL = false;
        //Reset the floatingIpId in vport
        String errorMesage = "";
        if (StringUtils.isNotBlank(vportVspId)) {
            errorMesage = updateVPortWithFloatingIPId(vportVspId, nuageVspAPIParams, null);
            s_logger.debug("Removed the association of Floating IP " + fipExternalId + " with VSP VPort " + vportVspId);
        } else {
            s_logger.debug("FIP " + fipExternalId + " is not associated any VPort in VSD. So, delete it from VSD");
        }
        if (StringUtils.isBlank(errorMesage)) {
            if (StringUtils.isNotBlank(floatingIpId)) {
                if (!cleanUpVspStaleObjects(NuageVspEntity.FLOATING_IP, floatingIpId, nuageVspAPIParams)) {
                    rollbackFIPACL = true;
                }
            }
        } else {
            //Log the error message where the FIP release failed
            s_logger.error(errorMesage);
            rollbackFIPACL = true;
        }
    }

    private static String createDefaultAclTemplate(NuageVspEntity aclTemplateType, String vpcOrSubnetUuid, NuageVspEntity domainType, String domainId, String networkName,
            NuageVspAPIParams nuageVspAPIParams) throws Exception {
        Map<String, Object> egressACLEntity = new HashMap<String, Object>();
        egressACLEntity.put(NuageVspAttribute.ACLTEMPLATES_NAME.getAttributeName(), aclTemplateType == NuageVspEntity.INGRESS_ACLTEMPLATES ? "Ingress ACL" : "Egress ACL");
        egressACLEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), vpcOrSubnetUuid);
        egressACLEntity.put(NuageVspAttribute.ACLTEMPLATES_ALLOW_IP.getAttributeName(), false);
        egressACLEntity.put(NuageVspAttribute.ACLTEMPLATES_ALLOW_NON_IP.getAttributeName(), false);
        egressACLEntity.put(NuageVspAttribute.ACLTEMPLATES_ACTIVE.getAttributeName(), true);

        String aclTemplateJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), domainType,
                domainId, aclTemplateType, egressACLEntity, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(),
                nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
        String aclTemplateId = NuageVspApiUtil.getEntityId(aclTemplateJson, aclTemplateType);
        s_logger.debug("Created ACLTemplate for network " + networkName + " in VSP . Response from VSP is " + aclTemplateJson);
        return aclTemplateId;
    }

    private static void createDefaultEgressAcl(boolean reuseDomain, String vpcOrSubnetUuid, NuageVspEntity domainType, String domainId, boolean defaultCSEgressPolicy,
            String egressACLTempId, Map<Integer, Map<String, Object>> defaultVspEgressAclEntries, String networkName, NuageVspAPIParams nuageVspAPIParams) throws Exception {
        //Now add Egress ACL template
        if (egressACLTempId == null) {
            egressACLTempId = createDefaultAclTemplate(NuageVspEntity.EGRESS_ACLTEMPLATES, vpcOrSubnetUuid, domainType, domainId, networkName, nuageVspAPIParams);
        }

        //Default Subnet Allow ACL
        if (!defaultVspEgressAclEntries.containsKey(NuageVspConstants.DEFAULT_SUBNET_ALLOW_ACL_PRIORITY)) {
            createDefaultACLEntry(vpcOrSubnetUuid, egressACLTempId, false, false, NuageVspConstants.ANY, NuageVspConstants.ACL_ACTION_FORWARD,
                    NuageVspConstants.DEFAULT_SUBNET_ALLOW_ACL_PRIORITY, NuageVspConstants.DEFAULT_SUBNET_ALLOW_ACL, NuageVspConstants.ANY, null,
                    NuageVspConstants.ENDPOINT_SUBNET, networkName, nuageVspAPIParams);
        }

        if (defaultCSEgressPolicy) {
            if (reuseDomain) {
                if (!defaultVspEgressAclEntries.containsKey(NuageVspConstants.DEFAULT_DOMAIN_BLOCK_ACL_PRIORITY)) {
                    createDefaultACLEntry(vpcOrSubnetUuid, egressACLTempId, false, false, NuageVspConstants.ANY, NuageVspConstants.ACL_ACTION_DROP,
                            NuageVspConstants.DEFAULT_DOMAIN_BLOCK_ACL_PRIORITY, NuageVspConstants.DEFAULT_DOMAIN_BLOCK_ACL, NuageVspConstants.ANY, null,
                            NuageVspConstants.ENDPOINT_DOMAIN, networkName, nuageVspAPIParams);
                }
            }
            //add ACL : ICMP allow non-reflexive
            if (!defaultVspEgressAclEntries.containsKey(NuageVspConstants.DEFAULT_ICMP_ALLOW_ACL_PRIORITY)) {
                createDefaultACLEntry(vpcOrSubnetUuid, egressACLTempId, false, false, "ICMP", NuageVspConstants.ACL_ACTION_FORWARD,
                        NuageVspConstants.DEFAULT_ICMP_ALLOW_ACL_PRIORITY, NuageVspConstants.DEFAULT_VSP_INGRESS_ALLOW_ICMP_ACL, NuageVspConstants.ANY, null,
                        NuageVspConstants.ANY, networkName, nuageVspAPIParams);
            }
        }
    }

    private static void createDefaultIngressAcl(String vpcOrSubnetUuid, NuageVspEntity domainType, String domainId, boolean defaultCSEgressPolicy,
            String ingressACLTempId, Map<Integer, Map<String, Object>> defaultVspIngressAclEntries, String networkName, NuageVspAPIParams nuageVspAPIParams) throws Exception {
        //Now add Ingress ACL template
        if (ingressACLTempId == null) {
            ingressACLTempId = createDefaultAclTemplate(NuageVspEntity.INGRESS_ACLTEMPLATES, vpcOrSubnetUuid, domainType, domainId, networkName, nuageVspAPIParams);
        }

        if (!defaultVspIngressAclEntries.containsKey(NuageVspConstants.DEFAULT_SUBNET_ALLOW_ACL_PRIORITY)) {
            createDefaultACLEntry(vpcOrSubnetUuid, ingressACLTempId, true, false, NuageVspConstants.ANY, NuageVspConstants.ACL_ACTION_FORWARD,
                    NuageVspConstants.DEFAULT_SUBNET_ALLOW_ACL_PRIORITY, NuageVspConstants.DEFAULT_SUBNET_ALLOW_ACL, NuageVspConstants.ANY, null,
                    NuageVspConstants.ENDPOINT_SUBNET, networkName, nuageVspAPIParams);
        }
        //networkAclId will null for firewalls and also VPC tier with no ACL list. Then set the default ACL
        if (defaultCSEgressPolicy) {
            if (!defaultVspIngressAclEntries.containsKey(NuageVspConstants.DEFAULT_TCP_ALLOW_ACL_PRIORITY)) {
                createDefaultACLEntry(vpcOrSubnetUuid, ingressACLTempId, true, true, "TCP", NuageVspConstants.ACL_ACTION_FORWARD, NuageVspConstants.DEFAULT_TCP_ALLOW_ACL_PRIORITY,
                        NuageVspConstants.DEFAULT_VSP_INGRESS_ALLOW_TCP_ACL, NuageVspConstants.ANY, null, NuageVspConstants.ANY, networkName, nuageVspAPIParams);
            }
            if (!defaultVspIngressAclEntries.containsKey(NuageVspConstants.DEFAULT_UDP_ALLOW_ACL_PRIORITY)) {
                createDefaultACLEntry(vpcOrSubnetUuid, ingressACLTempId, true, true, "UDP", NuageVspConstants.ACL_ACTION_FORWARD, NuageVspConstants.DEFAULT_UDP_ALLOW_ACL_PRIORITY,
                        NuageVspConstants.DEFAULT_VSP_INGRESS_ALLOW_UDP_ACL, NuageVspConstants.ANY, null, NuageVspConstants.ANY, networkName, nuageVspAPIParams);
            }
            if (!defaultVspIngressAclEntries.containsKey(NuageVspConstants.DEFAULT_ICMP_ALLOW_ACL_PRIORITY)) {
                createDefaultACLEntry(vpcOrSubnetUuid, ingressACLTempId, true, false, "ICMP", NuageVspConstants.ACL_ACTION_FORWARD,
                        NuageVspConstants.DEFAULT_ICMP_ALLOW_ACL_PRIORITY, NuageVspConstants.DEFAULT_VSP_INGRESS_ALLOW_ICMP_ACL, NuageVspConstants.ANY, null,
                        NuageVspConstants.ANY, networkName, nuageVspAPIParams);
            }
        }
    }

    public static void createDefaultACLEntry(String networkUuid, String aclTemplateId, boolean isIngress, boolean isReflexive, String protocol, String action, int aclPriority,
            String aclEntryDescription, String locationType, String locationId, String destinationNetwork, String networkName, NuageVspAPIParams nuageVspAPIParams)
            throws Exception {
        if (isIngress) {
            Map<String, Object> ingressACLEntryEntity = null;
            ingressACLEntryEntity = new HashMap<String, Object>();
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_ETHER_TYPE.getAttributeName(), NuageVspConstants.ETHERTYPE_IP);
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_ACTION.getAttributeName(), action);
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_LOCATION_TYPE.getAttributeName(), locationType);
            if (locationId != null) {
                ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_LOCATION_ID.getAttributeName(), locationId);
            }
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_NETWORK_TYPE.getAttributeName(), destinationNetwork);
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_PROTOCOL.getAttributeName(), getProtocolNumber(protocol));
            if (StringUtils.equals(protocol, "TCP") || StringUtils.equals(protocol, "UDP")) {
                ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_SOURCE_PORT.getAttributeName(), NuageVspConstants.STAR);
                ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_DEST_PORT.getAttributeName(), NuageVspConstants.STAR);
            }
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_DSCP.getAttributeName(), NuageVspConstants.STAR);
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_REFLEXIVE.getAttributeName(), isReflexive);
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_DESCRIPTION.getAttributeName(), aclEntryDescription);
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_PRIORITY.getAttributeName(), aclPriority);

            String ingressACLEntry = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                    NuageVspEntity.INGRESS_ACLTEMPLATES, aclTemplateId, NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES, ingressACLEntryEntity, null,
                    nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false,
                    nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
            s_logger.debug("Created Default IngressACLTemplateEntry for network " + networkName + " in VSP . Response from VSP is " + ingressACLEntry);
        } else {
            Map<String, Object> egressACLEntryEntity = null;
            egressACLEntryEntity = new HashMap<String, Object>();
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_ETHER_TYPE.getAttributeName(), NuageVspConstants.ETHERTYPE_IP);
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_ACTION.getAttributeName(), action);
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_LOCATION_TYPE.getAttributeName(), NuageVspConstants.ANY);
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_NETWORK_TYPE.getAttributeName(), destinationNetwork);
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_PROTOCOL.getAttributeName(), getProtocolNumber(protocol));
            if (StringUtils.equals(protocol, "TCP") || StringUtils.equals(protocol, "UDP")) {
                egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_SOURCE_PORT.getAttributeName(), NuageVspConstants.STAR);
                egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_DEST_PORT.getAttributeName(), NuageVspConstants.STAR);
            }
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_DSCP.getAttributeName(), NuageVspConstants.STAR);
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_REFLEXIVE.getAttributeName(), isReflexive);
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_DESCRIPTION.getAttributeName(), aclEntryDescription);
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_PRIORITY.getAttributeName(), aclPriority);

            String ingressACLEntry = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                    NuageVspEntity.EGRESS_ACLTEMPLATES, aclTemplateId, NuageVspEntity.EGRESS_ACLTEMPLATES_ENTRIES, egressACLEntryEntity, null,
                    nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false,
                    nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
            s_logger.debug("Created Default egressACLTemplateEntry for network " + networkName + " in VSP . Response from VSP is " + ingressACLEntry);
        }
    }

    public static Map<ACLRule, List<String>> createEgressACLEntryInVsp(boolean isNetworkAcl, String vsdEnterpriseId, String egressAclTemplateId, ACLRule rule, String sourceIp,
            String aclNetworkLocationId, long networkId, List<String> successfullyAddedEgressACls, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {

        Map<ACLRule, List<String>> errorMap = new HashMap<ACLRule, List<String>>();
        List<String> errorMessages = new ArrayList<String>();
        for (String sourceCidr : rule.getSourceCidrList()) {
            try {
                String egressAclEntryId = createAclEntriesWithRetry(isNetworkAcl, NuageVspEntity.EGRESS_ACLTEMPLATES, NuageVspEntity.EGRESS_ACLTEMPLATES_ENTRIES, vsdEnterpriseId, rule, sourceIp, null,
                        networkId, aclNetworkLocationId, sourceCidr, egressAclTemplateId, nuageVspAPIParams);
                successfullyAddedEgressACls.add(egressAclEntryId);

            } catch (Exception exception) {
                errorMessages.add("Failed to create Egress ACL Entry for rule " + rule + " in VSP enterprise " + vsdEnterpriseId + ". " + exception.getMessage());
            }
        }
        if (errorMessages.size() > 0) {
            errorMap.put(rule, errorMessages);
        }
        return errorMap;
    }

    public static Map<ACLRule, List<String>> updateEgressACLEntryInVsp(String vsdEnterpriseId, String egressAclEntryId, Map<String, Object> egressEntryData, ACLRule rule,
            String sourceIp, String aclNetworkLocationId, long networkId, int oldPriority, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {

        Map<ACLRule, List<String>> errorMap = new HashMap<ACLRule, List<String>>();
        List<String> errorMessages = new ArrayList<String>();
        for (String sourceCidr : rule.getSourceCidrList()) {
            try {
                //Check if the ACL is modified or not. If yes, then execute the update method
                Map<String, Object> modifiedEgressACLEntryEntity = getEgressAclEntry(vsdEnterpriseId, rule, sourceIp, aclNetworkLocationId, networkId, nuageVspAPIParams,
                        sourceCidr, true, oldPriority);
                if (!rule.isNotModified(rule, modifiedEgressACLEntryEntity, egressEntryData)) {
                    String egressAclEntryJson = NuageVspApi.executeRestApi(RequestType.MODIFY, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                            NuageVspEntity.EGRESS_ACLTEMPLATES_ENTRIES, egressAclEntryId, null, modifiedEgressACLEntryEntity, null, nuageVspAPIParams.getRestRelativePath(),
                            nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(),
                            nuageVspAPIParams.getNuageVspCmsId());
                    s_logger.debug("Modified Egress ACL Entry for rule " + rule + " with CIDR " + sourceCidr + " in VSP. Response from VSP is " + egressAclEntryJson);
                }
            } catch (Exception exception) {
                if (!isNoChangeInEntityException(exception)) {
                    errorMessages.add("Failed to Modify Engress ACL Entry for rule " + rule + " in VSP enterprise " + vsdEnterpriseId + ". " + exception.getMessage());
                }
            }
        }
        if (errorMessages.size() > 0) {
            errorMap.put(rule, errorMessages);
        }
        return errorMap;
    }

    private static boolean isNoChangeInEntityException(Exception exception) {
        //"\"internalErrorCode\":2039"
        return exception instanceof NuageVspException && ((NuageVspException)exception).getNuageErrorCode() == NuageVspApi.s_noChangeInEntityErrorCode;
    }

    public static Map<ACLRule, List<String>> createIngressACLEntryInVsp(boolean isNetworkAcl, String vsdEnterpriseId, String ingressAclTemplateId, ACLRule rule,
            String aclNetworkLocationId, long networkId, List<String> successfullyAddedIngressACls, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {

        Map<ACLRule, List<String>> errorMap = new HashMap<ACLRule, List<String>>();
        List<String> errorMessages = new ArrayList<String>();
        for (String sourceCidr : rule.getSourceCidrList()) {
            try {
                String ingressAclEntryId = createAclEntriesWithRetry(isNetworkAcl, NuageVspEntity.INGRESS_ACLTEMPLATES, NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES, vsdEnterpriseId, rule, null, null, networkId,
                        aclNetworkLocationId, sourceCidr, ingressAclTemplateId, nuageVspAPIParams);
                successfullyAddedIngressACls.add(ingressAclEntryId);
            } catch (Exception exception) {
                errorMessages.add("Failed to create Ingress ACL Entry for rule " + rule + " with CIDR " + sourceCidr + " in VSP enterprise " + vsdEnterpriseId + ". "
                        + exception.getMessage());
            }
        }
        if (errorMessages.size() > 0) {
            errorMap.put(rule, errorMessages);
        }

        return errorMap;
    }

    public static Map<ACLRule, List<String>> updateIngressACLEntryInVsp(String vsdEnterpriseId, String ingressAclEntryId, Map<String, Object> ingressEntryData, ACLRule rule,
            String aclNetworkLocationId, long networkId, int oldPriority, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {

        Map<ACLRule, List<String>> errorMap = new HashMap<ACLRule, List<String>>();
        List<String> errorMessages = new ArrayList<String>();
        for (String sourceCidr : rule.getSourceCidrList()) {
            try {
                //Check if the ACL is modified or not. If yes, then execute the update method
                Map<String, Object> modifiedIngressACLEntryEntity = getIngressAclEntry(vsdEnterpriseId, rule, aclNetworkLocationId, networkId, nuageVspAPIParams, sourceCidr, true,
                        oldPriority);
                if (!rule.isNotModified(rule, modifiedIngressACLEntryEntity, ingressEntryData)) {
                    //modify Ingress ACL Entry
                    String ingressAclEntryJson = NuageVspApi.executeRestApi(RequestType.MODIFY, nuageVspAPIParams.getCloudstackDomainName(),
                            nuageVspAPIParams.getCurrentUserName(), NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES, ingressAclEntryId, null, modifiedIngressACLEntryEntity, null,
                            nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(),
                            false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                    s_logger.debug("Updated Ingress ACL Entry for rule " + rule + " with CIDR " + sourceCidr + " in VSP. Response from VSP is " + ingressAclEntryJson);
                }
            } catch (Exception exception) {
                if (!isNoChangeInEntityException(exception)) {
                    errorMessages.add("Failed to Modify Ingress ACL Entry for rule " + rule + " with CIDR " + sourceCidr + " in VSP enterprise " + vsdEnterpriseId + ". "
                            + exception.getMessage());
                }
            }
        }
        if (errorMessages.size() > 0) {
            errorMap.put(rule, errorMessages);
        }

        return errorMap;
    }

    private static Map<String, Object> getEgressAclEntry(String vsdEnterpriseId, ACLRule rule, String sourceIp, String aclNetworkLocationId, long networkId,
            NuageVspAPIParams nuageVspAPIParams, String sourceCidr, boolean isUpdate, int oldPriority) throws NuageVspAPIUtilException {
        Map<String, Object> egressACLEntryEntity = new HashMap<String, Object>();
        egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_ETHER_TYPE.getAttributeName(), NuageVspConstants.ETHERTYPE_IP);
        egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_ACTION.getAttributeName(),
                rule.getAction().equals(ACLAction.Allow) ? NuageVspConstants.ACL_ACTION_FORWARD : NuageVspConstants.ACL_ACTION_DROP);
        egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_PROTOCOL.getAttributeName(), getProtocolNumber(rule.getProtocol()));
        egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_DSCP.getAttributeName(), NuageVspConstants.STAR);
        egressACLEntryEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), rule.getUuid());
        setPriorityForAcl(rule, networkId, isUpdate, oldPriority);
        if (rule.getPriority() >= 0 && (rule.getPriority() < NuageVspConstants.MAX_ACL_PRIORITY)) {
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_PRIORITY.getAttributeName(), rule.getPriority());
        } else {
            String error = "Rule number " + rule.getPriority() + " can not be greater than " + NuageVspConstants.MAX_ACL_PRIORITY + " as it is used as rule numbers for"
                    + " predefined rules in VSP";
            s_logger.error(error);
            throw new NuageVspAPIUtilException(error);
        }

        //if the rule purpose is StaticNAT then need to create a macro set to the source cidr and
        //set the IPOverride to floating IP IP and network type to Enterprise Macro
        //sourceCidr is source CIDR in both case of NetworkACL and Firewall.
        String macroId = getOrCreatePublicMacroInEnterprise(vsdEnterpriseId, sourceCidr, rule.getUuid(), nuageVspAPIParams);
        egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_NETWORK_TYPE.getAttributeName(), NuageVspConstants.ENTERPRISE_NETWORK);
        egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_NETWORK_ID.getAttributeName(), macroId);
        if (sourceIp != null) {
            //Address override with the NAT IP which is the source address in the StaticNatRule
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_ADDR_OVERRIDE.getAttributeName(), sourceIp);
        }
        //If the CS traffic type is ingress rule, when creating a egress rule source port becomes the destination port in VSD
        //Setting the Location and network in ACL
        egressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_LOCATION_TYPE.getAttributeName(), NuageVspConstants.SUBNET);
        egressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_LOCATION_ID.getAttributeName(), aclNetworkLocationId);

        egressACLEntryEntity
                .put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_DEST_PORT.getAttributeName(), getPortRange(rule.getStartPort(), rule.getEndPort(), rule.getProtocol()));
        egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_SOURCE_PORT.getAttributeName(), getPortRange(rule.getProtocol()));

        if (rule.getAction().equals(ACLAction.Allow) && isTCPOrUDP(rule.getProtocol())) {
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_REFLEXIVE.getAttributeName(), true);
        } else {
            egressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_REFLEXIVE.getAttributeName(), false);
        }
        return egressACLEntryEntity;
    }

    private static Map<String, Object> getIngressAclEntry(String vsdEnterpriseId, ACLRule rule, String aclNetworkLocationId, long networkId, NuageVspAPIParams nuageVspAPIParams,
            String sourceCidr, boolean isUpdate, int oldPriority) throws NuageVspAPIUtilException {
        Map<String, Object> ingressACLEntryEntity = new HashMap<String, Object>();
        ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_ETHER_TYPE.getAttributeName(), NuageVspConstants.ETHERTYPE_IP);
        ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_ACTION.getAttributeName(),
                rule.getAction().equals(ACLAction.Allow) ? NuageVspConstants.ACL_ACTION_FORWARD : NuageVspConstants.ACL_ACTION_DROP);
        ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_PROTOCOL.getAttributeName(), getProtocolNumber(rule.getProtocol()));
        ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_DSCP.getAttributeName(), NuageVspConstants.STAR);
        ingressACLEntryEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), rule.getUuid());

        //check the priority number for NetworkACL
        setPriorityForAcl(rule, networkId, isUpdate, oldPriority);
        //Firewall rules do not have any priority. So, its value is set to -1 in ACLRule
        if (rule.getPriority() >= 0 && (rule.getPriority() < NuageVspConstants.MAX_ACL_PRIORITY)) {
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_PRIORITY.getAttributeName(), rule.getPriority());
        } else {
            String error = "Rule can not be greater than " + NuageVspConstants.MAX_ACL_PRIORITY + " as it is used as rule numbers for" + " predefined rules in VSP";
            s_logger.error(error);
            throw new NuageVspAPIUtilException(error);
        }
        if (rule.getType().equals(ACLRule.ACLType.Firewall)) {
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_ADDR_OVERRIDE.getAttributeName(), sourceCidr);
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_NETWORK_TYPE.getAttributeName(), NuageVspConstants.ANY);
        } else if (rule.getType().equals(ACLRule.ACLType.NetworkACL)) {
            //In case of Egress NetworkACL, sourceCidr is the destination network. So, we need to create a Macro like its done for Egress case
            String macroId = getOrCreatePublicMacroInEnterprise(vsdEnterpriseId, sourceCidr, rule.getUuid(), nuageVspAPIParams);
            ingressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_NETWORK_TYPE.getAttributeName(), NuageVspConstants.ENTERPRISE_NETWORK);
            ingressACLEntryEntity.put(NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_NETWORK_ID.getAttributeName(), macroId);
        }

        ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_LOCATION_TYPE.getAttributeName(), NuageVspConstants.SUBNET);
        ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_LOCATION_ID.getAttributeName(), aclNetworkLocationId);

        ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_DEST_PORT.getAttributeName(),
                getPortRange(rule.getStartPort(), rule.getEndPort(), rule.getProtocol()));
        ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_SOURCE_PORT.getAttributeName(), getPortRange(rule.getProtocol()));

        if (rule.getAction().equals(ACLAction.Allow) && isTCPOrUDP(rule.getProtocol())) {
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_REFLEXIVE.getAttributeName(), true);
        } else {
            ingressACLEntryEntity.put(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_REFLEXIVE.getAttributeName(), false);
        }
        return ingressACLEntryEntity;
    }

    private static void setPriorityForAcl(ACLRule rule, long networkId, boolean isUpdate, int oldPriority) throws NuageVspAPIUtilException {
        if (rule.getType().equals(ACLType.NetworkACL)) {
            if (rule.getPriority() > NuageVspConstants.MIN_ACL_PRIORITY) {
                String error = "Rule number can not be greater than " + NuageVspConstants.MIN_ACL_PRIORITY + " as it is used to generate a unique rule per tier in VSP";
                s_logger.error(error);
                throw new NuageVspAPIUtilException(error);
            } else {
                //In case of NetworkACL, same rule list can be set to different Tiers. So, modify the priority by prepending the tier ID
                //to make it unique across the tiers under the same VPC
                int newPriority = Integer.valueOf(String.valueOf(networkId) + String.valueOf(rule.getPriority()));
                rule.setPriority(newPriority);
            }
        } else if (rule.getType().equals(ACLRule.ACLType.Firewall)) {
            //Set the priority for FW ACL handle the priority for networkACL later
            if (isUpdate) {
                rule.setPriority(oldPriority);
            } else {
                rule.setPriority(NuageVspApiUtil.getRandomPriority());
            }
        }
    }

    public static void cleanUpVspStaleObjectsWithExternalID(NuageVspEntity entityType, String entityId, NuageVspEntity childEntityType, String externalId,
            NuageVspAPIParams nuageVspAPIParams) {
        try {
            String jsonString = findEntityByExternalUuid(entityType, entityId, childEntityType, externalId, nuageVspAPIParams);
            if (StringUtils.isNotBlank(jsonString)) {
                List<Map<String, Object>> entities = NuageVspApi.parseJsonString(childEntityType, jsonString);
                for (Map<String, Object> entity : entities) {
                    String childEntityId = (String)entity.get(NuageVspAttribute.ID.getAttributeName());
                    NuageVspApi.executeRestApi(RequestType.DELETE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), childEntityType,
                            childEntityId, null, null, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                            nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                    s_logger.debug("Successfully cleaned stale VSP entity " + childEntityType + " with ID " + childEntityId);
                }
            }
        } catch (Exception e) {
            s_logger.warn("Failed to clean " + childEntityType + " with external ID " + externalId + " from NuageVsp. Please contact Nuage Vsp csproot to clean stale objects");
        }
    }

    public static String findEntityUsingFilter(NuageVspEntity entityType, String entityId, NuageVspEntity childEntityType, String filterValue, NuageVspAPIParams nuageVspAPIParams)
            throws NuageVspAPIUtilException {

        String jsonString = null;
        try {
            jsonString = NuageVspApi.executeRestApi(RequestType.GETALL, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), entityType, entityId,
                    childEntityType, filterValue, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                    nuageVspAPIParams.getRetryInterval(), nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
            return jsonString;
        } catch (Exception exception) {
            String errorMessage = "Failed to execute REST API call to VSP to get " + entityType + " using VSP filter " + filterValue + ".  Json response from VSP REST API is  "
                    + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static NuageVspAPIParams getNuageVspAPIParametersAsCmsUser(HostVO nuageVspHost, String nuageVspCmsId) {
        return getNuageVspAPIParameters(null, null, true, nuageVspHost, nuageVspCmsId);
    }

    public static NuageVspAPIParams getNuageVspAPIParametersAsCmsUser(Map<String, String> hostDetails, String nuageVspCmsId) {
        return getNuageVspAPIParameters(null, null, true, hostDetails, nuageVspCmsId);
    }

    public static NuageVspAPIParams getNuageVspAPIParameters(String domainUuid, String userUuid, boolean executeAsCmsUser, HostVO nuageVspHost, String nuageVspCmsId) {
        return getNuageVspAPIParameters(domainUuid, userUuid, executeAsCmsUser, nuageVspHost.getDetails(), nuageVspCmsId);
    }

    public static NuageVspAPIParams getNuageVspAPIParameters(String domainUuid, String userUuid, boolean executeAsCmsUser, Map<String, String> hostDetails, String nuageVspCmsId) {
        NuageVspAPIParams nuageVspAPIParams = null;
        nuageVspAPIParams = new NuageVspAPIParams();
        nuageVspAPIParams.setRestRelativePath(new StringBuffer().append("https://").append(hostDetails.get("hostname")).append(":").append(hostDetails.get("port"))
                .append(hostDetails.get("apirelativepath")).toString());
        nuageVspAPIParams.setCmsUserInfo(new String[] {NuageVspConstants.CMS_USER_ENTEPRISE_NAME, hostDetails.get("cmsuser"),
                org.apache.commons.codec.binary.StringUtils.newStringUtf8(Base64.decodeBase64(hostDetails.get("cmsuserpass")))});
        nuageVspAPIParams.setNoofRetry(Integer.parseInt(hostDetails.get("retrycount")));
        nuageVspAPIParams.setRetryInterval(Long.parseLong(hostDetails.get("retryinterval")));

        if (!executeAsCmsUser) {
            //Hack to remove "-" from UserUuId. This is because VSP limits the username to less than 32 character
            //and CS uuid has 32 characters
            nuageVspAPIParams.setCurrentUserName(userUuid.replaceAll("-", ""));
            nuageVspAPIParams.setCurrentUserEnterpriseName(domainUuid);
        }
        nuageVspAPIParams.setCmsUser(executeAsCmsUser);
        nuageVspAPIParams.setNuageVspCmsId(nuageVspCmsId);
        return nuageVspAPIParams;
    }

    private static boolean isVMInterfacePresent(String vmInterfacesFromVSP, String macAddress) throws NuageVspAPIUtilException {
        boolean vmInterfaceExists = false;
        List<Map<String, Object>> objectDetails = parseJson(vmInterfacesFromVSP, NuageVspEntity.VM_INTERFACE);
        for (Map<String, Object> map : objectDetails) {
            if (map.containsKey(NuageVspAttribute.VM_INTERFACE_MAC.getAttributeName())
                    && StringUtils.equals((String)map.get(NuageVspAttribute.VM_INTERFACE_MAC.getAttributeName()), macAddress)) {
                vmInterfaceExists = true;
                break;
            }
        }
        return vmInterfaceExists;
    }

    public static String getEnterpriseName(String domainName, String domainPath) {
        StringTokenizer tokens = new StringTokenizer(domainPath, "/");
        if (tokens.countTokens() <= 1) {
            return domainName;
        }

        return domainPath.substring(1, domainPath.length()).replace("/", "-");
    }

    public static String findEntityUsingFilter(NuageVspEntity entityType, String entityId, NuageVspEntity childEntityType, String filterAttrName, Object filterAttrValue,
            NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {

        return findEntityUsingFilter(entityType, entityId, childEntityType, filterAttrName + " == "
                + ((filterAttrValue instanceof String) ? "'" + filterAttrValue + "'" : filterAttrValue), nuageVspAPIParams);
    }

    public static <T> T findFieldValueByExternalUuid(NuageVspEntity entityType, String entityId, NuageVspEntity childEntityType, String externalId,
                                                     String fieldName, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        String jsonString = findEntityUsingFilter(entityType, entityId, childEntityType, NuageVspAttribute.EXTERNAL_ID.getAttributeName(), externalId, nuageVspAPIParams);
        return (T) getFieldValue(jsonString, childEntityType, fieldName);
    }

    public static String findEntityIdByExternalUuid(NuageVspEntity entityType, String entityId, NuageVspEntity childEntityType, String externalId,
            NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        String jsonString = findEntityUsingFilter(entityType, entityId, childEntityType, NuageVspAttribute.EXTERNAL_ID.getAttributeName(), externalId, nuageVspAPIParams);
        return getEntityId(jsonString, entityType);
    }

    public static String findEntityByExternalUuid(NuageVspEntity entityType, String entityId, NuageVspEntity childEntityType, String externalId, NuageVspAPIParams nuageVspAPIParams)
            throws NuageVspAPIUtilException {

        try {
            String json = findEntityUsingFilter(entityType, entityId, childEntityType, NuageVspAttribute.EXTERNAL_ID.getAttributeName(), externalId, nuageVspAPIParams);
            return json;
        } catch (Exception exception) {
            String errorMessage = "Failed to execute REST API call to VSP to get " + entityType + " using VSP filter " + externalId + ".  Json response from VSP REST API is  "
                    + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static String getEntityId(String jsonString, NuageVspEntity entityType) throws NuageVspAPIUtilException {
        String id = "";
        if (StringUtils.isNotBlank(jsonString)) {
            List<Map<String, Object>> entityDetails = parseJson(jsonString, entityType);
            id = (String)entityDetails.iterator().next().get(NuageVspAttribute.ID.getAttributeName());
        }
        return id;
    }

    public static <T> T getFieldValue(String jsonString, NuageVspEntity entityType, String fieldName) throws NuageVspAPIUtilException  {
        T fieldValue = null;
        if (StringUtils.isNotBlank(jsonString)) {
            List<Map<String, Object>> entityDetails = parseJson(jsonString, entityType);
            fieldValue = (T) entityDetails.iterator().next().get(fieldName);
        }
        return fieldValue;
    }

    public static int getChildrenCount(NuageVspEntity entityType, String entityId, NuageVspEntity childEntityType, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        String jsonString = findEntityUsingFilter(entityType, entityId, childEntityType, null, nuageVspAPIParams);
        if (StringUtils.isNotBlank(jsonString)) {
            List<Map<String, Object>> entityDetails = parseJson(jsonString, childEntityType);
            return entityDetails.size();
        }
        return 0;
    }

    public static boolean cleanUpVspStaleObjects(NuageVspEntity entityToBeCleaned, String entityIDToBeCleaned, NuageVspAPIParams nuageVspAPIParams, List<Integer> retryNuageErrorCodes) {
        try {
            NuageVspApi.executeRestApi(RequestType.DELETE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(), entityToBeCleaned,
                    entityIDToBeCleaned, null, null, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(),
                    nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), retryNuageErrorCodes, nuageVspAPIParams.getNuageVspCmsId());
            s_logger.debug("Successfully cleaned stale VSP entity " + entityToBeCleaned + " with ID " + entityIDToBeCleaned);
        } catch (Exception e) {
            s_logger.warn("Failed to clean " + entityToBeCleaned + " with ID " + entityIDToBeCleaned + " from NuageVsp. Please contact Nuage Vsp csproot to clean stale objects");
            return false;
        }
        return true;
    }

    public static boolean cleanUpVspStaleObjects(NuageVspEntity entityToBeCleaned, String entityIDToBeCleaned, NuageVspAPIParams nuageVspAPIParams) {
        return cleanUpVspStaleObjects(entityToBeCleaned, entityIDToBeCleaned, nuageVspAPIParams, null);
    }

    private static void createVportInVsp(String vmInstanceName, String vmUuid, List<Map<String, String>> vmInterfaceList, Object[] attachedNetworkDetails,
            NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {

        List<String> vPortIds = new ArrayList<String>();
        for (Map<String, String> vmInterface : vmInterfaceList) {

            String vmInterfaceUuid = vmInterface.get(NuageVspAttribute.EXTERNAL_ID.getAttributeName());
            //Create a VPort for each VM interface. This its association will not be deleted when VM is stopped
            //then set the VPortID in the VM Interface
            //Check if VPort already exists
            NuageVspEntity networkTypeToBeAttached = (NuageVspEntity)attachedNetworkDetails[0];
            String networkIdToBeAttached = (String)attachedNetworkDetails[1];
            String vPortId = NuageVspApiUtil.findEntityIdByExternalUuid(networkTypeToBeAttached, networkIdToBeAttached, NuageVspEntity.VPORT, vmInterfaceUuid, nuageVspAPIParams);
            if (StringUtils.isBlank(vPortId)) {
                Map<String, Object> vmPortEntity = new HashMap<String, Object>();
                vmPortEntity.put(NuageVspAttribute.VPORT_NAME.getAttributeName(), vmInterfaceUuid);
                vmPortEntity.put(NuageVspAttribute.VPORT_DESCRIPTION.getAttributeName(), vmInterface.get(NuageVspAttribute.VM_INTERFACE_MAC.getAttributeName()));
                vmPortEntity.put(NuageVspAttribute.VPORT_ACTIVE.getAttributeName(), true);
                vmPortEntity.put(NuageVspAttribute.VPORT_TYPE.getAttributeName(), "VM");
                vmPortEntity.put(NuageVspAttribute.VPORT_ADDRESSSPOOFING.getAttributeName(), "INHERITED");
                vmPortEntity.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), vmInterfaceUuid);

                try {
                    String vPortJsonString = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                            networkTypeToBeAttached, networkIdToBeAttached, NuageVspEntity.VPORT, vmPortEntity, null, nuageVspAPIParams.getRestRelativePath(),
                            nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), true, nuageVspAPIParams.isCmsUser(),
                            nuageVspAPIParams.getNuageVspCmsId());
                    s_logger.debug("Created VPort for network " + networkTypeToBeAttached + " with ID " + networkIdToBeAttached + " in Nuage. Response from VSP is "
                            + vPortJsonString);
                    vPortId = getEntityId(vPortJsonString, NuageVspEntity.VPORT);
                    vPortIds.add(vPortId);
                } catch (Exception e) {
                    String errorMessage = "Failed to create VPort in VSP using REST API. Json response from VSP REST API is  " + e.getMessage();
                    s_logger.error(errorMessage, e);
                    //clean up all the previously created VPorts
                    for (String vportId : vPortIds) {
                        cleanUpVspStaleObjects(NuageVspEntity.VPORT, vportId, nuageVspAPIParams);
                    }
                    throw new NuageVspAPIUtilException(errorMessage);
                }
            }
            //Set the VPort information in then VMInsterfaceList
            vmInterface.put(NuageVspAttribute.VM_INTERFACE_VPORT_ID.getAttributeName(), vPortId);
        }
    }

    public static List<Map<String, Object>> parseJson(String jsonString, NuageVspEntity nuageEntityType) throws NuageVspAPIUtilException {
        try {
            return NuageVspApi.parseJsonString(nuageEntityType, jsonString);
        } catch (Exception exception) {
            s_logger.error("Failed to parse the Json response from VSP REST API. Json string is  " + jsonString, exception);
            throw new NuageVspAPIUtilException("Failed to parse the VM Json response from VSP REST API. VM Json string is  " + jsonString);
        }
    }

    private static String getPortRange(String protocolType) {
        return getPortRange(null, null, protocolType);
    }

    private static String getPortRange(Integer startPort, Integer endPort, String protocolType) {
        String portRange = NuageVspConstants.STAR;
        if (endPort == null) {
            if (startPort != null) {
                portRange = String.valueOf(startPort);
            }
        } else {
            portRange = startPort + "-" + endPort;
        }

        if (isTCPOrUDP(protocolType)) {
            return portRange;
        }

        return null;
    }

    private static String getProtocolNumber(String protocolType) {
        if (StringUtils.equalsIgnoreCase(protocolType, "TCP") || StringUtils.equals(protocolType, "6")) {
            return String.valueOf(6);
        } else if (StringUtils.equalsIgnoreCase(protocolType, "UDP") || StringUtils.equals(protocolType, "17")) {
            return String.valueOf(17);
        } else if (StringUtils.equalsIgnoreCase(protocolType, "ICMP") || StringUtils.equals(protocolType, "1")) {
            return String.valueOf(1);
        } else if (StringUtils.equalsIgnoreCase(protocolType, "ALL")) {
            return NuageVspConstants.ANY;
        } else {
            return protocolType;
        }

    }

    private static boolean isTCPOrUDP(String protocolType) {
        return StringUtils.equalsIgnoreCase(protocolType, "TCP") || StringUtils.equals(protocolType, "6") || StringUtils.equalsIgnoreCase(protocolType, "UDP")
                || StringUtils.equals(protocolType, "17");
    }

    public static Map<String, Map<String, Object>> filterDefaultACLEntries(List<Map<String, Object>> aclEntries) {
        Map<String, Map<String, Object>> externalUuidToAcl = new HashMap<String, Map<String, Object>>();
        for (Map<String, Object> entry : aclEntries) {
            int aclPriority = (Integer)entry.get(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_PRIORITY.getAttributeName());
            String externalUuid = (String)entry.get(NuageVspAttribute.EXTERNAL_ID.getAttributeName());
            if (!isDefaultPriorityACL(aclPriority)) {
                externalUuidToAcl.put(externalUuid, entry);
            }
        }
        return externalUuidToAcl;
    }

    public static Map<Integer, Map<String, Object>> getDefaultAclEntries(boolean isIngress, String aclTempId, NuageVspAPIParams nuageVspAPIParamsAsCmsUser) throws Exception {
        List<Map<String, Object>> defaultVspAclEntries = NuageVspApiUtil.getACLEntriesAssociatedToLocation(null, isIngress ? NuageVspEntity.INGRESS_ACLTEMPLATES
                : NuageVspEntity.EGRESS_ACLTEMPLATES, aclTempId, nuageVspAPIParamsAsCmsUser);
        Map<Integer, Map<String, Object>> aclPriorityToEntry = new Hashtable<Integer, Map<String, Object>>();
        for (Map<String, Object> aclEntry : defaultVspAclEntries) {
            aclPriorityToEntry.put((Integer)aclEntry.get(NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_PRIORITY.getAttributeName()), aclEntry);
        }
        return aclPriorityToEntry;
    }

    public static boolean isDefaultPriorityACL(int priority) {
        return (priority == NuageVspConstants.DEFAULT_TCP_ALLOW_ACL_PRIORITY) || (priority == NuageVspConstants.DEFAULT_UDP_ALLOW_ACL_PRIORITY)
                || (priority == NuageVspConstants.DEFAULT_ICMP_ALLOW_ACL_PRIORITY) || (priority == NuageVspConstants.DEFAULT_DOMAIN_BLOCK_ACL_PRIORITY)
                || (priority == NuageVspConstants.DEFAULT_SUBNET_ALLOW_ACL_PRIORITY || (priority >= NuageVspConstants.SUBNET_BLOCK_ACL_PRIORITY));
    }

    public static Configuration getConfigProperties() {
        return configProps;
    }

    public static int getRandomPriority() {
        Random random = new Random();
        return ((int)(random.nextDouble() * 99999) % 1000000) + 1;
    }

    //TODO: use the new executeAPI with nuageRetryErrorCodes API to make it very generic
    private static String createAclEntriesWithRetry(boolean isNetworkAcl, NuageVspEntity aclTemplateType, NuageVspEntity aclTemplateEntryType, String vsdEnterpriseId, ACLRule rule, String sourceIp,
            String sourceIpUuid, long networkId, String aclNetworkLocationId, String sourceCidr, String aclTemplateId, NuageVspAPIParams nuageVspAPIParams) throws Exception {
        int attempt = 1;
        long delayFactor = 2;
        long sleepTime = nuageVspAPIParams.getRetryInterval();
        Exception exception = null;
        do {
            try {
                String aclEntryJson = null;
                Map<String, Object> aclEntryEntity = null;
                boolean isEgress = aclTemplateType.equals(NuageVspEntity.EGRESS_ACLTEMPLATES);
                aclEntryEntity = isEgress ? getEgressAclEntry(vsdEnterpriseId, rule, sourceIp, aclNetworkLocationId, networkId, nuageVspAPIParams, sourceCidr,
                        false, -1) : getIngressAclEntry(vsdEnterpriseId, rule, aclNetworkLocationId, networkId, nuageVspAPIParams, sourceCidr, false, -1);
                //create Egress/Ingress ACL Entry
                aclEntryJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                        aclTemplateType, aclTemplateId, aclTemplateEntryType, aclEntryEntity, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(),
                        nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
                s_logger.debug("Created " + aclTemplateEntryType + " ACL Entry " + (rule != null ? (" for rule " + rule + " with CIDR " + sourceCidr) : "")
                        + " in VSP. Response from VSP is " + aclEntryJson);

                if (attempt > 1) {
                    s_logger.trace(String.format("After %s attempt, ACL Entry was re-created successfully ", --attempt));
                }
                return getEntityId(aclEntryJson, aclTemplateEntryType);
            } catch (Exception e) {
                exception = e;
                if (attempt >= 1) {
                    if (attempt <= nuageVspAPIParams.getNoofRetry()) {
                        if (!isNetworkAcl
                                && (exception instanceof NuageVspException && ((NuageVspException)exception).getHttpErrorCode() == 409 && ((NuageVspException)exception)
                                        .getNuageErrorCode() == NuageVspApi.s_duplicateAclPriority/*2591*/)) {
                            s_logger.debug("Failed to Create ACL Entry for rule " + rule + " in VSP enterprise " + vsdEnterpriseId + ". " + exception.getMessage());
                            s_logger.debug(String.format("Attempt %s to re-create ACL Entry", attempt));
                            try {
                                Thread.sleep(sleepTime);
                            } catch (InterruptedException e1) {
                                s_logger.warn("Retry sleeping got interrupted");
                                throw e;
                            }
                        } else {
                            throw e;
                        }
                    }

                    attempt++;
                    sleepTime *= delayFactor;
                }
            }
        } while (attempt <= nuageVspAPIParams.getNoofRetry() + 1);

        s_logger.error(String.format("Failed to Create ACL Entry even after %s attempts, due to exception %s ", nuageVspAPIParams.getNoofRetry(), exception.getMessage()));
        throw exception;
    }

    public static Map<ACLRule, List<String>> createOrDeleteDefaultIngressSubnetBlockAcl(String networkName, String vpcOrSubnetUuid, Long networkId,
            NuageVspAPIParams nuageVspAPIParamsAsCmsUser, String aclNetworkLocationId, String networkUuid, String ingressACLTempId, int noOfActiveOrAddedIngressAcls) {
        Map<ACLRule, List<String>> errorMap = new HashMap<ACLRule, List<String>>();
        List<String> errorMessages = new ArrayList<String>();
        try {
            int priority = (int)(NuageVspConstants.SUBNET_BLOCK_ACL_PRIORITY + networkId);
            //Add the default subnet block acl if it is not added get the IngressACLEntry with default priority
            String defaultIngressAcl = NuageVspApiUtil.findEntityUsingFilter(NuageVspEntity.INGRESS_ACLTEMPLATES, ingressACLTempId, NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES,
                    NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_PRIORITY.getAttributeName(), priority, nuageVspAPIParamsAsCmsUser);
            if (noOfActiveOrAddedIngressAcls > 0) {
                if (StringUtils.isBlank(defaultIngressAcl)) {
                    //Create the ACL with default priority
                    try {
                        NuageVspApiUtil.createDefaultACLEntry(vpcOrSubnetUuid, ingressACLTempId, true, false, NuageVspConstants.ANY, NuageVspConstants.ACL_ACTION_DROP, priority,
                                NuageVspConstants.SUBNET_BLOCK_ACL, NuageVspConstants.SUBNET, aclNetworkLocationId, NuageVspConstants.ANY, networkName, nuageVspAPIParamsAsCmsUser);
                        s_logger.debug("Default ACL to block subnets traffic is added with priority " + priority);
                    } catch (Exception e) {
                        if (e instanceof NuageVspException && ((NuageVspException)e).getHttpErrorCode() == 409 && ((NuageVspException)e).getNuageErrorCode() == NuageVspApi.s_duplicateAclPriority/*2591*/) {
                            s_logger.debug("Looks like the ACL Entry with priority " + priority + " already exists. So, it is not re-created");
                        }
                    }
                }
            } else {
                if (!StringUtils.isBlank(defaultIngressAcl)) {
                    List<Map<String, Object>> ingressAclEntry = parseJson(defaultIngressAcl, NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES);
                    s_logger.debug("There are no Egress ACLs added to the network " + networkName + ". So, delete default subnet block ACL");
                    cleanUpVspStaleObjects(NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES, (String)ingressAclEntry.iterator().next().get(NuageVspAttribute.ID.getAttributeName()),
                            nuageVspAPIParamsAsCmsUser);
                }
            }

        } catch (Exception e) {
            errorMessages.add("Failed to create Subnet for network " + networkName + ".  Json response from VSP REST API is " + e.getMessage());
            NetworkACLItemVO rule = new NetworkACLItemVO(0, 0, NuageVspConstants.STAR, 0, new ArrayList<String>(1), 0, 0, NetworkACLItem.TrafficType.Ingress, Action.Deny, 0);
            rule.setState(NetworkACLItem.State.Add);
            errorMap.put(new ACLRule(rule, false), errorMessages);
        }
        return errorMap;
    }

    public static void applyStaticNatInVSP(String networkName, String networkUuid, NuageVspAPIParams nuageVspAPIParamsAsCmsUser, String attachedL2DomainOrDomainId, NuageVspEntity attachedNetworkType,
            String vspNetworkId, String vpcOrSubnetUuid, boolean isVpc, String staticNatIpAddress, String staticNatIpUuid, String staticNatVlanGateway, String staticNatVlanNetmask,
            String staticNatVanUuid, String nicIp4Address, String nicUuid, String vportId, String domainId) throws NuageVspAPIUtilException {
        //check if the SharedNetwork exists in Vsp
        String vspSharedNetworkJson = NuageVspApiUtil.findEntityUsingFilter(NuageVspEntity.SHARED_NETWORK, null, null,
                NuageVspAttribute.SHARED_RESOURCE_NAME.getAttributeName(), staticNatVanUuid, nuageVspAPIParamsAsCmsUser);
        String vspSharedNetworkId = null;
        if (StringUtils.isBlank(vspSharedNetworkJson)) {
            vspSharedNetworkId = NuageVspApiUtil.createSharedResourceInVSP(staticNatVanUuid, staticNatVlanGateway,
                    staticNatVlanNetmask, nuageVspAPIParamsAsCmsUser);
        } else {
            vspSharedNetworkId = NuageVspApiUtil.getEntityId(vspSharedNetworkJson, NuageVspEntity.SHARED_NETWORK);
        }
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Static NAT " + staticNatIpAddress + "(" + staticNatIpUuid + ") associated to network " + networkName
                    + " is attached to the VM interface " + nicIp4Address + ". So, create a new Floating IP " + staticNatIpAddress
                    + " in FloatingIP shared resource " + vspSharedNetworkId + " and associate it to VM with VPort " + vportId);
        }
        if (vportId != null) {
            NuageVspApiUtil.allocateFIPToVPortInVsp(staticNatIpAddress, staticNatIpUuid, networkUuid,
                    vspSharedNetworkId, domainId != null ? domainId : attachedL2DomainOrDomainId, vportId, attachedNetworkType, nuageVspAPIParamsAsCmsUser, vpcOrSubnetUuid, isVpc);
        } else {
            if (attachedNetworkType.equals(NuageVspEntity.DOMAIN)) {
                String vportIdUsingNICUuid = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.SUBNET, vspNetworkId, NuageVspEntity.VPORT, nicUuid,
                        nuageVspAPIParamsAsCmsUser);
                if (StringUtils.isNotBlank(vportIdUsingNICUuid)) {
                    s_logger.warn("NIC associated to Static NAT " + staticNatIpAddress + "(" + staticNatIpUuid + ") is not present in VSD. But, VM's VPort"
                            + " with externalID " + nicUuid + " exists in VSD. So, associate the FIP to the Vport " + vportIdUsingNICUuid);
                    NuageVspApiUtil.allocateFIPToVPortInVsp(staticNatIpAddress, staticNatIpUuid, networkUuid,
                            vspSharedNetworkId, domainId != null ? domainId : attachedL2DomainOrDomainId, vportIdUsingNICUuid, attachedNetworkType, nuageVspAPIParamsAsCmsUser, vpcOrSubnetUuid, isVpc);
                } else {
                    StringBuffer errorMessage = new StringBuffer();
                    errorMessage.append("Static NAT ").append(staticNatIpAddress).append("(" + staticNatIpUuid + ") associated to network ").append(networkName)
                            .append(" is not associated to the VM interface because neither the interface nor the VPort with NIC's UUID ").append(nicUuid).append(" is not present in VSD");
                    s_logger.warn(errorMessage);
                }
            }
        }
    }

    public static List<String> cleanStaleAclsFromVsp(List<? extends InternalIdentity> rules, Map<String, Map<String, Object>> vspIngressAclEntriesExtUuidToAcl,
            Map<String, Map<String, Object>> vspEgressAclEntriesExtUuidToAcl, Boolean acsIngressFirewall, NuageVspAPIParams nuageVspAPIParamsAsCmsUser) {
        List<String> cleanedUpVspAclEntryIds = new ArrayList<String>();

        if (acsIngressFirewall != null) {
            //In Firewall case, either Ingress or Egress rules is push down so check against only one ACl type to avoid cleaning up of the other as other list will be empty
            if (acsIngressFirewall) {
                for (Map.Entry<String, Map<String, Object>> egressAclEntry : vspEgressAclEntriesExtUuidToAcl.entrySet()) {
                    if (!doesACLExistsInIngressList(rules, egressAclEntry.getKey())) {
                        String id = (String)egressAclEntry.getValue().get(NuageVspAttribute.ID.getAttributeName());
                        cleanUpVspStaleObjects(NuageVspEntity.EGRESS_ACLTEMPLATES_ENTRIES, id, nuageVspAPIParamsAsCmsUser);
                        cleanedUpVspAclEntryIds.add(id);
                    }
                }
            } else {
                for (Map.Entry<String, Map<String, Object>> ingressAclEntry : vspIngressAclEntriesExtUuidToAcl.entrySet()) {
                    if (!doesACLExistsInEgressList(rules, ingressAclEntry.getKey())) {
                        String id = (String)ingressAclEntry.getValue().get(NuageVspAttribute.ID.getAttributeName());
                        cleanUpVspStaleObjects(NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES, id, nuageVspAPIParamsAsCmsUser);
                        cleanedUpVspAclEntryIds.add(id);
                    }
                }
            }
        }
        //This is networkAcl Case. rules contain both ingress and Egress list so check both the tables
        else {
            for (Map.Entry<String, Map<String, Object>> egressAclEntry : vspEgressAclEntriesExtUuidToAcl.entrySet()) {
                if (!doesACLExistsInIngressList(rules, egressAclEntry.getKey())) {
                    String id = (String)egressAclEntry.getValue().get(NuageVspAttribute.ID.getAttributeName());
                    cleanUpVspStaleObjects(NuageVspEntity.EGRESS_ACLTEMPLATES_ENTRIES, id, nuageVspAPIParamsAsCmsUser);
                    cleanedUpVspAclEntryIds.add(id);
                }
            }
            for (Map.Entry<String, Map<String, Object>> ingressAclEntry : vspIngressAclEntriesExtUuidToAcl.entrySet()) {
                if (!doesACLExistsInEgressList(rules, ingressAclEntry.getKey())) {
                    String id = (String)ingressAclEntry.getValue().get(NuageVspAttribute.ID.getAttributeName());
                    cleanUpVspStaleObjects(NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES, id, nuageVspAPIParamsAsCmsUser);
                    cleanedUpVspAclEntryIds.add(id);
                }
            }
        }

        return cleanedUpVspAclEntryIds;
    }

    private static boolean doesACLExistsInEgressList(List<? extends InternalIdentity> rules, String aclEntryEnternalUuid) {
        for (InternalIdentity rule : rules) {
            if ((rule instanceof FirewallRule && ((FirewallRule)rule).getTrafficType().equals(FirewallRule.TrafficType.Egress))
                    || (rule instanceof NetworkACLItem && ((NetworkACLItem)rule).getTrafficType().equals(NetworkACLItem.TrafficType.Egress))) {
                if (isSameACL(aclEntryEnternalUuid, rule)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean doesACLExistsInIngressList(List<? extends InternalIdentity> rules, String aclEntryEnternalUuid) {
        for (InternalIdentity rule : rules) {
            if ((rule instanceof FirewallRule && ((FirewallRule)rule).getTrafficType().equals(FirewallRule.TrafficType.Ingress))
                    || (rule instanceof NetworkACLItem && ((NetworkACLItem)rule).getTrafficType().equals(NetworkACLItem.TrafficType.Ingress))) {
                if (isSameACL(aclEntryEnternalUuid, rule)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSameACL(String aclEntryEnternalUuid, InternalIdentity rule) {
        return (rule instanceof FirewallRule && ((FirewallRule)rule).getUuid().equals(aclEntryEnternalUuid))
                || (rule instanceof NetworkACLItem && ((NetworkACLItem)rule).getUuid().equals(aclEntryEnternalUuid));
    }

    public static String generateCmsIdForNuageVsp(String cmsName, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        try {
            // Get Cloud Management System ID
            Map<String, Object> entity = new HashMap<String, Object>();
            entity.put(NuageVspAttribute.CLOUD_MGMT_SYSTEM_NAME.getAttributeName(), cmsName);

            String cmsJson = NuageVspApi.executeRestApi(RequestType.CREATE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                    NuageVspEntity.CLOUD_MGMT_SYSTEMS, entity, nuageVspAPIParams.getRestRelativePath(),
                    nuageVspAPIParams.getCmsUserInfo(), nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), nuageVspAPIParams.isCmsUser(),
                    nuageVspAPIParams.getNuageVspCmsId());
            s_logger.debug("Retrieved CMS ID for VSP . Response from VSP is " + cmsJson);
            return getEntityId(cmsJson, NuageVspEntity.CLOUD_MGMT_SYSTEMS);
        } catch (Exception exception) {
            String errorMessage = "Failed to retrieve CMS ID VSP. Response from VSP REST API is  " + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static boolean removeCmsIdForNuageVsp(String cmsId, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        try {
            NuageVspApi.executeRestApi(RequestType.DELETE, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                    NuageVspEntity.CLOUD_MGMT_SYSTEMS, cmsId, null, null, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(),
                    nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
            s_logger.debug("Deleted CMS ID for VSP.");
            return true;
        } catch (Exception exception) {
            String errorMessage = "Failed to delete CMS ID VSP. Response from VSP REST API is  " + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

    public static boolean isSupportedApiVersion(String version) {
        return isSupportedApiVersion(new NuageVspApiVersion(version));
    }

    public static boolean isSupportedApiVersion(NuageVspApiVersion version) {
        return version.compareTo(NuageVspApiVersion.V3_2) >= 0;
    }

    public static boolean isKnownCmsIdForNuageVsp(String cmsId, NuageVspAPIParams nuageVspAPIParams) throws NuageVspAPIUtilException {
        try {
            NuageVspApi.executeRestApi(RequestType.GET, nuageVspAPIParams.getCloudstackDomainName(), nuageVspAPIParams.getCurrentUserName(),
                    NuageVspEntity.CLOUD_MGMT_SYSTEMS, cmsId, null, null, null, nuageVspAPIParams.getRestRelativePath(), nuageVspAPIParams.getCmsUserInfo(),
                    nuageVspAPIParams.getNoofRetry(), nuageVspAPIParams.getRetryInterval(), false, nuageVspAPIParams.isCmsUser(), nuageVspAPIParams.getNuageVspCmsId());
            return true;
        } catch (Exception exception) {
            if (exception instanceof NuageVspException && ((NuageVspException) exception).getHttpErrorCode() == 404) {
                return false;
            }

            String errorMessage = "Failed to retrieve CMS ID VSP. Response from VSP REST API is  " + exception.getMessage();
            s_logger.error(errorMessage, exception);
            throw new NuageVspAPIUtilException(errorMessage);
        }
    }

}
