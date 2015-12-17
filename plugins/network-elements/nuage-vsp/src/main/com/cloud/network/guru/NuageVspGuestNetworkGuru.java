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

import com.cloud.utils.exception.CloudRuntimeException;
import net.nuage.vsp.client.common.model.NuageVspAPIParams;
import net.nuage.vsp.client.common.model.NuageVspAttribute;
import net.nuage.vsp.client.common.model.NuageVspEntity;
import net.nuage.vsp.client.exception.NuageVspAPIUtilException;
import net.nuage.vsp.client.rest.NuageVspApi;
import net.nuage.vsp.client.rest.NuageVspApiUtil;
import net.nuage.vsp.client.rest.NuageVspConstants;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.amazonaws.util.json.JSONArray;

import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.VlanVO;
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
import com.cloud.network.IpAddress;
import com.cloud.network.Network;
import com.cloud.network.Network.GuestType;
import com.cloud.network.Network.Service;
import com.cloud.network.Network.State;
import com.cloud.network.NetworkProfile;
import com.cloud.network.Networks;
import com.cloud.network.NuageVspDeviceVO;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.PhysicalNetwork.IsolationMethod;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.NetworkDetailVO;
import com.cloud.network.dao.NetworkDetailsDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.NuageVspDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.manager.NuageVspManager;
import com.cloud.network.vpc.Vpc;
import com.cloud.network.vpc.dao.VpcDao;
import com.cloud.offering.NetworkOffering;
import com.cloud.offerings.NetworkOfferingVO;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.offerings.dao.NetworkOfferingServiceMapDao;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.util.ExperimentalFeatureLoader;
import com.cloud.util.NuageVspUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.db.DB;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.NicProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;

