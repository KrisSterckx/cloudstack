package net.nuage.vsp.client.common.model;

import java.util.HashMap;
import java.util.Map;

public enum NuageVspAttribute {

    //ID
    ID("ID"),

    //ExtenalID
    EXTERNAL_ID("externalID"),

    //parentID
    PARENT_ID("parentID"),

    //Me
    APIKEY("APIKey"),

    //Enterprise
    ENTERPRISE_NAME("name"),
    ENTERPRISE_DESCRIPTION("description"),
    ENTERPRISE_PROFILE_ID("enterpriseProfileID"),

    //Group
    GROUP_NAME("name"),
    GROUP_DESCRIPTION("description"),
    GROUP_PRIVATE("private"),

    //User
    USER_USERNAME("userName"),
    USER_FIRSTNAME("firstName"),
    USER_LASTNAME("lastName"),
    USER_PASSWORD("password"),
    USER_EMAIL("email"),
    USER_DESCRIPTION("description"),

    //Domain Template
    DOMAIN_TEMPLATE_NAME("name"),
    DOMAIN_TEMPLATE_DESCRIPTION("description"),

    //L2Domain Template
    L2DOMAIN_TEMPLATE_NAME("name"),
    L2DOMAIN_TEMPLATE_DESCRIPTION("description"),
    L2DOMAIN_TEMPLATE_ADDRESS("address"),
    L2DOMAIN_TEMPLATE_NETMASK("netmask"),
    L2DOMAIN_TEMPLATE_GATEWAY("gateway"),

    //Zone Template
    ZONE_TEMPLATE_NAME("name"),
    ZONE_TEMPLATE_DESCRIPTION("description"),

    //Domain
    DOMAIN_NAME("name"),
    DOMAIN_DESCRIPTION("description"),
    DOMAIN_TEMPLATE_ID("templateID"),

    //Zone
    ZONE_NAME("name"),
    ZONE_DESCRIPTION("description"),

    //Subnet
    SUBNET_NAME("name"),
    SUBNET_DESCRIPTION("description"),
    SUBNET_ADDRESS("address"),
    SUBNET_NETMASK("netmask"),
    SUBNET_GATEWAY("gateway"),

    //Address Range
    ADDRESS_RANGE_MIN("minAddress"),
    ADDRESS_RANGE_MAX("maxAddress"),

    //L2Domain
    L2DOMAIN_NAME("name"),
    L2DOMAIN_DESCRIPTION("description"),
    L2DOMAIN_ADDRESS("address"),
    L2DOMAIN_NETMASK("netmask"),
    L2DOMAIN_GATEWAY("gateway"),
    L2DOMAIN_TEMPLATE_ID("templateID"),

    DHCP_OPTIONS_LENGTH("length"),
    DHCP_OPTIONS_TYPE("type"),
    DHCP_OPTIONS_VALUE("value"),

    //ACL TEMPLATES
    ACLTEMPLATES_PRIORITY_TYPE("priorityType"),
    ACLTEMPLATES_NAME("name"),
    ACLTEMPLATES_DESCRIPTION("description"),
    ACLTEMPLATES_ALLOW_NON_IP("defaultAllowNonIP"),
    ACLTEMPLATES_ALLOW_IP("defaultAllowIP"),
    ACLTEMPLATES_ACTIVE("active"),
    ACLTEMPLATES_ENTRIES("ACLEntries"),

    //Ingress ACL Template Entries
    INGRESS_ACLTEMPLATES_ENTRY_LOCATION_TYPE("locationType"),
    INGRESS_ACLTEMPLATES_ENTRY_LOCATION_ID("locationID"),
    INGRESS_ACLTEMPLATES_ENTRY_NETWORK_TYPE("networkType"),
    INGRESS_ACLTEMPLATES_ENTRY_NETWORK_ID("networkID"),
    INGRESS_ACLTEMPLATES_ENTRY_SOURCE_PORT("sourcePort"),
    INGRESS_ACLTEMPLATES_ENTRY_DEST_PORT("destinationPort"),
    INGRESS_ACLTEMPLATES_ENTRY_PROTOCOL("protocol"),
    INGRESS_ACLTEMPLATES_ENTRY_PRIORITY("priority"),
    INGRESS_ACLTEMPLATES_ENTRY_ACTION("action"),
    INGRESS_ACLTEMPLATES_ENTRY_ETHER_TYPE("etherType"),
    INGRESS_ACLTEMPLATES_ENTRY_ADDR_OVERRIDE("addressOverride"),
    INGRESS_ACLTEMPLATES_ENTRY_REFLEXIVE("reflexive"),
    INGRESS_ACLTEMPLATES_ENTRY_DSCP("DSCP"),
    INGRESS_ACLTEMPLATES_ENTRY_DESCRIPTION("description"),

