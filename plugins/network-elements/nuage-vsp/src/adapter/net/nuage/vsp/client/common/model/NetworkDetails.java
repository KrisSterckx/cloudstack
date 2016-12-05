package net.nuage.vsp.client.common.model;


public class NetworkDetails {

    private enum Type {
        VPC, ISOLATED, L2;
    }

    private String enterpriseId;

    private NuageVspEntity vspDomainType;
    private String vspDomainId;
    private String vspDomainExternalId;

    private NuageVspEntity vspSubnetType;
    private String vspSubnetId;
    private String vspSubnetExternalId;

    private Long networkId;

    private boolean hasVpn;

    private Type type;

    public static class Builder {
        NetworkDetails details = new NetworkDetails();

        public Builder enterprise(String enterpriseId) {
            details.enterpriseId = enterpriseId;
            return this;
        }

        public Builder isolatedNetwork(Long networkId, String vspDomainId, String vspSubnetId, String externalId) {
            details.networkId = networkId;

            details.vspDomainType = NuageVspEntity.DOMAIN;
            details.vspDomainId = vspDomainId;
            details.vspDomainExternalId = externalId;

            details.vspSubnetType = NuageVspEntity.SUBNET;
            details.vspSubnetId = vspSubnetId;
            details.vspSubnetExternalId = externalId;

            details.type = Type.ISOLATED;

            return this;
        }

        public Builder vpcTier(Long networkId, String vspDomainId, String vspDomainExternalId, String vspSubnetId, String vspSubnetExternalId) {
            details.networkId = networkId;

            details.vspDomainType = NuageVspEntity.DOMAIN;
            details.vspDomainId = vspDomainId;
            details.vspDomainExternalId = vspDomainExternalId;

            details.vspSubnetType = NuageVspEntity.SUBNET;
            details.vspSubnetId = vspSubnetId;
            details.vspSubnetExternalId = vspSubnetExternalId;

            details.type = Type.VPC;

            return this;
        }

        public Builder l2Network(Long networkId, String vspSubnetId, String externalId) {
            details.networkId = networkId;

            details.vspDomainType = NuageVspEntity.L2DOMAIN;
            details.vspDomainId = vspSubnetId;
            details.vspDomainExternalId = externalId;

            details.vspSubnetType = NuageVspEntity.L2DOMAIN;
            details.vspSubnetId = vspSubnetId;
            details.vspSubnetExternalId = externalId;

            details.type = Type.L2;

            return this;
        }

        public Builder withVpn(boolean b) {
            details.hasVpn = b;

            return this;
        }


        public NetworkDetails build() {
            NetworkDetails result = details;
            details = new NetworkDetails();
            return result;
        }
    }

    public NuageVspEntity getSubnetType() {
        return vspSubnetType;
    }

    public String getSubnetId() {
        return vspSubnetId;
    }

    public String getEnterpriseId() {
        return enterpriseId;
    }

    public NuageVspEntity getVspDomainType() {
        return vspDomainType;
    }

    public String getVspDomainId() {
        return vspDomainId;
    }

    public String getVspDomainExternalId() {
        return vspDomainExternalId;
    }

    public NuageVspEntity getVspSubnetType() {
        return vspSubnetType;
    }

    public String getVspSubnetId() {
        return vspSubnetId;
    }

    public String getVspSubnetExternalId() {
        return vspSubnetExternalId;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public boolean isVpc() {
        return Type.VPC == type;
    }

    public boolean isL3() {
        return Type.L2 != type;
    }

    public String getDomainExternalId() {
        return vspDomainExternalId;
    }

    public NetworkDetails() {}

    public NetworkDetails(NuageVspEntity entityType, String entityId, Boolean isVpc, String domainUuid) {
        this.vspSubnetType = entityType;
        this.vspSubnetId = entityId;
        this.type = isVpc ? Type.VPC : Type.ISOLATED;
        this.vspDomainExternalId = domainUuid;
    }

    public void setEntityType(NuageVspEntity entityType) {
        this.vspSubnetType = entityType;
    }

    public void setEntityId(String entityId) {
        this.vspSubnetId = entityId;
    }

    public void setIsVpc(Boolean isVpc) {
        this.type = isVpc ? Type.VPC : Type.ISOLATED;
    }

    public void setDomainUuid(String domainUuid) {
        this.vspDomainExternalId = domainUuid;
    }

    public boolean isOfferingWithVpn() {
        return hasVpn;
    }
}
