// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.cloud.network.element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.inject.Inject;

import com.cloud.util.NuageVspUtil;
import net.nuage.vsp.client.common.model.NuageVspAPIParams;
import net.nuage.vsp.client.common.model.NuageVspAttribute;
import net.nuage.vsp.client.common.model.NuageVspEntity;
import net.nuage.vsp.client.rest.NuageVspApi;
import net.nuage.vsp.client.rest.NuageVspApiUtil;

import org.apache.cloudstack.api.InternalIdentity;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.deploy.DeployDestination;
import com.cloud.domain.Domain;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.network.Network.Service;
import com.cloud.network.Network.State;
import com.cloud.network.NetworkModel;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.PhysicalNetwork.IsolationMethod;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.manager.NuageVspManager;
import com.cloud.network.router.VirtualRouter;
import com.cloud.network.router.VirtualRouter.Role;
import com.cloud.network.router.VpcVirtualNetworkApplianceManager;
import com.cloud.network.vpc.NetworkACLItem;
import com.cloud.network.vpc.NetworkACLItemDao;
import com.cloud.network.vpc.NetworkACLItemVO;
import com.cloud.network.vpc.PrivateGateway;
import com.cloud.network.vpc.StaticRouteProfile;
import com.cloud.network.vpc.Vpc;
import com.cloud.network.vpc.VpcManager;
import com.cloud.network.vpc.dao.NetworkACLDao;
import com.cloud.offering.NetworkOffering;
import com.cloud.user.AccountManager;
import com.cloud.vm.DomainRouterVO;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.DomainRouterDao;

@Component
@Local(value = {VpcProvider.class, NetworkACLServiceProvider.class, NuageVspElement.class, UserDataServiceProvider.class})
public class NuageVspVpcElement extends NuageVspElement implements VpcProvider, NetworkACLServiceProvider, UserDataServiceProvider {
    private static final Logger s_logger = Logger.getLogger(NuageVspVpcElement.class);

    @Inject
    NetworkACLDao _networkACLDao;
    @Inject
    PhysicalNetworkDao _physicalNetworkDao;
    @Inject
    VpcVirtualNetworkApplianceManager _vpcRouterMgr;
    @Inject
    AccountManager _accountMgr;
    @Inject
    DomainRouterDao _routerDao;
    @Inject
    VpcManager _vpcMgr;
    @Inject
    NetworkModel _networkMgr;
    @Inject
    VpcVirtualNetworkApplianceManager _routerMgr;
    @Inject
    NetworkACLItemDao _networkACLItemDao;

    @Override
    public Map<Network.Service, Map<Network.Capability, String>> getCapabilities() {
        Map<Network.Service, Map<Network.Capability, String>> capabilities = new HashMap<Network.Service, Map<Network.Capability, String>>();

        //add network ACL capability
        Map<Network.Capability, String> networkACLCapabilities = new HashMap<Network.Capability, String>();
        networkACLCapabilities.put(Network.Capability.SupportedProtocols, "tcp,udp,icmp");
        capabilities.put(Network.Service.NetworkACL, networkACLCapabilities);
        capabilities.put(Service.UserData, null);

        return capabilities;
    }

    // NetworkElement API
    @Override
    public Network.Provider getProvider() {
        return Network.Provider.NuageVspVpc;
    }

    @Override
    public boolean canEnableIndividualServices() {
        return true;
    }

    @Override
    public boolean verifyServicesCombination(Set<Service> services) {
        if (!services.contains(Service.NetworkACL) && !services.contains(Service.UserData)) {
            s_logger.warn("Unable to provide services without NetworkACL and UserDataservice enabled for this element");
            return false;
        }

        return true;
    }

