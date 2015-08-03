package net.nuage.vsp.client.common.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum NuageVspEntity {
    ME("me", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.APIKEY),
    ENTERPRISE("enterprises", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.ENTERPRISE_NAME, NuageVspAttribute.ENTERPRISE_DESCRIPTION, NuageVspAttribute.ENTERPRISE_PROFILE_ID),
    GROUP("groups", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.GROUP_NAME, NuageVspAttribute.GROUP_DESCRIPTION),
    USER("users", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.USER_USERNAME, NuageVspAttribute.USER_DESCRIPTION, NuageVspAttribute.USER_EMAIL, NuageVspAttribute.USER_PASSWORD,
            NuageVspAttribute.USER_FIRSTNAME, NuageVspAttribute.USER_LASTNAME),
    L2DOMAIN_TEMPLATE("l2domaintemplates", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.L2DOMAIN_TEMPLATE_NAME, NuageVspAttribute.L2DOMAIN_TEMPLATE_DESCRIPTION),
    DOMAIN_TEMPLATE("domaintemplates", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.DOMAIN_TEMPLATE_NAME, NuageVspAttribute.DOMAIN_TEMPLATE_DESCRIPTION),
    ZONE_TEMPLATE("zonetemplates", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.ZONE_TEMPLATE_NAME, NuageVspAttribute.ZONE_TEMPLATE_DESCRIPTION),
    INGRESS_ACLTEMPLATES("ingressacltemplates", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.ACLTEMPLATES_NAME, NuageVspAttribute.ACLTEMPLATES_DESCRIPTION,
            NuageVspAttribute.ACLTEMPLATES_ACTIVE, NuageVspAttribute.ACLTEMPLATES_ALLOW_IP, NuageVspAttribute.ACLTEMPLATES_ALLOW_NON_IP, NuageVspAttribute.ACLTEMPLATES_ENTRIES),
    INGRESS_ACLTEMPLATES_ENTRIES("ingressaclentrytemplates", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.PARENT_ID, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_ACTION,
            NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_DEST_PORT, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_ETHER_TYPE, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_LOCATION_TYPE,
            NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_LOCATION_ID, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_NETWORK_TYPE, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_NETWORK_ID,
            NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_PRIORITY, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_PROTOCOL, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_SOURCE_PORT,
            NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_ADDR_OVERRIDE, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_REFLEXIVE, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_DSCP, NuageVspAttribute.INGRESS_ACLTEMPLATES_ENTRY_DESCRIPTION),
    EGRESS_ACLTEMPLATES("egressacltemplates", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.ACLTEMPLATES_NAME, NuageVspAttribute.ACLTEMPLATES_DESCRIPTION,
            NuageVspAttribute.ACLTEMPLATES_ACTIVE, NuageVspAttribute.ACLTEMPLATES_ALLOW_IP, NuageVspAttribute.ACLTEMPLATES_ALLOW_NON_IP, NuageVspAttribute.ACLTEMPLATES_ENTRIES),
    EGRESS_ACLTEMPLATES_ENTRIES("egressaclentrytemplates", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.PARENT_ID, NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_ACTION,
            NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_DEST_PORT, NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_ETHER_TYPE, NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_LOCATION_TYPE,
            NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_LOCATION_ID, NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_NETWORK_TYPE, NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_NETWORK_ID,
            NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_PRIORITY, NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_PROTOCOL, NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_SOURCE_PORT,
            NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_ADDR_OVERRIDE, NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_REFLEXIVE, NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_DSCP,
            NuageVspAttribute.EGRESS_ACLTEMPLATES_ENTRY_DESCRIPTION),
    DOMAIN("domains", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.DOMAIN_NAME, NuageVspAttribute.DOMAIN_DESCRIPTION, NuageVspAttribute.DOMAIN_TEMPLATE_ID),
    ZONE("zones", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.ZONE_NAME, NuageVspAttribute.ZONE_DESCRIPTION),
    SUBNET("subnets", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.SUBNET_NAME, NuageVspAttribute.SUBNET_DESCRIPTION, NuageVspAttribute.SUBNET_ADDRESS,
            NuageVspAttribute.SUBNET_NETMASK, NuageVspAttribute.SUBNET_GATEWAY),
    ADDRESS_RANGE("addressranges", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.ADDRESS_RANGE_MIN, NuageVspAttribute.ADDRESS_RANGE_MAX),
    L2DOMAIN("l2domains", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.L2DOMAIN_NAME, NuageVspAttribute.L2DOMAIN_DESCRIPTION, NuageVspAttribute.L2DOMAIN_ADDRESS,
            NuageVspAttribute.L2DOMAIN_NETMASK, NuageVspAttribute.L2DOMAIN_GATEWAY),
    VM("vms", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.VM_NAME, NuageVspAttribute.VM_DESCRIPTION, NuageVspAttribute.VM_UUID, NuageVspAttribute.VM_STATUS,
            NuageVspAttribute.VM_INTERFACES),
    VM_INTERFACE("vminterfaces", NuageVspAttribute.ID, NuageVspAttribute.VM_INTERFACE_NAME, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.VM_DESCRIPTION, NuageVspAttribute.VM_INTERFACE_MAC, NuageVspAttribute.VM_INTERFACE_ATTACHED_NETWORK_ID,
            NuageVspAttribute.VM_INTERFACE_ATTACHED_NETWORK_TYPE, NuageVspAttribute.VM_INTERFACE_GATEWAY, NuageVspAttribute.VM_INTERFACE_IPADDRESS, NuageVspAttribute.VM_INTERFACE_NETMASK,
            NuageVspAttribute.VM_INTERFACE_DOMAIN_ID, NuageVspAttribute.VM_INTERFACE_VPORT_ID),
    SHARED_NETWORK("sharednetworkresources", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.SHARED_RESOURCE_ADRESS, NuageVspAttribute.SHARED_RESOURCE_GATEWAY, NuageVspAttribute.SHARED_RESOURCE_NAME,
            NuageVspAttribute.SHARED_RESOURCE_NETMASK, NuageVspAttribute.SHARED_RESOURCE_TYPE),
    FLOATING_IP("floatingips", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.FLOATING_IP_ADDRESS, NuageVspAttribute.FLOATING_IP_ASSOC_SHARED_NTWK_ID, NuageVspAttribute.FLOATING_IP_ASSIGNED),
    VPORT("vports", NuageVspAttribute.ID, NuageVspAttribute.EXTERNAL_ID, NuageVspAttribute.VPORT_DESCRIPTION, NuageVspAttribute.VPORT_FLOATING_IP_ID),
    ENTERPRISE_NTWK_MACRO("enterprisenetworks", NuageVspAttribute.ID, NuageVspAttribute.ENTERPRISE_NTWK_MACRO_NAME, NuageVspAttribute.ENTERPRISE_NTWK_MACRO_DESCRIPTION, NuageVspAttribute.ENTERPRISE_NTWK_MACRO_ADDRESS,
            NuageVspAttribute.ENTERPRISE_NTWK_MACRO_NETMASK, NuageVspAttribute.EXTERNAL_ID),
    ENTERPRISE_PROFILE("enterpriseprofiles", NuageVspAttribute.ID, NuageVspAttribute.ENTERPRISE_PROFILE_NAME, NuageVspAttribute.ENTERPRISE_PROFILE_DESCRIPTION, NuageVspAttribute.ENTERPRISE_PROFILE_ADV_QOS,
            NuageVspAttribute.ENTERPRISE_PROFILE_FLOATING_IP_QUOTA, NuageVspAttribute.ENTERPRISE_PROFILE_FWD_CLASSES, NuageVspAttribute.ENTERPRISE_PROFILE_GATEWAY_MGMT, NuageVspAttribute.EXTERNAL_ID),
    DHCP_OPTIONS("dhcpoptions", NuageVspAttribute.ID, NuageVspAttribute.DHCP_OPTIONS_LENGTH, NuageVspAttribute.DHCP_OPTIONS_TYPE, NuageVspAttribute.DHCP_OPTIONS_VALUE, NuageVspAttribute.EXTERNAL_ID),
    GATEWAY("gateways", NuageVspAttribute.ID, NuageVspAttribute.GATEWAY_SYSTEMID),
    WAN_SERVICES("services", NuageVspAttribute.ID, NuageVspAttribute.WAN_SERVICE_VPN_CONNECT_ID),
    VPN_CONNECTION("vpnconnections", NuageVspAttribute.ID, NuageVspAttribute.VPN_CONNECTION_WANSERVICE_ID, NuageVspAttribute.VPN_CONNECTION_WANSERVICE_NAME),
    ENTERPRISEPERMISSION("enterprisepermissions", NuageVspAttribute.ID, NuageVspAttribute.ENTERPRISEPERMISSION_PERMITTED_ENTITYID, NuageVspAttribute.ENTERPRISEPERMISSION_PERMITTED_ENTITYYPE, NuageVspAttribute.ENTERPRISEPERMISSION_PERMITTED_ACTION),
    CLOUD_MGMT_SYSTEMS("cms", NuageVspAttribute.ID)
    ;

    private String entityType;

    private NuageVspAttribute[] attributes;

    private NuageVspEntity(String entityType, NuageVspAttribute... attributes) {
        this.entityType = entityType;
        this.attributes = attributes;
    }

    private static Map<String, NuageVspEntity> lookup = new HashMap<String, NuageVspEntity>();

    static {
        for (NuageVspEntity entity : NuageVspEntity.values()) {
            lookup.put(entity.getEntityType(), entity);
        }
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public NuageVspAttribute[] getAttributes() {
        return attributes;
    }

    public void setAttributes(NuageVspAttribute[] attributes) {
        this.attributes = attributes;
    }

    public static List<NuageVspAttribute> getAttributes(String entityType) {
        NuageVspEntity entity = lookup.get(entityType);
        return Arrays.asList(entity.getAttributes());
    }

    public static NuageVspEntity lookup(String entityType) throws Exception {
        NuageVspEntity nuageEntity = lookup.get(entityType);
        if (nuageEntity == null) {
            throw new Exception("Entity " + entityType + " is not supported by Nuage");
        }
        return nuageEntity;
    }

}
