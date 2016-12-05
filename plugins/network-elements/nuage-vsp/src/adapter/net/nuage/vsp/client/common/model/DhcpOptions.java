package net.nuage.vsp.client.common.model;

import java.util.List;

import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;

/**
 * @author waegemae
 */
public class DhcpOptions {

    List<DhcpOption> options = Lists.newArrayList();

    public DhcpOptions() {

    }

    public DhcpOptions(List<String> dnsServers, String networkDomain) {

        if (CollectionUtils.isNotEmpty(dnsServers)) {
            options.add(new DhcpOption(6, dnsServers));
        }
        if (networkDomain != null && !networkDomain.isEmpty()) {
            options.add(new DhcpOption(15, networkDomain));
        }
    }

    public DhcpOptions(String hostname, boolean defaultInterface, boolean domainRouter) {
        if (!domainRouter) {
            if (!defaultInterface) {
                options.add(new DhcpOption(3, (byte) 0));
                options.add(new DhcpOption(15, (byte) 0));   // overrule the subnet option back to 0
            }
            options.add(new DhcpOption(12, hostname));
        }
    }

    public List<DhcpOption> getOptions() {
        return options;
    }

    public void addOption(DhcpOption option) {
        options.add(option);
    }

    public void addOptionList (List<DhcpOption> optionList) {
        options.addAll(optionList);
    }
}
