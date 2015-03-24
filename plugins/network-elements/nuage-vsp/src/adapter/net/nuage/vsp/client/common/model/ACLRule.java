package net.nuage.vsp.client.common.model;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ObjectUtils;

import com.cloud.network.rules.FirewallRule;
import com.cloud.network.vpc.NetworkACLItem;

public class ACLRule {

    public enum ACLState {
        Active, Add, Revoke;
    }

    public enum ACLTrafficType {
        Ingress, Egress;
    }

    public enum ACLAction {
        Allow, Deny;
    }

    public enum ACLType {
        Firewall, NetworkACL
    }

    List<String> sourceCidrList = null;
    String uuid = null;
    String protocol = null;
    Integer startPort = null;
    Integer endPort = null;
    ACLState state = null;
    ACLTrafficType trafficType = null;
    ACLAction action = null;
    Long sourceIpAddressId = null;
    Integer priority = 0;
    ACLType type;

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public ACLType getType() {
        return type;
    }

    public Integer getPriority() {
        return priority;
    }

    public ACLAction getAction() {
        return action;
    }

    public Long getSourceIpAddressId() {
        return sourceIpAddressId;
    }

    public ACLState getState() {
        return state;
    }

    public ACLTrafficType getTrafficType() {
        return trafficType;
    }

    public List<String> getSourceCidrList() {
        return sourceCidrList;
    }

    public String getUuid() {
        return uuid;
    }

    public String getProtocol() {
        return protocol;
    }

    public Integer getStartPort() {
        return startPort;
    }

    public Integer getEndPort() {
        return endPort;
    }

    public ACLRule(Object aclRule, boolean egressDefaultPolicy) {
        if (aclRule instanceof FirewallRule) {
            FirewallRule firewallRule = (FirewallRule)aclRule;
            this.sourceCidrList = firewallRule.getSourceCidrList();
            this.uuid = firewallRule.getUuid();
            this.protocol = firewallRule.getProtocol();
            this.startPort = firewallRule.getSourcePortStart();
            this.endPort = firewallRule.getSourcePortEnd();
            this.state = ACLState.valueOf(firewallRule.getState().name());
            this.trafficType = ACLTrafficType.valueOf(firewallRule.getTrafficType().name());
            this.sourceIpAddressId = firewallRule.getSourceIpAddressId();
            if (firewallRule.getTrafficType().equals(FirewallRule.TrafficType.Egress) && egressDefaultPolicy) {
                this.action = ACLAction.Deny;
            } else {
                this.action = ACLAction.Allow;
            }
            this.priority = -1;
            this.type = ACLType.Firewall;
        } else {
            NetworkACLItem networkAcl = (NetworkACLItem)aclRule;
            this.sourceCidrList = networkAcl.getSourceCidrList();
            this.uuid = networkAcl.getUuid();
            this.protocol = networkAcl.getProtocol();
            this.startPort = networkAcl.getSourcePortStart();
            this.endPort = networkAcl.getSourcePortEnd();
            this.state = ACLState.valueOf(networkAcl.getState().name());
            this.trafficType = ACLTrafficType.valueOf(networkAcl.getTrafficType().name());
            this.action = ACLAction.valueOf(networkAcl.getAction().name());
            this.priority = networkAcl.getNumber();
            this.type = ACLType.NetworkACL;
        }
    }

    public boolean isNotModified(ACLRule aclRule, Map<String, Object> modifiedAcl, Map<String, Object> existingVspAcl) {
        for (Map.Entry<String, Object> aclValue : modifiedAcl.entrySet()) {
            if (existingVspAcl.containsKey(aclValue.getKey()) && !ObjectUtils.equals(existingVspAcl.get(aclValue.getKey()), aclValue.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "uuid=" + this.uuid + ":" + "protocol=" + this.protocol + ":" + "cidr=" + this.sourceCidrList + ":" + "port=" + this.startPort + "-" + this.endPort + ":" + "state="
                + this.state + ":" + "trafficType=" + this.trafficType + ":" + "action=" + this.action + ":" + "type=" + this.type + ":" + "priority=" + this.priority;
    }
}