    @Override
    public boolean implementVpc(Vpc vpc, DeployDestination dest, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException,
            InsufficientCapacityException {
        if (!canHandle(vpc)) {
            return false;
        }

        Map<VirtualMachineProfile.Param, Object> params = new HashMap<VirtualMachineProfile.Param, Object>(1);
        params.put(VirtualMachineProfile.Param.ReProgramGuestNetworks, true);

        _vpcRouterMgr.deployVirtualRouterInVpc(vpc, dest, _accountMgr.getAccount(vpc.getAccountId()), params);

        return true;
    }

    @Override
    public boolean shutdownVpc(Vpc vpc, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        if (!canHandle(vpc)) {
            return false;
        }

        s_logger.debug("Handling shutdownVpc() call back to delete the DomainTemplate associated to VPC " + vpc.getName() + " from VSP");
        String vspNetworkId = null;
        // Clean up all the network that was created

        List<DomainRouterVO> routers = _routerDao.listByVpcId(vpc.getId());
        if (routers == null || routers.isEmpty()) {
            return true;
        }
        List<String> domainRouterUuid = new ArrayList<String>();
        boolean result = true;
        for (DomainRouterVO router : routers) {
            result = result && (_vpcRouterMgr.destroyRouter(router.getId(), context.getAccount(), context.getCaller().getId()) != null);
            if (result) {
                domainRouterUuid.add(router.getUuid());
            }
        }

        long domainId = vpc.getDomainId();
        Domain domain = _domainDao.findById(domainId);
        Long zoneId = vpc.getZoneId();
        Long guestPhysicalNetworkId = getPhysicalNetworkId(zoneId);

        try {
            if (vpc.getState().equals(Vpc.State.Inactive)) {
                String nuageVspCmsId = NuageVspUtil.findNuageVspDeviceCmsIdByPhysNet(guestPhysicalNetworkId, _nuageVspDao, _configDao);
                NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(getNuageVspHost(guestPhysicalNetworkId), nuageVspCmsId);
                if (domainRouterUuid.size() > 0) {
                    for (String routerUuid : domainRouterUuid) {
                        String vmJsonString = NuageVspApiUtil.getVMDetails(domain.getUuid() + "(Enterprise uuid)", routerUuid, nuageVspAPIParamsAsCmsUser);
                        if (!StringUtils.isBlank(vmJsonString)) {
                            //get the VM ID
                            List<Map<String, Object>> vmDetails = NuageVspApiUtil.parseJson(vmJsonString, NuageVspEntity.VM);
                            Map<String, Object> vm = vmDetails.iterator().next();
                            String vmId = (String)vm.get(NuageVspAttribute.ID.getAttributeName());
                            NuageVspApiUtil.deleteVM(routerUuid, nuageVspAPIParamsAsCmsUser, vmId);
                            if (s_logger.isDebugEnabled()) {
                                s_logger.debug("Handling shutdownVpc() call back. VR " + (String)vm.get(NuageVspAttribute.VM_NAME.getAttributeName()) + " in VPC " + vpc.getName()
                                        + " is deleted as the VPC is deleted");
                            }
                        }
                    }
                }

                String enterpriseId = NuageVspUtil.getEnterpriseId(domain, _domainDao, nuageVspAPIParamsAsCmsUser);
                if (StringUtils.isNotBlank(enterpriseId)) {
                    //get the L3 DomainTemplate with externalUuid
                    String domainTemplateId = NuageVspApiUtil.findFieldValueByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN,
                            vpc.getUuid(), NuageVspAttribute.DOMAIN_TEMPLATE_ID.getAttributeName(), nuageVspAPIParamsAsCmsUser);
                    if (domainTemplateId == null) return true;

                    String vpcDomainTemplateName = _configDao.getValue(NuageVspManager.NuageVspVpcDomainTemplateName.key());
                    String vpcDomainTemplateEntity = NuageVspApiUtil.findEntityUsingFilter(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN_TEMPLATE,
                            "name", vpcDomainTemplateName, nuageVspAPIParamsAsCmsUser);
                    String vpcDomainTemplateId = NuageVspApiUtil.getEntityId(vpcDomainTemplateEntity, NuageVspEntity.DOMAIN_TEMPLATE);
                    if (domainTemplateId.equals(vpcDomainTemplateId)) {
                        vspNetworkId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, vpc.getUuid(),
                                nuageVspAPIParamsAsCmsUser);
                        if (StringUtils.isNotBlank(vspNetworkId)) {
                            if (s_logger.isDebugEnabled()) {
                                s_logger.debug("VPC getting deleted and its state is  " + vpc.getState() + ". So delete VPC " + vpc.getUuid() + " from VSP");
                            }
                            result = NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.DOMAIN, vspNetworkId, nuageVspAPIParamsAsCmsUser, Arrays.asList(NuageVspApi.s_networkModificationError));
                        }
                    } else {
                        vspNetworkId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN_TEMPLATE, vpc.getUuid(),
                                nuageVspAPIParamsAsCmsUser);
                        if (StringUtils.isNotBlank(vspNetworkId)) {
                            s_logger.debug("VPC getting deleted and its state is  " + vpc.getState() + ". So delete VPC " + vpc.getUuid() + " from VSP");
                            result = NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.DOMAIN_TEMPLATE, vspNetworkId, nuageVspAPIParamsAsCmsUser, Arrays.asList(NuageVspApi.s_networkModificationError));
                        } else {
                            // Added fallback for scenario described in CLOUD-406
                            vspNetworkId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, vpc.getUuid(),
                                    nuageVspAPIParamsAsCmsUser);
                            if (StringUtils.isNotBlank(vspNetworkId)) {
                                if (s_logger.isDebugEnabled()) {
                                    s_logger.debug("VPC getting deleted and its state is  " + vpc.getState() + ". So delete VPC " + vpc.getUuid() + " from VSP");
                                }
                                result = NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.DOMAIN, vspNetworkId, nuageVspAPIParamsAsCmsUser, Arrays.asList(NuageVspApi.s_networkModificationError));
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            s_logger.warn("Failed to clean up network information in Vsp " + e.getMessage());
            result = false;
        }

