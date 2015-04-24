package com.cloud.network.guru;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.inject.Inject;

import net.nuage.vsp.client.common.model.NuageVspAPIParams;
import net.nuage.vsp.client.common.model.NuageVspAttribute;
import net.nuage.vsp.client.common.model.NuageVspEntity;
import net.nuage.vsp.client.exception.NuageVspAPIUtilException;
import net.nuage.vsp.client.rest.NuageVspApi;
import net.nuage.vsp.client.rest.NuageVspApiUtil;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.amazonaws.util.json.JSONArray;
import com.cloud.dc.DataCenter;
import com.cloud.dc.VlanVO;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.Network.GuestType;
import com.cloud.network.Network.Service;
import com.cloud.network.Network.State;
import com.cloud.network.IpAddress;
import com.cloud.network.NetworkProfile;
import com.cloud.network.Networks;
import com.cloud.network.NuageVspDeviceVO;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.PhysicalNetwork.IsolationMethod;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.NuageVspDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.manager.NuageVspManager;
import com.cloud.network.vpc.Vpc;
import com.cloud.network.vpc.dao.VpcDao;
import com.cloud.offering.NetworkOffering;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.offerings.dao.NetworkOfferingServiceMapDao;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.db.DB;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.NicProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;

@Local(value = NetworkGuru.class)
public class NuageVspGuestNetworkGuru extends GuestNetworkGuru {
    private static final Logger s_logger = Logger.getLogger(NuageVspGuestNetworkGuru.class);

    @Inject
    NetworkOfferingServiceMapDao _ntwkOfferingSrvcDao;
    @Inject
    NetworkOfferingDao _ntwkOfferingDao;
    @Inject
    DomainDao _domainDao;
    @Inject
    AccountDao _accountDao;
    @Inject
    NuageVspDao _nuageVspDao;
    @Inject
    HostDao _hostDao;
    @Inject
    VpcDao _vpcDao;
    @Inject
    DataCenterDao _dataCenterDao;
    @Inject
    NuageVspManager _nuageVspManager;
    @Inject
    IPAddressDao _ipAddressDao;

    public NuageVspGuestNetworkGuru() {
        super();
        _isolationMethods = new IsolationMethod[] {IsolationMethod.VSP};
    }