import static com.cloud.util.ExperimentalFeatureLoader.ExperimentalFeature.CONCURRENT_VSD_OPS;

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
    @Inject
    NetworkDetailsDao _networkDetailsDao;
    @Inject
    ExperimentalFeatureLoader _expFeatureLoader;

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

        Collection<String[]> ipAddressRanges = new ArrayList<String[]>();
        String virtualRouterIp = getVirtualRouterIP(network, ipAddressRanges);

        String networkUuid = network.getUuid();
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
                implementNetwork(network, offering, physicalNetworkId, ipAddressRanges, vpcId, isVpc, networksDomain, networksAccount);
            }
        } catch (NuageVspAPIUtilException e) {
            throw new InsufficientVirtualNetworkCapacityException(e.getMessage(), Network.class, network.getId());
        }

        return implemented;
    }

    @Override
    public NicProfile allocate(Network network, NicProfile nic, VirtualMachineProfile vm) throws InsufficientVirtualNetworkCapacityException, InsufficientAddressCapacityException {
        String vrIp;
        if (network.getBroadcastUri() != null) {
            vrIp = network.getBroadcastUri().getPath().substring(1);
        } else {
            vrIp = getVirtualRouterIP(network, null);
        }

        if (vm.getType() != VirtualMachine.Type.DomainRouter && nic != null && vrIp.equals(nic.getRequestedIpv4())) {
            DataCenter dc = _dcDao.findById(network.getDataCenterId());
            throw new InsufficientVirtualNetworkCapacityException("Unable to acquire Guest IP address for network " + network, DataCenter.class,
                    dc.getId());
        }

        return super.allocate(network, nic, vm);
    }

    @Override
    protected boolean canHandle(NetworkOffering offering, final NetworkType networkType, final PhysicalNetwork physicalNetwork) {
        // This guru handles only Guest Isolated network that supports Source nat service
        if (networkType == NetworkType.Advanced && isMyTrafficType(offering.getTrafficType()) && (offering.getGuestType() == Network.GuestType.Isolated || offering.getGuestType() == Network.GuestType.Shared)
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

        NetworkOfferingVO offering = _ntwkOfferingDao.findById(network.getNetworkOfferingId());

        boolean forceIpAddress = vm.getType().equals(VirtualMachine.Type.InternalLoadBalancerVm)
                || offering.getGuestType() == GuestType.Shared;
        // TODO : add if user-selected fixed ip
        addVmInterfaceOrCreateVMNuageVsp(network, vm, nic);
    }

    @DB
    private void implementNetwork(Network network, NetworkOffering offering, Long physicalNetworkId, Collection<String[]> ipAddressRanges, Long vpcId, boolean isVpc,
            Domain networksDomain, AccountVO networksAccount) throws NuageVspAPIUtilException {
        String nuageVspCmdId = NuageVspUtil.findNuageVspDeviceCmsIdByPhysNet(physicalNetworkId, _nuageVspDao, _configDao);
        NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(getNuageVspHost(physicalNetworkId), nuageVspCmdId);
        Pair<String, String> enterpriseAndGroupId = NuageVspUtil.getEnterpriseAndGroupId(networksDomain, _domainDao, networksAccount, _accountDao, nuageVspAPIParamsAsCmsUser);

        boolean useConcurrentVsdOps = _expFeatureLoader.isExperimentalFeatureEnabledForPhysicalNetwork(physicalNetworkId, CONCURRENT_VSD_OPS);
        if (!useConcurrentVsdOps) {
            long networkId = network.getId();
            network = _networkDao.acquireInLockTable(network.getId(), 1200);
            if (network == null) {
                throw new ConcurrentOperationException("Unable to acquire lock on network " + networkId);
            }
        }

        try {
            JSONArray jsonArray = new JSONArray();
            jsonArray.put(enterpriseAndGroupId.second());
            String vsdDomainId = null, vsdSubnetId = null;
            NuageVspEntity entity = null;
            //this is not owned by project probably this is users network, hopefully the account ID matches user Id
            //Since this is a new network now create a L2 DomainTemplate and instantiate it and add the subnet
            //or create a L3 DomainTemplate and instantiate it
            if (isVpc || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.SourceNat)
                    || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.StaticNat)
                    || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.Connectivity)) {
                //get the details of DNS server setting to be set on the network
                List<String> dnsServers = _nuageVspManager.getDnsDetails(network);
                List<String> gatewaySystemIds = _nuageVspManager.getGatewaySystemIds();

                if (isVpc) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Handling implement() callback for network " + network.getName() + " in VPC use case");
                    }
                    Vpc vpcObj = _vpcDao.findById(vpcId);
                    String vpcDomainTemplateName = _configDao.getValue(NuageVspManager.NuageVspVpcDomainTemplateName.key());
                    entity = NuageVspEntity.SUBNET;
                    Pair<String, String> vsdDomainAndSubnetId = NuageVspApiUtil.createVPCOrL3NetworkWithDefaultACLs(enterpriseAndGroupId.first(), network.getName(), network.getId(), NetUtils.getCidrNetmask(network.getCidr()),
                            NetUtils.getCidrSubNet(network.getCidr()), network.getGateway(), network.getNetworkACLId(), dnsServers, gatewaySystemIds,
                            ipAddressRanges, offering.getEgressDefaultPolicy(), network.getUuid(), jsonArray, nuageVspAPIParamsAsCmsUser, vpcObj.getName(),
                            vpcObj.getUuid(), vpcDomainTemplateName);
                    vsdDomainId = vsdDomainAndSubnetId.first();
                    vsdSubnetId = vsdDomainAndSubnetId.second();
                } else if (offering.getGuestType() == GuestType.Shared) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Handling implement() callback for network " + network.getName() + " in Shared Network use case");
                    }

                    // Shared networks use their own ipAddressRanges, therefore overwrite
                    ipAddressRanges = new ArrayList<String[]>();
                    List<VlanVO> vlans = _vlanDao.listVlansByNetworkId(network.getId());
                    for (VlanVO vlan : vlans) {
                        boolean isIpv4 = StringUtils.isNotBlank(vlan.getIpRange());
                        String[] range = isIpv4 ? vlan.getIpRange().split("-") : vlan.getIp6Range().split("-");
                        ipAddressRanges.add(range);
                    }

                    String sharedNetworkDomainTemplateName = _configDao.getValue(NuageVspManager.NuageVspSharedNetworkDomainTemplateName.key());
                    entity = NuageVspEntity.SUBNET;
                    Pair<String, String> vsdDomainAndSubnetId = NuageVspApiUtil.createSharedNetworkWithDefaultACLs(networksDomain.getUuid(), enterpriseAndGroupId.first(), network.getName(), NetUtils.getCidrNetmask(network.getCidr()),
                            NetUtils.getCidrSubNet(network.getCidr()), network.getGateway(), network.getNetworkACLId(), dnsServers, gatewaySystemIds,
                            ipAddressRanges, offering.getEgressDefaultPolicy(), network.getUuid(), jsonArray, nuageVspAPIParamsAsCmsUser, sharedNetworkDomainTemplateName);
                    vsdDomainId = vsdDomainAndSubnetId.first();
                    vsdSubnetId = vsdDomainAndSubnetId.second();
                } else {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Handling implement() callback for network " + network.getName() + " in Isolated Network use case");
                    }
                    String isolatedNetworkDomainTemplateName = _configDao.getValue(NuageVspManager.NuageVspIsolatedNetworkDomainTemplateName.key());
                    entity = NuageVspEntity.SUBNET;
                    Pair<String, String> vsdDomainAndSubnetId = NuageVspApiUtil.createIsolatedL3NetworkWithDefaultACLs(enterpriseAndGroupId.first(), network.getName(), network.getId(), NetUtils.getCidrNetmask(network.getCidr()),
                            NetUtils.getCidrSubNet(network.getCidr()), network.getGateway(), network.getNetworkACLId(), dnsServers, gatewaySystemIds,
                            ipAddressRanges, offering.getEgressDefaultPolicy(), network.getUuid(), jsonArray, nuageVspAPIParamsAsCmsUser, isolatedNetworkDomainTemplateName);
                    vsdDomainId = vsdDomainAndSubnetId.first();
                    vsdSubnetId = vsdDomainAndSubnetId.second();
                }
            } else {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Handling implement() callback for network " + network.getName() + " in Isolated L2 Network use case");
                }
                entity = NuageVspEntity.L2DOMAIN;
                vsdDomainId = NuageVspApiUtil.createIsolatedL2NetworkWithDefaultACLs(enterpriseAndGroupId.first(), network.getName(), NetUtils.getCidrNetmask(network.getCidr()),
                        NetUtils.getCidrSubNet(network.getCidr()), network.getGateway(), ipAddressRanges, offering.getEgressDefaultPolicy(), network.getUuid(), jsonArray,
                        nuageVspAPIParamsAsCmsUser);
            }
            saveNetworkDetails(network, entity, enterpriseAndGroupId.first(), vsdDomainId, vsdSubnetId);
        } finally {
            if (!useConcurrentVsdOps) {
                _networkDao.releaseFromLockTable(network.getId());
            }
        }
    }


    private void saveNetworkDetails(Network network, NuageVspEntity entity, String vsdEnterpriseId, String vsdDomainId, String vsdSubnetId) {
        Map<String, String> networkDetails = NuageVspUtil.constructNetworkDetails(entity, vsdEnterpriseId, vsdDomainId, vsdSubnetId);
        for (Map.Entry<String, String> networkDetail : networkDetails.entrySet()) {
            NetworkDetailVO networkDetailVO = new NetworkDetailVO(network.getId(), networkDetail.getKey(), networkDetail.getValue(), false);
            _networkDetailsDao.persist(networkDetailVO);
        }
    }

    private void removeNetworkDetails(Network network) {
        _networkDetailsDao.removeDetails(network.getId());
    }

    @DB
    public void addVmInterfaceOrCreateVMNuageVsp(Network network, VirtualMachineProfile vm, NicProfile allocatedNic) throws InsufficientVirtualNetworkCapacityException {
        boolean useConcurrentVsdOps = _expFeatureLoader.isExperimentalFeatureEnabledForPhysicalNetwork(network.getPhysicalNetworkId(), CONCURRENT_VSD_OPS);
        boolean lockedNetwork = false;
        if (!useConcurrentVsdOps) {
            lockedNetwork = lockNetworkForUserVm(network, vm);
            if (lockedNetwork) {
                s_logger.debug("Locked network " + network.getId() + " for creation of user VM " + vm.getInstanceName());
            }
        }

        try {
            //NicProfile does not contain the NIC UUID. We need this information to set it in the VMInterface and VPort
            //that we create in VSP
            DataCenter dc = _dcDao.findById(network.getDataCenterId());
            Account networksAccount = _accountDao.findById(network.getAccountId());
            DomainVO networksDomain = _domainDao.findById(network.getDomainId());
            Object[] attachedNetworkDetails;
            boolean domainRouter = false;
            try {
                String nuageVspCmsId = NuageVspUtil.findNuageVspDeviceCmsIdByPhysNet(network.getPhysicalNetworkId(), _nuageVspDao, _configDao);
                NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(getNuageVspHost(network.getPhysicalNetworkId()), nuageVspCmsId);
                long networkOwnedBy = network.getAccountId();
                //get the Account details and find the type                                   *
                AccountVO networkAccountDetails = _accountDao.findById(networkOwnedBy);
                if (networkAccountDetails.getType() == Account.ACCOUNT_TYPE_PROJECT) {
                    throw new InsufficientVirtualNetworkCapacityException("CS project support is not yet implemented in NuageVsp", DataCenter.class, dc.getId());
                } else {
                    //this is not owned by project probably this is users network, hopefully the account ID matches user Id
                    //Since this is a new network now create a L2 Domain Template and instantiate it and add the subnet
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
                } else {
                    vmInterfaces.put(NuageVspAttribute.VM_INTERFACE_IPADDRESS.getAttributeName(), nic.getIp4Address());
                }

                vmInterfaceList.add(vmInterfaces);

                String vmJsonString = NuageVspApiUtil.getVMDetails(network.getUuid(), vm.getUuid(), nuageVspAPIParamsAsCmsUser);
                String vmInterfacesDetails;
                String[] vportAndDomainId = null;

                //now execute all the APIs a the network's account user. So reset the nuage API parameters
                NuageVspAPIParams nuageVspAPIParamsAsNtwkAccUser;
                if (NuageVspManager.NuageVspMultiTenancy.value() == Boolean.FALSE) {
                    Domain rootDomain = _domainDao.findById(Domain.ROOT_DOMAIN);
                    Account systemAccount = _accountDao.findById(Account.ACCOUNT_ID_SYSTEM);
                    nuageVspAPIParamsAsNtwkAccUser = NuageVspApiUtil.getNuageVspAPIParameters(rootDomain.getUuid(), systemAccount.getUuid(), false,
                            getNuageVspHost(network.getPhysicalNetworkId()), nuageVspCmsId);
                } else {
                    nuageVspAPIParamsAsNtwkAccUser = NuageVspApiUtil.getNuageVspAPIParameters(networksDomain.getUuid(), networksAccount.getUuid(), false,
                            getNuageVspHost(network.getPhysicalNetworkId()), nuageVspCmsId);
                }

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
                        s_logger.trace("Interface with MAC " + allocatedNic.getMacAddress() + " is already configured for VM " + vm.getInstanceName() +
                                " in network " + network.getName() + ", not going to reconfigure");
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
                                    staticNatVlan.getVlanGateway(), staticNatVlan.getVlanNetmask(), staticNatVlan.getUuid(), allocatedNic.getIp4Address(), null, vportAndDomainId[0], null);
                        }
                    } catch (Exception e) {
                        s_logger.warn("Post processing of StaticNAT could not continue. Error happened while checking if StaticNat " + staticNatIp.getAddress()
                                + " is in Sync with VSP. " + e.getMessage());
                    }
                }
            } catch (NuageVspAPIUtilException e) {
                throw new InsufficientVirtualNetworkCapacityException(e.getMessage(), Network.class, network.getId());
            }
        } finally {
            if (network != null && lockedNetwork) {
                _networkDao.releaseFromLockTable(network.getId());
                s_logger.debug("Unlocked network " + network.getId() + " for creation of user VM " + vm.getInstanceName());
            }
        }
    }

    @Override
    @DB
    public void deallocate(Network network, NicProfile nic, VirtualMachineProfile vm) {
        boolean useConcurrentVsdOps = _expFeatureLoader.isExperimentalFeatureEnabledForPhysicalNetwork(network.getPhysicalNetworkId(), CONCURRENT_VSD_OPS);
        boolean lockedNetwork = false;
        if (!useConcurrentVsdOps) {
            lockedNetwork = lockNetworkForUserVm(network, vm);
            if (lockedNetwork) {
                s_logger.debug("Locked network " + network.getId() + " for deallocation of user VM " + vm.getInstanceName());
            }
        }

        try {
            try {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Handling deallocate() call back, which is called when a VM is destroyed or interface is removed, to delete VM Interface with IP "
                            + nic.getIp4Address() + " from a VM " + vm.getInstanceName() + " associated to network " + network.getName() + " with state "
                            + vm.getVirtualMachine().getState());
                }
                // Execute a split API call to delete the VM
                String nuageVspCmsId = NuageVspUtil.findNuageVspDeviceCmsIdByPhysNet(network.getPhysicalNetworkId(), _nuageVspDao, _configDao);
                NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(getNuageVspHost(network.getPhysicalNetworkId()), nuageVspCmsId);

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
                String errorMessage = "Handling deallocate(). VM " + vm.getInstanceName() + " associated to network " + network.getName() + " with NIC IP " + nic.getIp4Address()
                        + " is getting destroyed. REST API failed to update the VM state in NuageVsp";
                s_logger.error(errorMessage, e);
                throw new CloudRuntimeException(errorMessage, e);
            }
        } finally {
            if (network != null && lockedNetwork) {
                _networkDao.releaseFromLockTable(network.getId());
                s_logger.debug("Unlocked network " + network.getId() + " for deallocation of user VM " + vm.getInstanceName());
            }
        }

        super.deallocate(network, nic, vm);
    }

    private boolean lockNetworkForUserVm(Network network, VirtualMachineProfile vm) {
        if (!vm.getVirtualMachine().getType().isUsedBySystem()) {
            long networkId = network.getId();
            network = _networkDao.acquireInLockTable(network.getId(), 1200);
            if (network == null) {
                throw new ConcurrentOperationException("Unable to acquire lock on network " + networkId);
            }
            return true;
        }
        return false;
    }

    private void cleanVMVPorts(Network network, NicProfile nic, VirtualMachineProfile vm, NuageVspAPIParams nuageVspAPIParamsAsCmsUser) throws NuageVspAPIUtilException {
        s_logger.info("Handling deallocate(). VM " + vm.getInstanceName() + " associated to network " + network.getName() + " with NIC IP " + nic.getIp4Address()
                + " is getting destroyed and it does not exists in NuageVSP.");
        //Clean up the VPorts
        NicVO nicVo = _nicDao.findById(nic.getId());
        DomainVO networksDomain = _domainDao.findById(network.getDomainId());
        Object[] attachedNetworkDetails = getAttachedNetworkDetails(network, networksDomain, nuageVspAPIParamsAsCmsUser);
        String vportId = NuageVspApiUtil.findEntityIdByExternalUuid((NuageVspEntity) attachedNetworkDetails[0], (String) attachedNetworkDetails[1], NuageVspEntity.VPORT,
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
        boolean useConcurrentVsdOps = _expFeatureLoader.isExperimentalFeatureEnabledForPhysicalNetwork(network.getPhysicalNetworkId(), CONCURRENT_VSD_OPS);
        if (!useConcurrentVsdOps) {
            long networkId = network.getId();
            network = _networkDao.acquireInLockTable(networkId, 1200);
            if (network == null) {
                throw new ConcurrentOperationException("Unable to acquire lock on network " + networkId);
            }
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
                String nuageVspCmsId = NuageVspUtil.findNuageVspDeviceCmsIdByPhysNet(network.getPhysicalNetworkId(), _nuageVspDao, _configDao);
                NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(getNuageVspHost(network.getPhysicalNetworkId()), nuageVspCmsId);

                String enterpriseId = NuageVspUtil.getEnterpriseId(domain, _domainDao, nuageVspAPIParamsAsCmsUser);
                if (StringUtils.isNotBlank(enterpriseId)) {
                    Long vpcId = network.getVpcId();
                    boolean isVpc = (vpcId != null);
                    if (isVpc || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.SourceNat)
                            || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.StaticNat)
                            || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(offering.getId(), Service.Connectivity)) {
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
                        } else if (offering.getGuestType() == GuestType.Shared) {
                            String vspDomainId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, domain.getUuid(),
                                    nuageVspAPIParamsAsCmsUser);
                            if (StringUtils.isNotBlank(vspDomainId)) {
                                int subnetCount = NuageVspApiUtil.getChildrenCount(NuageVspEntity.DOMAIN, vspDomainId, NuageVspEntity.SUBNET, nuageVspAPIParamsAsCmsUser);
                                if (subnetCount > 1) {
                                    vspNetworkId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.DOMAIN, vspDomainId, NuageVspEntity.SUBNET, network.getUuid(),
                                            nuageVspAPIParamsAsCmsUser);
                                    if (s_logger.isDebugEnabled()) {
                                        s_logger.debug("Found a VSP L3 network " + vspNetworkId + " that corresponds to network " + network.getName() + " in CS. So, delete it");
                                    }
                                    result = NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.SUBNET, vspNetworkId, nuageVspAPIParamsAsCmsUser, Arrays.asList(NuageVspApi.s_networkModificationError));
                                } else {
                                    String sharedNetworkDomainTemplateName = _configDao.getValue(NuageVspManager.NuageVspSharedNetworkDomainTemplateName.key());
                                    result = cleanUpDomainAndTemplate(enterpriseId, domain.getUuid(), network.getName(), sharedNetworkDomainTemplateName, nuageVspAPIParamsAsCmsUser);
                                }
                            }

                        } else {
                            String isolatedNetworkDomainTemplateName = _configDao.getValue(NuageVspManager.NuageVspIsolatedNetworkDomainTemplateName.key());
                            result = cleanUpDomainAndTemplate(enterpriseId, network.getUuid(), network.getName(), isolatedNetworkDomainTemplateName, nuageVspAPIParamsAsCmsUser);
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
                removeNetworkDetails(network);
            } catch (Exception e) {
                s_logger.warn("Failed to clean up network " + network.getName() + " information in Vsp " + e.getMessage());
                result = false;
            }
        } finally {
            if (network != null && !useConcurrentVsdOps) {
                _networkDao.releaseFromLockTable(network.getId());
            }
        }

        return result && super.trash(network, offering);
    }

    private boolean cleanUpDomainAndTemplate(String enterpriseId, String networkUuid, String networkName, String preConfiguredDomainTemplateName,
                                             NuageVspAPIParams nuageVspAPIParamsAsCmsUser) throws NuageVspAPIUtilException {
        //get the L3 DomainTemplate with externalUuid
        String domainTemplateId = NuageVspApiUtil.findFieldValueByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN,
                networkUuid, NuageVspAttribute.DOMAIN_TEMPLATE_ID.getAttributeName(), nuageVspAPIParamsAsCmsUser);
        if (domainTemplateId == null) return true;

        String preConfiguredDomainTemplateEntity = NuageVspApiUtil.findEntityUsingFilter(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN_TEMPLATE,
                "name", preConfiguredDomainTemplateName, nuageVspAPIParamsAsCmsUser);
        String preConfiguredDomainTemplateId = NuageVspApiUtil.getEntityId(preConfiguredDomainTemplateEntity, NuageVspEntity.DOMAIN_TEMPLATE);
        String vspNetworkId;
        if (domainTemplateId.equals(preConfiguredDomainTemplateId)) {
            vspNetworkId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN, networkUuid,
                    nuageVspAPIParamsAsCmsUser);
            if (StringUtils.isNotBlank(vspNetworkId)) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Found a VSP L3 network " + vspNetworkId + " that corresponds to network " + networkName + " in CS. So, delete it");
                }
                return NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.DOMAIN, vspNetworkId, nuageVspAPIParamsAsCmsUser, Arrays.asList(NuageVspApi.s_networkModificationError));
            }
        } else {
            vspNetworkId = NuageVspApiUtil.findEntityIdByExternalUuid(NuageVspEntity.ENTERPRISE, enterpriseId, NuageVspEntity.DOMAIN_TEMPLATE, networkUuid,
                    nuageVspAPIParamsAsCmsUser);
            if (StringUtils.isNotBlank(vspNetworkId)) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Found a VSP L3 network " + vspNetworkId + " that corresponds to network " + networkName + " in CS. So, delete it");
                }
                return NuageVspApiUtil.cleanUpVspStaleObjects(NuageVspEntity.DOMAIN_TEMPLATE, vspNetworkId, nuageVspAPIParamsAsCmsUser, Arrays.asList(NuageVspApi.s_networkModificationError));
            }
        }
        return true;
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

    private String getVirtualRouterIP(Network network, Collection<String[]> ipAddressRanges) throws InsufficientVirtualNetworkCapacityException {
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

            if (ipAddressRanges != null) {
                String[] ipAddressRange = new String[2];
                ipAddressRange[0] = NetUtils.long2Ip(ipIterator.next());
                ipAddressRange[1] = NetUtils.getIpRangeEndIpFromCidr(subnet, cidrSize);
                ipAddressRanges.add(ipAddressRange);
            }

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

    private static class NetworkDetails {
        public NuageVspEntity entityType;
        public String entityId;
        public Boolean isVpc;
        public String domainUuid;

        public Object[] getData() {
            return new Object[] {
                entityType, entityId, isVpc, domainUuid
            };
        }
    }

    private Object[] getAttachedNetworkDetails(Network network, DomainVO networksDomain, NuageVspAPIParams nuageVspAPIParamsAsCmsUser) throws NuageVspAPIUtilException {
        NetworkDetails attachedNetworkDetails = new NetworkDetails();
        NetworkOfferingVO networkOffering = _ntwkOfferingDao.findById(network.getNetworkOfferingId());
        long networkOfferingId = networkOffering.getId();
        Long vpcId = network.getVpcId();
        boolean isVpc = (vpcId != null);
        boolean isL3Domain = _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(networkOfferingId, Service.SourceNat)
                || _ntwkOfferingSrvcDao.areServicesSupportedByNetworkOffering(networkOfferingId, Service.StaticNat);

        Map<String, String> networkDetails = _networkDetailsDao.listDetailsKeyPairs(network.getId(), false);
        if (MapUtils.isNotEmpty(networkDetails)) {
            attachedNetworkDetails.entityType = NuageVspEntity.valueOf(networkDetails.get(NuageVspConstants.NETWORK_METADATA_TYPE));
            if (isL3Domain) {
                attachedNetworkDetails.entityId = networkDetails.get(NuageVspConstants.NETWORK_METADATA_VSD_SUBNET_ID);
                attachedNetworkDetails.isVpc = isVpc;
                if (isVpc) {
                    attachedNetworkDetails.domainUuid = _vpcDao.findById(vpcId).getUuid();
                } else if (networkOffering.getGuestType() == GuestType.Shared) {
                    attachedNetworkDetails.domainUuid = networksDomain.getUuid();
                } else {
                    attachedNetworkDetails.domainUuid = network.getUuid();
                }
            } else {
                attachedNetworkDetails.entityId = networkDetails.get(NuageVspConstants.NETWORK_METADATA_VSD_DOMAIN_ID);
            }
            return attachedNetworkDetails.getData();
        }

        String enterpriseId = NuageVspUtil.getEnterpriseId(networksDomain, _domainDao, nuageVspAPIParamsAsCmsUser);
        Pair<String, String> vsdDomainAndSubnetId;
        if (isL3Domain) {
            attachedNetworkDetails.entityType = NuageVspEntity.SUBNET;
            attachedNetworkDetails.isVpc = isVpc;
            if (isVpc) {
                Vpc vpcObj = _vpcDao.findById(vpcId);
                vsdDomainAndSubnetId = NuageVspApiUtil.getIsolatedSubNetwork(enterpriseId, network.getUuid(), nuageVspAPIParamsAsCmsUser, vpcObj.getUuid());
                attachedNetworkDetails.entityId = vsdDomainAndSubnetId.second();
                attachedNetworkDetails.domainUuid = vpcObj.getUuid();
            } else if (networkOffering.getGuestType() == GuestType.Shared) {
                vsdDomainAndSubnetId = NuageVspApiUtil.getIsolatedSubNetwork(enterpriseId, network.getUuid(), nuageVspAPIParamsAsCmsUser, networksDomain.getUuid());
                attachedNetworkDetails.entityId = vsdDomainAndSubnetId.second();
                attachedNetworkDetails.domainUuid = networksDomain.getUuid();
            } else {
                vsdDomainAndSubnetId = NuageVspApiUtil.getIsolatedSubNetwork(enterpriseId, network.getUuid(), nuageVspAPIParamsAsCmsUser);
                attachedNetworkDetails.entityId = vsdDomainAndSubnetId.second();
                attachedNetworkDetails.domainUuid = network.getUuid();
            }

        } else {
            attachedNetworkDetails.entityType = NuageVspEntity.L2DOMAIN;
            attachedNetworkDetails.entityId = NuageVspApiUtil.getIsolatedDomain(enterpriseId, network.getUuid(), NuageVspEntity.L2DOMAIN, nuageVspAPIParamsAsCmsUser);
            vsdDomainAndSubnetId = new Pair<String, String>(attachedNetworkDetails.entityId, null);
        }
        saveNetworkDetails(network, attachedNetworkDetails.entityType, enterpriseId, vsdDomainAndSubnetId.first(), vsdDomainAndSubnetId.second());
        return attachedNetworkDetails.getData();
    }

}