        return result;
    }

    private boolean canHandle(Vpc vpc) {
        Long zoneId = vpc.getZoneId();
        Long guestPhysicalNetworkId = getPhysicalNetworkId(zoneId);
        if (!_networkModel.isProviderEnabledInPhysicalNetwork(guestPhysicalNetworkId, getProvider().getName())) {
            s_logger.debug("NuageVspElement is not enabled for physical network " + guestPhysicalNetworkId);
            return false;
        }
        return true;
    }

    private Long getPhysicalNetworkId(Long zoneId) {
        Long guestPhysicalNetworkId = new Long(0);
        List<PhysicalNetworkVO> physicalNetworkList = _physicalNetworkDao.listByZone(zoneId);
        for (PhysicalNetworkVO phyNtwk : physicalNetworkList) {
            if (phyNtwk.getIsolationMethods().contains(IsolationMethod.VSP.name())) {
                guestPhysicalNetworkId = phyNtwk.getId();
                break;
            }
        }
        return guestPhysicalNetworkId;
    }

    @Override
    public boolean implement(Network network, NetworkOffering offering, DeployDestination dest, ReservationContext context) throws ResourceUnavailableException,
            ConcurrentOperationException, InsufficientCapacityException {
        if (!canHandle(network, null)) {
            return false;
        }

        Long vpcId = network.getVpcId();
        if (vpcId == null) {
            s_logger.trace("Network " + network + " is not associated with any VPC");
            return false;
        }

        Vpc vpc = _vpcMgr.getActiveVpc(vpcId);
        if (vpc == null) {
            s_logger.warn("Unable to find Enabled VPC by id " + vpcId);
            return false;
        }

        Map<VirtualMachineProfile.Param, Object> params = new HashMap<VirtualMachineProfile.Param, Object>(1);
        params.put(VirtualMachineProfile.Param.ReProgramGuestNetworks, true);

        List<DomainRouterVO> routers = _vpcRouterMgr.deployVirtualRouterInVpc(vpc, dest, _accountMgr.getAccount(vpc.getAccountId()), params);
        if ((routers == null) || (routers.size() == 0)) {
            s_logger.warn("Can't find at least one running router!");
            return false;
        }

        if (routers.size() > 1) {
            s_logger.warn("Found more than one router in vpc " + vpc);
            return false;
        }

        DomainRouterVO router = routers.get(0);
        //Add router to guest network if needed
        if (!_networkMgr.isVmPartOfNetwork(router.getId(), network.getId())) {
            Map<VirtualMachineProfile.Param, Object> paramsForRouter = new HashMap<VirtualMachineProfile.Param, Object>(1);
            if (network.getState() == State.Setup) {
                paramsForRouter.put(VirtualMachineProfile.Param.ReProgramGuestNetworks, true);
            }
            if (!_vpcRouterMgr.addVpcRouterToGuestNetwork(router, network, false, paramsForRouter)) {
                s_logger.warn("Failed to add VPC router " + router + " to guest network " + network);
                return false;
            } else {
                s_logger.debug("Successfully added VPC router " + router + " to guest network " + network);
            }
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Starting the sync for network " + network.getName() + " at " + new Date());
            s_logger.debug("Started Sync Network ACL Rule for network " + network.getName() + " at " + new Date());
        }
        List<NetworkACLItemVO> rules = null;
        if (network.getNetworkACLId() != null) {
            rules = _networkACLItemDao.listByACL(network.getNetworkACLId());
        } else {
            rules = new ArrayList<NetworkACLItemVO>(1);
        }

        if (_networkModel.areServicesSupportedByNetworkOffering(offering.getId(), Service.NetworkACL)) {
            applyACLRules(network, rules, true, null, false);
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Finished Sync Network ACL Rule for network " + network.getName() + " at " + new Date());
        }
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Finished the sync for network " + network.getName() + " at " + new Date());
        }

        return true;
    }

    @Override
    public boolean prepare(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context) throws ConcurrentOperationException,
            InsufficientCapacityException, ResourceUnavailableException {
        if (!canHandle(network, null)) {
            return false;
        }

        Long vpcId = network.getVpcId();
        if (vpcId == null) {
            s_logger.trace("Network " + network + " is not associated with any VPC");
            return false;
        }

        Vpc vpc = _vpcMgr.getActiveVpc(vpcId);
        if (vpc == null) {
            s_logger.warn("Unable to find Enabled VPC by id " + vpcId);
            return false;
        }

        if (vm.getType() == VirtualMachine.Type.User) {
            Map<VirtualMachineProfile.Param, Object> params = new HashMap<VirtualMachineProfile.Param, Object>(1);
            params.put(VirtualMachineProfile.Param.ReProgramGuestNetworks, true);
            List<DomainRouterVO> routers = _vpcRouterMgr.deployVirtualRouterInVpc(vpc, dest, _accountMgr.getAccount(vpc.getAccountId()), params);
            if ((routers == null) || (routers.size() == 0)) {
                s_logger.warn("Can't find at least one running router!");
                return false;
            }

            if (routers.size() > 1) {
                s_logger.warn("Found more than one router in vpc " + vpc);
                return false;
            }

            DomainRouterVO router = routers.get(0);
            //Add router to guest network if needed
            if (!_networkMgr.isVmPartOfNetwork(router.getId(), network.getId())) {
                Map<VirtualMachineProfile.Param, Object> paramsForRouter = new HashMap<VirtualMachineProfile.Param, Object>(1);
                //need to reprogram guest network if it comes in a setup state
                if (network.getState() == State.Setup) {
                    paramsForRouter.put(VirtualMachineProfile.Param.ReProgramGuestNetworks, true);
                }
                if (!_vpcRouterMgr.addVpcRouterToGuestNetwork(router, network, false, paramsForRouter)) {
                    s_logger.warn("Failed to add VPC router " + router + " to guest network " + network);
                    return false;
                } else {
                    s_logger.debug("Successfully added VPC router " + router + " to guest network " + network);
                }
            }
        }

        return true;
    }

    @Override
    public boolean shutdown(Network network, ReservationContext context, boolean cleanup) throws ConcurrentOperationException, ResourceUnavailableException {
        if (!canHandle(network, null)) {
            return false;
        }

        boolean success = true;
        Long vpcId = network.getVpcId();
        if (vpcId == null) {
            s_logger.debug("Network " + network + " doesn't belong to any vpc, so skipping unplug nic part");
            return success;
        }

        List<? extends VirtualRouter> routers = _routerDao.listByVpcId(vpcId);
        for (VirtualRouter router : routers) {
            //1) Check if router is already a part of the network
            if (!_networkMgr.isVmPartOfNetwork(router.getId(), network.getId())) {
                s_logger.debug("Router " + router + " is not a part the network " + network);
                continue;
            }
            //2) Call unplugNics in the network service
            success = success && _vpcRouterMgr.removeVpcRouterFromGuestNetwork(router, network, false);
            if (!success) {
                s_logger.warn("Failed to unplug nic in network " + network + " for virtual router " + router);
            } else {
                s_logger.debug("Successfully unplugged nic in network " + network + " for virtual router " + router);
            }
        }

        return success;
    }

    @Override
    public boolean destroy(Network config, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
        if (!canHandle(config, null)) {
            return false;
        }

        boolean success = true;
        s_logger.debug("Handling destroy() call back to unplug nic from VR for the network " + config.getName() + " with uuid " + config.getUuid());
        Long vpcId = config.getVpcId();
        if (vpcId == null) {
            s_logger.debug("Network " + config + " doesn't belong to any vpc, so skipping unplug nic part");
            return success;
        }

        List<? extends VirtualRouter> routers = _routerDao.listByVpcId(vpcId);
        for (VirtualRouter router : routers) {
            //1) Check if router is already a part of the network
            if (!_networkMgr.isVmPartOfNetwork(router.getId(), config.getId())) {
                s_logger.debug("Router " + router + " is not a part the network " + config);
                continue;
            }
            //2) Call unplugNics in the network service
            success = success && _vpcRouterMgr.removeVpcRouterFromGuestNetwork(router, config, false);
            if (!success) {
                s_logger.warn("Failed to unplug nic in network " + config + " for virtual router " + router);
            } else {
                s_logger.debug("Successfully unplugged nic in network " + config + " for virtual router " + router);
            }
        }

        return success;
    }

    @Override
    public boolean createPrivateGateway(PrivateGateway gateway) throws ConcurrentOperationException, ResourceUnavailableException {
        return false;
    }

    @Override
    public boolean deletePrivateGateway(PrivateGateway privateGateway) throws ConcurrentOperationException, ResourceUnavailableException {
        return false;
    }

    @Override
    public boolean applyStaticRoutes(Vpc vpc, List<StaticRouteProfile> routes) throws ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean applyNetworkACLs(Network network, List<? extends NetworkACLItem> rules) throws ResourceUnavailableException {
        if (!canHandle(network, Service.NetworkACL)) {
            return false;
        }

        s_logger.debug("Handling applyNetworkACLs for network " + network.getName() + " with " + rules.size() + " Network ACLs");
        return applyACLRules(network, rules, true, null, rules.isEmpty());
    }

    @Override
    public boolean applyACLItemsToPrivateGw(PrivateGateway privateGateway, List<? extends NetworkACLItem> rules) throws ResourceUnavailableException {
        return true;
    }

    @Override
    protected List<String> deleteStaleACLEntries(String vpcOrSubnetUuid, NuageVspEntity attachedNetworkType, String attachedL2DomainOrDomainId, boolean egressDefaultPolicy,
            String aclNetworkLocationId, List<? extends InternalIdentity> rules, Map<String, Map<String, Object>> vspIngressAclEntriesExtUuidToAcl,
            Map<String, Map<String, Object>> vspEgressAclEntriesExtUuidToAcl, String ingressACLTempId, Map<Integer, Map<String, Object>> defaultVspIngressAclEntries,
            String egressACLTempId, Map<Integer, Map<String, Object>> defaultVspEgressAclEntries, String networkName, Boolean isAcsIngressAcl,
            NuageVspAPIParams nuageVspAPIParamsAsCmsUser, boolean createDefaultRules) throws Exception {
        List<String> deletedEntries = new ArrayList<String>();
        if (rules == null || rules.isEmpty()) {
            s_logger.debug("ACL rule list for network " + networkName + " is empty. So, no rules to apply. So, delete all the existing ACL in VSP for this network");
            //iterate through ingress and egress acl entries and delete all the ACLs
            for (Map<String, Object> ingressAclEntry : vspIngressAclEntriesExtUuidToAcl.values()) {
                String id = (String)ingressAclEntry.get(NuageVspAttribute.ID.getAttributeName());
                NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.INGRESS_ACLTEMPLATES_ENTRIES, id, nuageVspAPIParamsAsCmsUser);
                deletedEntries.add(id);
            }
            for (Map<String, Object> egressAclEntry : vspEgressAclEntriesExtUuidToAcl.values()) {
                String id = (String)egressAclEntry.get(NuageVspAttribute.ID.getAttributeName());
                NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.EGRESS_ACLTEMPLATES_ENTRIES, id, nuageVspAPIParamsAsCmsUser);
                deletedEntries.add(id);
            }
        } else {

            //Clean all the ACLs that are stale in VSP
            s_logger.debug("ACL rule list for network " + networkName + " is not empty. So, some rules needs to be applied in VSP");
            NuageVspApiUtil.cleanStaleAclsFromVsp(rules, vspIngressAclEntriesExtUuidToAcl, vspEgressAclEntriesExtUuidToAcl, null, nuageVspAPIParamsAsCmsUser);
        }
        if (createDefaultRules) {
            NuageVspApiUtil.createDefaultIngressAndEgressAcls(true, vpcOrSubnetUuid, true, attachedNetworkType, attachedL2DomainOrDomainId, new StringBuffer(), ingressACLTempId,
                    defaultVspIngressAclEntries, egressACLTempId, defaultVspEgressAclEntries, networkName, nuageVspAPIParamsAsCmsUser);
        }
        return deletedEntries;
    }

    @Override
    public boolean addPasswordAndUserdata(Network network, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, InsufficientCapacityException, ResourceUnavailableException {
        if (canHandle(network, Service.UserData)) {
            if (vm.getType() != VirtualMachine.Type.User) {
                return false;
            }

            if (network.getIp6Gateway() != null) {
                s_logger.info("Skip password and userdata service setup for IPv6 VM");
                return true;
            }
            VirtualMachineProfile uservm = vm;

            List<DomainRouterVO> routers = getRouters(network, dest);

            if ((routers == null) || (routers.size() == 0)) {
                throw new ResourceUnavailableException("Can't find at least one router!", DataCenter.class, network.getDataCenterId());
            }

            return _routerMgr.applyUserData(network, nic, uservm, dest, routers);
        }
        return false;
    }

    @Override
    public boolean savePassword(Network network, NicProfile nic, VirtualMachineProfile vm) throws ResourceUnavailableException {
        if (!canHandle(network, null)) {
            return false;
        }
        List<DomainRouterVO> routers = _routerDao.listByNetworkAndRole(network.getId(), Role.VIRTUAL_ROUTER);
        if (routers == null || routers.isEmpty()) {
            s_logger.debug("Can't find virtual router element in network " + network.getId());
            return true;
        }

        VirtualMachineProfile uservm = vm;

        return _routerMgr.savePasswordToRouter(network, nic, uservm, routers);
    }

    @Override
    public boolean saveUserData(Network network, NicProfile nic, VirtualMachineProfile vm) throws ResourceUnavailableException {
        if (!canHandle(network, null)) {
            return false;
        }
        List<DomainRouterVO> routers = _routerDao.listByNetworkAndRole(network.getId(), Role.VIRTUAL_ROUTER);
        if (routers == null || routers.isEmpty()) {
            s_logger.debug("Can't find virtual router element in network " + network.getId());
            return true;
        }
        VirtualMachineProfile uservm = vm;

        return _routerMgr.saveUserDataToRouter(network, nic, uservm, routers);
    }

    @Override
    public boolean saveSSHKey(Network network, NicProfile nic, VirtualMachineProfile vm, String sSHPublicKey) throws ResourceUnavailableException {
        if (!canHandle(network, null)) {
            return false;
        }
        List<DomainRouterVO> routers = _routerDao.listByNetworkAndRole(network.getId(), Role.VIRTUAL_ROUTER);
        if (routers == null || routers.isEmpty()) {
            s_logger.debug("Can't find virtual router element in network " + network.getId());
            return true;
        }

        VirtualMachineProfile uservm = vm;

        return _routerMgr.saveSSHPublicKeyToRouter(network, nic, uservm, routers, sSHPublicKey);
    }

    private List<DomainRouterVO> getRouters(Network network, DeployDestination dest) {
        boolean publicNetwork = false;
        if (_networkMgr.isProviderSupportServiceInNetwork(network.getId(), Service.SourceNat, getProvider())) {
            publicNetwork = true;
        }
        boolean isPodBased = (dest.getDataCenter().getNetworkType() == NetworkType.Basic || _networkMgr.isSecurityGroupSupportedInNetwork(network))
                && network.getTrafficType() == TrafficType.Guest;

        List<DomainRouterVO> routers;

        if (publicNetwork) {
            routers = _routerDao.listByNetworkAndRole(network.getId(), Role.VIRTUAL_ROUTER);
        } else {
            if (isPodBased) {
                Long podId = dest.getPod().getId();
                routers = _routerDao.listByNetworkAndPodAndRole(network.getId(), podId, Role.VIRTUAL_ROUTER);
            } else {
                routers = _routerDao.listByNetworkAndRole(network.getId(), Role.VIRTUAL_ROUTER);
            }
        }

        // for Basic zone, add all Running routers - we have to send
        // Dhcp/vmData/password info to them when
        // network.dns.basiczone.updates is set to "all"
        if (isPodBased && _routerMgr.getDnsBasicZoneUpdate().equalsIgnoreCase("all")) {
            Long podId = dest.getPod().getId();
            List<DomainRouterVO> allRunningRoutersOutsideThePod = _routerDao.findByNetworkOutsideThePod(network.getId(), podId, com.cloud.vm.VirtualMachine.State.Running,
                    Role.VIRTUAL_ROUTER);
            routers.addAll(allRunningRoutersOutsideThePod);
        }
        return routers;
    }
}