    @Override
    public Network design(NetworkOffering offering, DeploymentPlan plan, Network userSpecified, Account owner) {

        // Check of the isolation type of the related physical network is STT
        PhysicalNetworkVO physnet = _physicalNetworkDao.findById(plan.getPhysicalNetworkId());
        DataCenter dc = _dcDao.findById(plan.getDataCenterId());
        if (!canHandle(offering, dc.getNetworkType(), physnet)) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Refusing to design this network");
            }
            return null;
        }

        NetworkVO networkObject = (NetworkVO)super.design(offering, plan, userSpecified, owner);
        if (networkObject == null) {
            return null;
        }

        // Override the broadcast domain type
        networkObject.setBroadcastDomainType(Networks.BroadcastDomainType.Vsp);
        return networkObject;
    }

    @Override
    public Network implement(Network network, NetworkOffering offering, DeployDestination dest, ReservationContext context) throws InsufficientVirtualNetworkCapacityException {

        assert (network.getState() == State.Implementing) : "Why are we implementing " + network;

        long dcId = dest.getDataCenter().getId();

        //get physical network id
        Long physicalNetworkId = network.getPhysicalNetworkId();

        // physical network id can be null in Guest Network in Basic zone, so locate the physical network
        if (physicalNetworkId == null) {
            physicalNetworkId = _networkModel.findPhysicalNetworkId(dcId, offering.getTags(), offering.getTrafficType());
        }

        NetworkVO implemented = new NetworkVO(network.getTrafficType(), network.getMode(), network.getBroadcastDomainType(), network.getNetworkOfferingId(), State.Allocated,
                network.getDataCenterId(), physicalNetworkId);

        if (network.getGateway() != null) {
            implemented.setGateway(network.getGateway());
        }

        if (network.getCidr() != null) {
            implemented.setCidr(network.getCidr());
        }

        Collection<String> ipAddressRange = new ArrayList<String>();
        String virtualRouterIp = getVirtualRouterIP(network, ipAddressRange);

        String networkUuid = implemented.getUuid();
        String tenantId = context.getDomain().getName() + "-" + context.getAccount().getAccountId();
        String broadcastUriStr = networkUuid + "/" + virtualRouterIp;
        implemented.setBroadcastUri(Networks.BroadcastDomainType.Vsp.toUri(broadcastUriStr));
        implemented.setBroadcastDomainType(Networks.BroadcastDomainType.Vsp);
        s_logger.info("Implemented OK, network " + networkUuid + " in tenant " + tenantId + " linked to " + implemented.getBroadcastUri().toString());

        try {
            //Check if the network is associated to a VPC
            Long vpcId = network.getVpcId();
            boolean isVpc = (vpcId != null);

            //Check owner of the Network
            Domain networksDomain = _domainDao.findById(network.getDomainId());

            //get the Account details and find the type
            AccountVO networksAccount = _accountDao.findById(network.getAccountId());
            if (networksAccount.getType() == Account.ACCOUNT_TYPE_PROJECT) {
                String errorMessage = "CS project support is not yet implemented in NuageVsp";
                s_logger.debug(errorMessage);
                throw new InsufficientVirtualNetworkCapacityException(errorMessage, Account.class, network.getAccountId());
            } else {
                implementNetwork(network, offering, physicalNetworkId, ipAddressRange, vpcId, isVpc, networksDomain, networksAccount);
            }
        } catch (NuageVspAPIUtilException e) {
            throw new InsufficientVirtualNetworkCapacityException(e.getMessage(), Network.class, network.getId());
        }

        return implemented;
    }

    @Override
    public NicProfile allocate(Network network, NicProfile nic, VirtualMachineProfile vm) throws InsufficientVirtualNetworkCapacityException, InsufficientAddressCapacityException {
        return super.allocate(network, nic, vm);
    }

    @Override
    protected boolean canHandle(NetworkOffering offering, final NetworkType networkType, final PhysicalNetwork physicalNetwork) {
        // This guru handles only Guest Isolated network that supports Source nat service
        if (networkType == NetworkType.Advanced && isMyTrafficType(offering.getTrafficType()) && offering.getGuestType() == Network.GuestType.Isolated
                && isMyIsolationMethod(physicalNetwork)) {
            return true;
        } else {
            s_logger.trace("We only take care of Guest networks of type   " + GuestType.Isolated + " in zone of type " + NetworkType.Advanced);
            return false;
        }
    }

    @Override
    public void reserve(NicProfile nic, Network network, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
            throws InsufficientVirtualNetworkCapacityException, InsufficientAddressCapacityException {
        super.reserve(nic, network, vm, dest, context);

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Handling reserve() call back to with Create a new VM " + vm.getInstanceName() + " or add an interface " + nic.getIp4Address()
                    + " to existing VM in network " + network.getName());
        }
        addVmInterfaceOrCreateVMNuageVsp(network, vm, nic);
    }

    @DB
    private void implementNetwork(Network network, NetworkOffering offering, Long physicalNetworkId, Collection<String> ipAddressRange, Long vpcId, boolean isVpc,
            Domain networksDomain, AccountVO networksAccount) throws NuageVspAPIUtilException {
        NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(getNuageVspHost(physicalNetworkId));
        String[] enterpriseAndGroupId = NuageVspApiUtil.getOrCreateVSPEnterpriseAndGroup(networksDomain.getName(), networksDomain.getPath(), networksDomain.getUuid(),
                networksAccount.getAccountName(), networksAccount.getUuid(), nuageVspAPIParamsAsCmsUser);

        long networkId = network.getId();
        Boolean isIpAccessControlFeatureEnabled = Boolean.valueOf(_configDao.getValue(NuageVspManager.NuageVspIpAccessControl.key()));
        network = _networkDao.acquireInLockTable(network.getId(), 1200);
        if (network == null) {
            throw new ConcurrentOperationException("Unable to acquire lock on network " + networkId);
        }
        try {
            JSONArray jsonArray = new JSONArray();
            jsonArray.put(enterpriseAndGroupId[1]);
            //this is not owned by project probably this is users network, hopefully the account ID matches user Id
            //Since this is a new network now create a L2domaitemplate and instantiate it and add the subnet
            //or create a L3 DomainTemplate and instantiate it
            if (isVpc || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.SourceNat)
                    || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.StaticNat)) {
                //get the details of DNS server setting to be set on the network
                List<String> dnsServers = _nuageVspManager.getDnsDetails(network);
                List<String> gatewaySystemIds = _nuageVspManager.getGatewaySystemIds();

                if (isVpc) {
                    Vpc vpcObj = _vpcDao.findById(vpcId);
                    String vpcDomainTemplateName = _configDao.getValue(NuageVspManager.NuageVspVpcDomainTemplateName.key());
                    NuageVspApiUtil.createVPCOrL3NetworkWithDefaultACLs(enterpriseAndGroupId[0], network.getName(), network.getId(), NetUtils.getCidrNetmask(network.getCidr()),
                            NetUtils.getCidrSubNet(network.getCidr()), network.getGateway(), network.getNetworkACLId(), dnsServers, gatewaySystemIds,
                            ipAddressRange, offering.getEgressDefaultPolicy(), network.getUuid(), jsonArray, nuageVspAPIParamsAsCmsUser, vpcObj.getName(),
                            vpcObj.getUuid(), isIpAccessControlFeatureEnabled, vpcDomainTemplateName);
                } else {
                    //Create an L3 DomainTemplate
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Handling implement() call back for network " + network.getName() + ". Check with VSP to see if a Isolated L3 networks exist or not");
                    }
                    String isolatedNetworkDomainTemplateName = _configDao.getValue(NuageVspManager.NuageVspIsolatedNetworkDomainTemplateName.key());
                    NuageVspApiUtil.createIsolatedL3NetworkWithDefaultACLs(enterpriseAndGroupId[0], network.getName(), network.getId(), NetUtils.getCidrNetmask(network.getCidr()),
                            NetUtils.getCidrSubNet(network.getCidr()), network.getGateway(), network.getNetworkACLId(), dnsServers, gatewaySystemIds,
                            ipAddressRange, offering.getEgressDefaultPolicy(), network.getUuid(), jsonArray, isIpAccessControlFeatureEnabled, nuageVspAPIParamsAsCmsUser,
                            isolatedNetworkDomainTemplateName);
                }
            } else {
                //Create a L2 DomainTemplate
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Handling implement() call back for network " + network.getName() + ". Check with VSP to see if a  Isolated L2 networks exist or not");
                }

                NuageVspApiUtil.createIsolatedL2NetworkWithDefaultACLs(enterpriseAndGroupId[0], network.getName(), NetUtils.getCidrNetmask(network.getCidr()),
                        NetUtils.getCidrSubNet(network.getCidr()), network.getGateway(), ipAddressRange, offering.getEgressDefaultPolicy(), network.getUuid(), jsonArray,
                        nuageVspAPIParamsAsCmsUser);
            }
        } finally {
            if (network != null) {
                _networkDao.releaseFromLockTable(network.getId());
            }
        }
    }

    @DB
    private void addVmInterfaceOrCreateVMNuageVsp(Network network, VirtualMachineProfile vm, NicProfile allocatedNic) throws InsufficientVirtualNetworkCapacityException {
        //NicProfile does not contain the NIC UUID. We need this information to set it in the VMInterface and VPort
        //that we create in VSP
        DataCenter dc = _dcDao.findById(network.getDataCenterId());
        Account networksAccount = _accountDao.findById(network.getAccountId());
        DomainVO networksDomain = _domainDao.findById(network.getDomainId());
        Object[] attachedNetworkDetails;
        Boolean isIpAccessControlFeatureEnabled = Boolean.valueOf(_configDao.getValue(NuageVspManager.NuageVspIpAccessControl.key()));
        boolean domainRouter = false;
        try {
            NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(getNuageVspHost(network.getPhysicalNetworkId()));
            long networkOwnedBy = network.getAccountId();
            //get the Account details and find the type
            AccountVO neworkAccountDetails = _accountDao.findById(networkOwnedBy);
            if (neworkAccountDetails.getType() == Account.ACCOUNT_TYPE_PROJECT) {
                throw new InsufficientVirtualNetworkCapacityException("CS project support is not yet implemented in NuageVsp", DataCenter.class, dc.getId());
            } else {
                //this is not owned by project probably this is users network, hopefully the account ID matches user Id
                //Since this is a new network now create a L2domaitemplate and instantiate it and add the subnet
                //or create a L3 DomainTemplate and instantiate it
                //Get the Nuage VSP configuration details
                attachedNetworkDetails = getAttachedNetworkDetails(network, networksDomain, nuageVspAPIParamsAsCmsUser);
            }
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("VSP Network to create VM " + vm.getInstanceName() + " or attach an interface " + allocatedNic.getIp4Address() + " is "
                        + attachedNetworkDetails[0] + "/" + attachedNetworkDetails[1] + " for ACS network " + network.getName());
            }

            NicVO nic = _nicDao.findById(allocatedNic.getId());
            List<Map<String, String>> vmInterfaceList = new ArrayList<Map<String, String>>();
            Map<String, String> vmInterfaces = new HashMap<String, String>();
            vmInterfaces.put(NuageVspAttribute.VM_INTERFACE_NAME.getAttributeName(), nic.getUuid());
            vmInterfaces.put(NuageVspAttribute.VM_INTERFACE_MAC.getAttributeName(), allocatedNic.getMacAddress());
            vmInterfaces.put(NuageVspAttribute.EXTERNAL_ID.getAttributeName(), nic.getUuid());
            //If VM is a Virtual Router then set the static that was reserved earlier
            if (vm.getType().equals(VirtualMachine.Type.DomainRouter)) {
                domainRouter = true;
                vmInterfaces.put(NuageVspAttribute.VM_INTERFACE_IPADDRESS.getAttributeName(), network.getBroadcastUri().getPath().substring(1));
            }

            vmInterfaceList.add(vmInterfaces);

            String vmJsonString = NuageVspApiUtil.getVMDetails(network.getUuid(), vm.getUuid(), nuageVspAPIParamsAsCmsUser);
            String vmInterfacesDetails;
            String[] vportAndDomainId = null;

            //now execute all the APIs a the network's account user. So reset the nuage API parameters
            NuageVspAPIParams nuageVspAPIParamsAsNtwkAccUser = NuageVspApiUtil.getNuageVspAPIParameters(networksDomain.getUuid(), networksAccount.getUuid(), false,
                    getNuageVspHost(network.getPhysicalNetworkId()));
            if (vmJsonString == null || StringUtils.isBlank(vmJsonString)) {
                //VM does not exists in VSP. So, create the VM in VSP
                vmInterfacesDetails = NuageVspApiUtil.createVMInVSP(vm.getInstanceName(), vm.getUuid(), vmInterfaceList, attachedNetworkDetails, nuageVspAPIParamsAsCmsUser,
                        nuageVspAPIParamsAsNtwkAccUser);
                if (vmInterfacesDetails != null) {
                    vportAndDomainId = setIPGatewayMaskInfo(network, allocatedNic, vmInterfacesDetails, true);
                } else {
                    String error = "Failed to get IP for the VM " + vm.getInstanceName() + " from VSP address for network " + network.getName();
                    s_logger.error(error);
                    throw new InsufficientVirtualNetworkCapacityException(error, Network.class, network.getId());
                }
            } else {
                //VM already exists, so just add the VM interface to the VM
                vmInterfacesDetails = NuageVspApiUtil.addVMInterfaceToVM(network.getUuid(), vm.getInstanceName(), vm.getUuid(), vmInterfaceList, allocatedNic.getMacAddress(),
                        vmJsonString, attachedNetworkDetails, nuageVspAPIParamsAsCmsUser, nuageVspAPIParamsAsNtwkAccUser);
                if (vmInterfacesDetails != null) {
                    vportAndDomainId = setIPGatewayMaskInfo(network, allocatedNic, vmInterfacesDetails, true);
                } else {
                    s_logger.info("Interface with MAC " + allocatedNic.getMacAddress() + " already exists in Nuage VSP. So, it is not added to the VM " + vm.getInstanceName()
                            + " in network" + network.getName());
                }
            }
            IPAddressVO staticNatIp = _ipAddressDao.findByVmIdAndNetworkId(network.getId(), vm.getId());
            if (!domainRouter && staticNatIp != null && staticNatIp.getState().equals(IpAddress.State.Allocated) && staticNatIp.isOneToOneNat()) {
                s_logger.debug("Found a StaticNat(in ACS DB) " + staticNatIp.getAddress() + " in Allocated state and it is associated to VM " + vm.getInstanceName()
                        + ". Trying to check if this StaticNAT is in sync with VSP.");
                if (vmInterfacesDetails == null && StringUtils.isNotBlank(vmJsonString)) {
                    //Case were this is a VM restart
                    List<Map<String, Object>> vmDetails = NuageVspApiUtil.parseJson(vmJsonString, NuageVspEntity.VM);
                    vmInterfacesDetails = (String)vmDetails.iterator().next().get(NuageVspAttribute.VM_INTERFACES.getAttributeName());
                    vportAndDomainId = setIPGatewayMaskInfo(network, allocatedNic, vmInterfacesDetails, false);
                }
                try {
                    if (vportAndDomainId != null) {
                        //Check if the VM in VSD has this StaticNAT and apply it if needed
                        VlanVO staticNatVlan = _vlanDao.findById(staticNatIp.getVlanId());
                        NuageVspApiUtil.applyStaticNatInVSP(network.getName(), network.getUuid(), nuageVspAPIParamsAsCmsUser, vportAndDomainId[1],
                                attachedNetworkDetails[0].equals(NuageVspEntity.SUBNET) ? NuageVspEntity.DOMAIN : NuageVspEntity.L2DOMAIN, (String)attachedNetworkDetails[1],
                                (String)attachedNetworkDetails[3], ((Boolean)attachedNetworkDetails[2]), staticNatIp.getAddress().addr(), staticNatIp.getUuid(),
                                staticNatVlan.getVlanGateway(), staticNatVlan.getVlanNetmask(), staticNatIp.isAccessControl(), isIpAccessControlFeatureEnabled,
                                staticNatVlan.getUuid(), allocatedNic.getIp4Address(), null, vportAndDomainId[0], null);
                    }
                } catch (Exception e) {
                    s_logger.warn("Post processing of StaticNAT could not continue. Error happened while checking if StaticNat " + staticNatIp.getAddress()
                            + " is in Sync with VSP. " + e.getMessage());
                }
            }
        } catch (NuageVspAPIUtilException e) {
            throw new InsufficientVirtualNetworkCapacityException(e.getMessage(), Network.class, network.getId());
        }
    }

    @Override
    @DB
    public void deallocate(Network network, NicProfile nic, VirtualMachineProfile vm) {
        long networkId = network.getId();
        network = _networkDao.acquireInLockTable(networkId, 1200);
        if (network == null) {
            throw new ConcurrentOperationException("Unable to acquire lock on network " + networkId);
        }
        try {
            try {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Handling deallocate() call back, which is called when a VM is destroyed or interface is removed, to delete VM Interface with IP "
                            + nic.getIp4Address() + " from a VM " + vm.getInstanceName() + " associated to network " + network.getName() + " with state "
                            + vm.getVirtualMachine().getState());
                }
                // Execute a split API call to delete the VM
                NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(getNuageVspHost(network.getPhysicalNetworkId()));

                String vmJsonString = NuageVspApiUtil.getVMDetails(network.getUuid(), vm.getUuid(), nuageVspAPIParamsAsCmsUser);

                if (!StringUtils.isBlank(vmJsonString)) {
                    //get the VM ID
                    List<Map<String, Object>> vmDetailsMap = NuageVspApiUtil.parseJson(vmJsonString, NuageVspEntity.VM);
                    Map<String, Object> vmDetails = vmDetailsMap.iterator().next();
                    String vmInterfaceJsonString = (String)vmDetails.get(NuageVspAttribute.VM_INTERFACES.getAttributeName());
                    String vmId = (String)vmDetails.get(NuageVspAttribute.ID.getAttributeName());
                    List<Map<String, Object>> vmInterfaceDetails = null;
                    if (!StringUtils.isBlank(vmInterfaceJsonString)) {
                        vmInterfaceDetails = NuageVspApiUtil.parseJson(vmInterfaceJsonString, NuageVspEntity.VM_INTERFACE);
                    }

                    if (vmInterfaceDetails != null && vmInterfaceDetails.size() > 0) {
                        // This is a case of a NIC being removed.
                        Map<String, Object> deletedVmInterfaceDetail = null;
                        String macAddr = null;
                        for (Map<String, Object> vmInterfaceDetail : vmInterfaceDetails) {
                            macAddr = (String)vmInterfaceDetail.get(NuageVspAttribute.VM_INTERFACE_MAC.getAttributeName());
                            if (macAddr.equals(nic.getMacAddress())) {
                                deletedVmInterfaceDetail = vmInterfaceDetail;
                                break;
                            }
                        }
                        if (deletedVmInterfaceDetail != null) {
                            String vmInterfaceID = (String)deletedVmInterfaceDetail.get(NuageVspAttribute.ID.getAttributeName());
                            String vPortId = (String)deletedVmInterfaceDetail.get(NuageVspAttribute.VM_INTERFACE_VPORT_ID.getAttributeName());
                            NuageVspApiUtil.deleteVmInterface(vm.getUuid(), macAddr, vmInterfaceID, nuageVspAPIParamsAsCmsUser);
                            if (s_logger.isDebugEnabled()) {
                                s_logger.debug("Handling deallocate(). Deleted VM interface with IP " + nic.getIp4Address() + " for VM " + vm.getInstanceName()
                                        + " associated to network " + network.getName() + " with MAC " + macAddr + " from VM with UUID " + vm.getUuid());
                            }
                            //clean up the stale Vport object that is attached to the VMInterface
                            NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.VPORT, vPortId, nuageVspAPIParamsAsCmsUser);
                        } else {
                            if (s_logger.isDebugEnabled()) {
                                s_logger.debug("Handling deallocate(). VM," + vm.getInstanceName() + " associated to network " + network.getName() + ", interface with IP "
                                        + nic.getIp4Address() + " and MAC " + nic.getMacAddress() + " is not present in VSP");
                            }
                        }
                    }
                    if ((vmInterfaceDetails == null || vmInterfaceDetails.size() == 0 || vmInterfaceDetails.size() == 1)
                            && vm.getVirtualMachine().getState().equals(VirtualMachine.State.Expunging)) {
                        //cleanup the VM as there are no VMInterfaces...
                        if (s_logger.isDebugEnabled()) {
                            s_logger.debug("Handling deallocate(). VM," + vm.getInstanceName() + " associated to network " + network.getName() + ", with interface IP "
                                    + nic.getIp4Address() + " and MAC " + nic.getMacAddress() + " is in Expunging state. So, delete this VM from VSP");
                        }
                        NuageVspApiUtil.deleteVM(vm.getUuid(), nuageVspAPIParamsAsCmsUser, vmId);
                    }
                } else {
                    cleanVMVPorts(network, nic, vm, nuageVspAPIParamsAsCmsUser);
                }
            } catch (NuageVspAPIUtilException e) {
                s_logger.error("Handling deallocate(). VM " + vm.getInstanceName() + " associated to network " + network.getName() + " with NIC IP " + nic.getIp4Address()
                        + " is getting destroyed. REST API failed to update the VM state in NuageVsp", e);
            }
        } finally {
            if (network != null) {
                _networkDao.releaseFromLockTable(network.getId());
            }
        }

        super.deallocate(network, nic, vm);
    }

    private void cleanVMVPorts(Network network, NicProfile nic, VirtualMachineProfile vm, NuageVspAPIParams nuageVspAPIParamsAsCmsUser) throws NuageVspAPIUtilException {
        s_logger.info("Handling deallocate(). VM " + vm.getInstanceName() + " associated to network " + network.getName() + " with NIC IP " + nic.getIp4Address()
                + " is getting destroyed and it does not exists in NuageVSP.");
        //Clean up the VPorts
        NicVO nicVo = _nicDao.findById(nic.getId());
        DomainVO networksDomain = _domainDao.findById(network.getDomainId());
        Object[] attachedNetworkDetails = getAttachedNetworkDetails(network, networksDomain, nuageVspAPIParamsAsCmsUser);
        String vportId = NuageVspApiUtil.findEntityIdByExternalUuid((NuageVspEntity)attachedNetworkDetails[0], (String)attachedNetworkDetails[1], NuageVspEntity.VPORT,
                nicVo.getUuid(), nuageVspAPIParamsAsCmsUser);
        if (StringUtils.isNotBlank(vportId)) {
            NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.VPORT, vportId, nuageVspAPIParamsAsCmsUser);
        }
    }

    @Override
    public void shutdown(NetworkProfile profile, NetworkOffering offering) {
        super.shutdown(profile, offering);
    }

    @Override
    @DB
    public boolean trash(Network network, NetworkOffering offering) {
        boolean result = true;
        long networkId = network.getId();
        network = _networkDao.acquireInLockTable(networkId, 1200);
        if (network == null) {
            throw new ConcurrentOperationException("Unable to acquire lock on network " + networkId);
        }
        try {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Handling trash() call back to delete the network " + network.getName() + " with uuid " + network.getUuid() + " from VSP");
            }
            String vspNetworkId = null;
            // Clean up all the network that was created
            long domainId = network.getDomainId();
            Domain domain = _domainDao.findById(domainId);
            try {
                NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(getNuageVspHost(network.getPhysicalNetworkId()));

                String enterpriseId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, null, null, domain.getUuid(), nuageVspAPIParamsAsCmsUser);
                if (StringUtils.isNotBlank(enterpriseId)) {
                    if (_ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.SourceNat)
                            || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.StaticNat)) {
                        Long vpcId = network.getVpcId();
                        boolean isVpc = (vpcId != null);
                        if (isVpc) {
                            Vpc vpcObj = _vpcDao.findById(vpcId);
                            String vspDomainId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, vpcObj.getUuid(),
                                    nuageVspAPIParamsAsCmsUser);
                            if (StringUtils.isNotBlank(vspDomainId)) {
                                vspNetworkId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.DOMAIN, vspDomainId, NuageVspEntity.SUBNET, network.getUuid(),
                                        nuageVspAPIParamsAsCmsUser);
                                if (StringUtils.isNotBlank(vspNetworkId)) {
                                    if (s_logger.isDebugEnabled()) {
                                        s_logger.debug("Found a VSP L3 network " + vspNetworkId + " that corresponds to tier " + network.getName() + " in CS. So, delete it");
                                    }
                                    result = NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.SUBNET, vspNetworkId, nuageVspAPIParamsAsCmsUser, Arrays.asList(NuageVspApi.s_networkModificationError));
                                }
                            }
                        } else {
                            //get the L3 DomainTemplate with externalUuid
                            String domainTemplateId = NuageVspApiUtil.findFieldValueByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN,
                                    network.getUuid(), NuageVspAttribute.DOMAIN_TEMPLATE_ID.getAttributeName(), nuageVspAPIParamsAsCmsUser);
                            String isolatedNetworkDomainTemplateName = _configDao.getValue(NuageVspManager.NuageVspIsolatedNetworkDomainTemplateName.key());
                            String isolatedNetworkDomainTemplateEntity = NuageVspApiUtil.findEntityUsingFilter(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN_TEMPLATE,
                                    "name", isolatedNetworkDomainTemplateName, nuageVspAPIParamsAsCmsUser);
                            String isolatedNetworkDomainTemplateId = NuageVspApiUtil.getEntityId(isolatedNetworkDomainTemplateEntity, NuageVspEntity.DOMAIN_TEMPLATE);
                            if (domainTemplateId.equals(isolatedNetworkDomainTemplateId)) {
                                vspNetworkId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, network.getUuid(),
                                        nuageVspAPIParamsAsCmsUser);
                                if (StringUtils.isNotBlank(vspNetworkId)) {
                                    if (s_logger.isDebugEnabled()) {
                                        s_logger.debug("Found a VSP L3 network " + vspNetworkId + " that corresponds to network " + network.getName() + " in CS. So, delete it");
                                    }
                                    result = NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.DOMAIN, vspNetworkId, nuageVspAPIParamsAsCmsUser, Arrays.asList(NuageVspApi.s_networkModificationError));
                                }
                            } else {
                                vspNetworkId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN_TEMPLATE, network.getUuid(),
                                        nuageVspAPIParamsAsCmsUser);
                                if (StringUtils.isNotBlank(vspNetworkId)) {
                                    if (s_logger.isDebugEnabled()) {
                                        s_logger.debug("Found a VSP L3 network " + vspNetworkId + " that corresponds to network " + network.getName() + " in CS. So, delete it");
                                    }
                                    result = NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.DOMAIN_TEMPLATE, vspNetworkId, nuageVspAPIParamsAsCmsUser, Arrays.asList(NuageVspApi.s_networkModificationError));
                                }
                            }
                        }
                    } else {
                        //Create a L2 DomainTemplate
                        vspNetworkId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.L2DOMAIN_TEMPLATE, network.getUuid(),
                                nuageVspAPIParamsAsCmsUser);
                        if (StringUtils.isNotBlank(vspNetworkId)) {
                            result = NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.L2DOMAIN_TEMPLATE, vspNetworkId, nuageVspAPIParamsAsCmsUser);
                            if (s_logger.isDebugEnabled()) {
                                s_logger.debug("Found a VSP L2 network " + vspNetworkId + " that corresponds to network " + network.getName() + " in CS and deleted it");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                s_logger.warn("Failed to clean up network " + network.getName() + " information in Vsp " + e.getMessage());
                result = false;
            }
        } finally {
            if (network != null) {
                _networkDao.releaseFromLockTable(network.getId());
            }
        }

        return result && super.trash(network, offering);
    }

    private HostVO getNuageVspHost(Long physicalNetworkId) throws NuageVspAPIUtilException {
        // TODO: Cache this as we don't want a DB hit everytime we send a message to VSD
        HostVO nuageVspHost;
        List<NuageVspDeviceVO> nuageVspDevices = _nuageVspDao.listByPhysicalNetwork(physicalNetworkId);
        if (nuageVspDevices != null && (!nuageVspDevices.isEmpty())) {
            NuageVspDeviceVO config = nuageVspDevices.iterator().next();
            nuageVspHost = _hostDao.findById(config.getHostId());
            _hostDao.loadDetails(nuageVspHost);
        } else {
            throw new NuageVspAPIUtilException("Nuage VSD is not configured on physical network " + physicalNetworkId);
        }
        return nuageVspHost;
    }

    private String getVirtualRouterIP(Network network, Collection<String> addressRange) throws InsufficientVirtualNetworkCapacityException {
        String virtualRouterIp;
        //Check if the subnet has minimum 5 host in it.
        String subnet = NetUtils.getCidrSubNet(network.getCidr());
        String netmask = NetUtils.getCidrNetmask(network.getCidr());
        long cidrSize = NetUtils.getCidrSize(netmask);

        Set<Long> allIPsInCidr = NetUtils.getAllIpsFromCidr(subnet, cidrSize, new HashSet<Long>());

        if (allIPsInCidr.size() > 3) {
            //get the second IP and see if it the networks GatewayIP
            Iterator<Long> ipIterator = allIPsInCidr.iterator();
            long vip = ipIterator.next();
            if (NetUtils.ip2Long(network.getGateway()) == vip) {
                s_logger.debug("Gateway of the Network(" + network.getName() + ") has the first IP " + NetUtils.long2Ip(vip));
                vip = ipIterator.next();
                virtualRouterIp = NetUtils.long2Ip(vip);
                s_logger.debug("So, reserving the 2nd IP " + virtualRouterIp + " for the Virtual Router IP in Network(" + network.getName() + ")");
            } else {
                virtualRouterIp = NetUtils.long2Ip(vip);
                s_logger.debug("1nd IP is not used as the gateway IP. So, reserving" + virtualRouterIp + " for the Virtual Router IP for " + "Network(" + network.getName() + ")");
            }
            addressRange.add(NetUtils.long2Ip(ipIterator.next()));
            addressRange.add(NetUtils.getIpRangeEndIpFromCidr(subnet, cidrSize));
            return virtualRouterIp;
        }

        throw new InsufficientVirtualNetworkCapacityException("VSP allocates an IP for VirtualRouter." + " So, subnet should have atleast minimum 4 hosts ", Network.class,
                network.getId());
    }

    private String[] setIPGatewayMaskInfo(Network network, NicProfile allocatedNic, String vmInterfacesDetails, boolean setGatewayInfo) throws InsufficientVirtualNetworkCapacityException {
        String[] vportAndDomainId = null;
        try {
            List<Map<String, Object>> vmInterfacesList = NuageVspApi.parseJsonString(NuageVspEntity.VM_INTERFACE, vmInterfacesDetails);
            for (Map<String, Object> interfaces : vmInterfacesList) {
                String macFromNuage = (String)interfaces.get(NuageVspAttribute.VM_INTERFACE_MAC.getAttributeName());
                if (StringUtils.equals(macFromNuage, allocatedNic.getMacAddress())) {
                    if (setGatewayInfo) {
                        allocatedNic.setIp4Address((String)interfaces.get(NuageVspAttribute.VM_INTERFACE_IPADDRESS.getAttributeName()));
                        allocatedNic.setGateway((String)interfaces.get(NuageVspAttribute.VM_INTERFACE_GATEWAY.getAttributeName()));
                        allocatedNic.setNetmask((String)interfaces.get(NuageVspAttribute.VM_INTERFACE_NETMASK.getAttributeName()));
                    }
                    vportAndDomainId = new String[2];
                    vportAndDomainId[0] = (String)interfaces.get(NuageVspAttribute.VM_INTERFACE_VPORT_ID.getAttributeName());
                    vportAndDomainId[1] = (String)interfaces.get(NuageVspAttribute.VM_INTERFACE_DOMAIN_ID.getAttributeName());
                    break;
                }
            }
        } catch (Exception e) {
            s_logger.error("Failed to parse the VM interface Json response from VSP REST API. VM interface json string is  " + vmInterfacesDetails, e);
            throw new InsufficientVirtualNetworkCapacityException("Failed to parse the VM interface Json response from VSP REST API. VM interface Json " + "string is  "
                    + vmInterfacesDetails + ". So. failed to get IP for the VM from VSP address for network " + network, Network.class, network.getId());
        }
        return vportAndDomainId;
    }

    private Object[] getAttachedNetworkDetails(Network network, DomainVO networksDomain, NuageVspAPIParams nuageVspAPIParamsAsCmsUser) throws NuageVspAPIUtilException {
        Object[] attachedNetworkDetails = new Object[4];
        long networkOfferingId = _ntwkOfferingDao.findById(network.getNetworkOfferingId()).getId();
        String enterpriseId = NuageVspApiUtil.getEnterprise(networksDomain.getUuid(), nuageVspAPIParamsAsCmsUser);
        if (_ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(networkOfferingId, Service.SourceNat)
                || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(networkOfferingId, Service.StaticNat)) {
            Long vpcId = network.getVpcId();
            boolean isVpc = (vpcId != null);
            if (isVpc) {
                Vpc vpcObj = _vpcDao.findById(vpcId);
                attachedNetworkDetails[1] = NuageVspApiUtil.getIsolatedSubNetwork(enterpriseId, network.getUuid(), nuageVspAPIParamsAsCmsUser, vpcObj.getUuid());
                attachedNetworkDetails[2] = Boolean.TRUE;
                attachedNetworkDetails[3] = vpcObj.getUuid();
            } else {
                attachedNetworkDetails[1] = NuageVspApiUtil.getIsolatedSubNetwork(enterpriseId, network.getUuid(), nuageVspAPIParamsAsCmsUser);
                attachedNetworkDetails[2] = Boolean.FALSE;
                attachedNetworkDetails[3] = network.getUuid();
            }
            attachedNetworkDetails[0] = NuageVspEntity.SUBNET;
        } else {
            attachedNetworkDetails[1] = NuageVspApiUtil.getIsolatedDomain(enterpriseId, network.getUuid(), NuageVspEntity.L2DOMAIN, nuageVspAPIParamsAsCmsUser);
            attachedNetworkDetails[0] = NuageVspEntity.L2DOMAIN;
        }
        return attachedNetworkDetails;
    }

}