    //Egress ACL Template Entry
    EGRESS_ACLTEMPLATES_ENTRY_LOCATION_TYPE("locationType"),
    EGRESS_ACLTEMPLATES_ENTRY_LOCATION_ID("locationID"),
    EGRESS_ACLTEMPLATES_ENTRY_NETWORK_TYPE("networkType"),
    EGRESS_ACLTEMPLATES_ENTRY_NETWORK_ID("networkID"),
    EGRESS_ACLTEMPLATES_ENTRY_SOURCE_PORT("sourcePort"),
    EGRESS_ACLTEMPLATES_ENTRY_DEST_PORT("destinationPort"),
    EGRESS_ACLTEMPLATES_ENTRY_PROTOCOL("protocol"),
    EGRESS_ACLTEMPLATES_ENTRY_PRIORITY("priority"),
    EGRESS_ACLTEMPLATES_ENTRY_ACTION("action"),
    EGRESS_ACLTEMPLATES_ENTRY_ETHER_TYPE("etherType"),
    EGRESS_ACLTEMPLATES_ENTRY_ADDR_OVERRIDE("addressOverride"),
    EGRESS_ACLTEMPLATES_ENTRY_REFLEXIVE("reflexive"),
    EGRESS_ACLTEMPLATES_ENTRY_DSCP("DSCP"),
    EGRESS_ACLTEMPLATES_ENTRY_DESCRIPTION("description"),

    //Virtual Machine
    VM_NAME("name"),
    VM_DESCRIPTION("description"),
    VM_UUID("UUID"),
    VM_STATUS("status"),

    VM_INTERFACES("interfaces"),

    //VM Interface
    VM_INTERFACE_NAME("name"),
    VM_INTERFACE_MAC("MAC"),
    VM_INTERFACE_ATTACHED_NETWORK_ID("attachedNetworkID"),
    VM_INTERFACE_ATTACHED_NETWORK_TYPE("attachedNetworkType"),
    VM_INTERFACE_GATEWAY("gateway"),
    VM_INTERFACE_IPADDRESS("IPAddress"),
    VM_INTERFACE_NETMASK("netmask"),
    VM_INTERFACE_VPORT_ID("VPortID"),
    VM_INTERFACE_DOMAIN_ID("domainID"),

    //Shared Resource
    SHARED_RESOURCE_ADRESS("address"),
    SHARED_RESOURCE_GATEWAY("gateway"),
    SHARED_RESOURCE_NETMASK("netmask"),
    SHARED_RESOURCE_NAME("name"),
    SHARED_RESOURCE_TYPE("type"),

    //FloatinIP
    FLOATING_IP_ASSOC_SHARED_NTWK_ID("associatedSharedNetworkResourceID"),
    FLOATING_IP_ADDRESS("address"),
    FLOATING_IP_ASSIGNED("assigned"),

    //VPort
    VPORT_NAME("name"),
    VPORT_TYPE("type"),
    VPORT_ACTIVE("active"),
    VPORT_ADDRESSSPOOFING("addressSpoofing"),
    VPORT_DESCRIPTION("description"),
    VPORT_FLOATING_IP_ID("associatedFloatingIPID"),

    //Public network Macro
    ENTERPRISE_NTWK_MACRO_NAME("name"),
    ENTERPRISE_NTWK_MACRO_DESCRIPTION("description"),
    ENTERPRISE_NTWK_MACRO_ADDRESS("address"),
    ENTERPRISE_NTWK_MACRO_NETMASK("netmask"),

    //Enterprise Profile
    ENTERPRISE_PROFILE_NAME("name"),
    ENTERPRISE_PROFILE_DESCRIPTION("description"),
    ENTERPRISE_PROFILE_FLOATING_IP_QUOTA("floatingIPsQuota"),
    ENTERPRISE_PROFILE_GATEWAY_MGMT("allowGatewayManagement"),
    ENTERPRISE_PROFILE_FWD_CLASSES("allowedForwardingClasses"),
    ENTERPRISE_PROFILE_ADV_QOS("allowAdvancedQOSConfigurations"),

    //Gateway
    GATEWAY_SYSTEMID("systemID"),

    //Gateway Service
    WAN_SERVICE_VPN_CONNECT_ID("associatedVPNConnectID"),

    //VPN connection
    VPN_CONNECTION_WANSERVICE_ID("associatedWANServiceID"),
    VPN_CONNECTION_WANSERVICE_NAME("name"),

    //EnterprisePermission
    ENTERPRISEPERMISSION_PERMITTED_ENTITYID("permittedEntityID"),
    ENTERPRISEPERMISSION_PERMITTED_ENTITYYPE("permittedEntityType"),
    ENTERPRISEPERMISSION_PERMITTED_ACTION("permittedAction"),

    //Cloud Management Systems
    CLOUD_MGMT_SYSTEM_NAME("name");
    ;

    private static Map<String, NuageVspAttribute> lookup = new HashMap<String, NuageVspAttribute>();

    static {
        for (NuageVspAttribute entity : NuageVspAttribute.values()) {
            lookup.put(entity.getAttributeName(), entity);
        }
    }

    private String attributeName;

    private NuageVspAttribute(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public static NuageVspAttribute lookup(String attributeName) {
        return lookup.get(attributeName);
    }

    @Override
    public String toString() {
        return getAttributeName();
    }
}
