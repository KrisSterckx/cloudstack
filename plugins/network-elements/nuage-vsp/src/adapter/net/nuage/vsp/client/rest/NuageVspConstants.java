package net.nuage.vsp.client.rest;

public class NuageVspConstants {

    public static final String CMS_USER_ENTEPRISE_NAME = "CSP";

    public static final int FLOATING_IP_QUOTA = 249999;

    public static final String DEFAULT_FWD_CLASS = "H";

    public static final String ACL_ACTION_FORWARD = "FORWARD";

    public static final String ACL_ACTION_DROP = "DROP";

    public static final String ETHERTYPE_IP = "0x0800";

    public static final String ANY = "ANY";

    public static final String STAR = "*";

    public static final String ENDPOINT_SUBNET = "ENDPOINT_SUBNET";

    public static final String ENDPOINT_DOMAIN = "ENDPOINT_DOMAIN";

    public static final String ENTERPRISE_NETWORK = "ENTERPRISE_NETWORK";

    public static final String SUBNET = "SUBNET";

    public static final String USER_FIRST_NAME = "FN";

    public static final String USER_LAST_NAME = "LN";

    public static final String USER_EMAIL = "defaultemail@email.com";

    public static final String USER_PASSWORD = "default";

    public static final String ZONE_NAME = "zone";

    public static final String VSP_DEFAULT_PROPERTIES = "vsp-defaults.properties";

    public static final int ENTERPRISE_PROFILE_FLOATING_IP_QUOTA = 100;

    public static final boolean ENTERPRISE_PROFILE_GATEWAY_MGMT = false;

    public static final String ENTERPRISE_PROFILE_FWD_CLASSES = "C,D,E,F,G,H";

    public static final boolean ENTERPRISE_PROFILE_ADV_QOS = false;

    public static final int MIN_ACL_PRIORITY = 9999;

    public static final int MAX_ACL_PRIORITY = 10000000;

    public static final int SUBNET_BLOCK_ACL_PRIORITY = 10800000;

    public static final String SUBNET_BLOCK_ACL = "Default Subnet ACL to block traffic as there is an ACL list associated with it";

    public static final int DEFAULT_SUBNET_ALLOW_ACL_PRIORITY = 0;

    public static final String DEFAULT_SUBNET_ALLOW_ACL = "Default Intra-Subnet Allow ACL";

    public static final int DEFAULT_DOMAIN_BLOCK_ACL_PRIORITY = 11000000;

    public static final String DEFAULT_DOMAIN_BLOCK_ACL = "Default Intra-Domain Deny ACL";

    public static final int DEFAULT_TCP_ALLOW_ACL_PRIORITY = 11000001;

    public static final String DEFAULT_VSP_INGRESS_ALLOW_TCP_ACL = "Default Allow TCP";

    public static final int DEFAULT_UDP_ALLOW_ACL_PRIORITY = 11000002;

    public static final String DEFAULT_VSP_INGRESS_ALLOW_UDP_ACL = "Default Allow UDP";

    public static final int DEFAULT_ICMP_ALLOW_ACL_PRIORITY = 11000003;

    public static final String DEFAULT_VSP_INGRESS_ALLOW_ICMP_ACL = "Default Allow ICMP";

    public static final int NUM_OF_PERIODIC_THREADS = 5;

    public static final int SYNC_UP_INTERVAL_IN_MINUTES = 60 * 8;

    public static final String EXTERNAL_ID_DELIMITER = "@";

    public static final String NETWORK_METADATA_TYPE = "type";
    
    public static final String NETWORK_METADATA_VSD_DOMAIN_ID = "vsdDomainId";

    public static final String NETWORK_METADATA_VSD_SUBNET_ID = "vsdSubnetId";
    
    public static final Integer DEFAULT_API_RETRY_COUNT = 4;
    public static final Long DEFAULT_API_RETRY_INTERVAL = 60L;
}
