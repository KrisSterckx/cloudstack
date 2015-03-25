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

package com.cloud.network.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import net.nuage.vsp.client.common.model.NuageVspEntity;
import net.nuage.vsp.client.rest.NuageVspApiUtil;
import net.nuage.vsp.client.rest.NuageVspConstants;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.network.ExternalNetworkDeviceManager;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.UpdateNuageVspDeviceAnswer;
import com.cloud.agent.api.UpdateNuageVspDeviceCommand;
import com.cloud.api.ApiDBUtils;
import com.cloud.api.commands.AddNuageVspDeviceCmd;
import com.cloud.api.commands.DeleteNuageVspDeviceCmd;
import com.cloud.api.commands.IssueNuageVspResourceRequestCmd;
import com.cloud.api.commands.ListNuageVspDevicesCmd;
import com.cloud.api.commands.UpdateNuageVspDeviceCmd;
import com.cloud.api.response.NuageVspDeviceResponse;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.DetailVO;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.network.Network;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.Networks;
import com.cloud.network.NuageVspDeviceVO;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.NuageVspDao;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkServiceProviderDao;
import com.cloud.network.dao.PhysicalNetworkServiceProviderVO;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.resource.NuageVspResource;
import com.cloud.network.sync.NuageVspSync;
import com.cloud.network.vpc.VpcManager;
import com.cloud.network.vpc.VpcOffering;
import com.cloud.network.vpc.VpcOffering.State;
import com.cloud.network.vpc.VpcOfferingServiceMapVO;
import com.cloud.network.vpc.VpcOfferingVO;
import com.cloud.network.vpc.dao.VpcDao;
import com.cloud.network.vpc.dao.VpcOfferingDao;
import com.cloud.network.vpc.dao.VpcOfferingServiceMapDao;
import com.cloud.network.vpc.dao.VpcServiceMapDao;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceState;
import com.cloud.resource.ServerResource;
import com.cloud.user.AccountManager;
import com.cloud.utils.StringUtils;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;

@Local(value = {NuageVspManager.class})
public class NuageVspManagerImpl extends ManagerBase implements NuageVspManager, Configurable {

    private static final Logger s_logger = Logger.getLogger(NuageVspManagerImpl.class);

    private static final int ONE_MINUTE_MULTIPLIER = 60 * 1000;

    @Inject
    ResourceManager _resourceMgr;
    @Inject
    HostDetailsDao _hostDetailsDao;
    @Inject
    HostDao _hostDao;
    @Inject
    PhysicalNetworkDao _physicalNetworkDao;
    @Inject
    PhysicalNetworkServiceProviderDao _physicalNetworkServiceProviderDao;
    @Inject
    NuageVspDao _nuageVspDao;
    @Inject
    NetworkDao _networkDao;
    @Inject
    VpcOfferingDao _vpcOffDao;
    @Inject
    VpcOfferingServiceMapDao _vpcOffSvcMapDao;
    @Inject
    VpcDao _vpcDao;
    @Inject
    VpcManager _vpcManager;
    @Inject
    NuageVspDao nuageVspDao;
    @Inject
    NuageVspSync nuageVspSync;
    @Inject
    DataCenterDao _dataCenterDao;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    NetworkModel _ntwkModel;
    @Inject
    AccountManager _accountMgr;
    @Inject
    IPAddressDao _ipAddressDao;
    @Inject
    FirewallRulesDao _firewallDao;
    @Inject
    VpcServiceMapDao _vpcSrvcDao;
    @Inject
    AgentManager _agentMgr;

    private ScheduledExecutorService scheduler;

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(AddNuageVspDeviceCmd.class);
        cmdList.add(DeleteNuageVspDeviceCmd.class);
        cmdList.add(ListNuageVspDevicesCmd.class);
        cmdList.add(IssueNuageVspResourceRequestCmd.class);
        cmdList.add(UpdateNuageVspDeviceCmd.class);
        return cmdList;
    }

    @Override
    public NuageVspDeviceVO updateNuageVspDevice(UpdateNuageVspDeviceCmd command) {

        ServerResource resource = new NuageVspResource();
        final String deviceName = Network.Provider.NuageVsp.getName();
        ExternalNetworkDeviceManager.NetworkDevice networkDevice = ExternalNetworkDeviceManager.NetworkDevice.getNetworkDevice(deviceName);
        final Long physicalNetworkId = command.getPhysicalNetworkId();
        PhysicalNetworkVO physicalNetwork = _physicalNetworkDao.findById(physicalNetworkId);
        if (physicalNetwork == null) {
            throw new InvalidParameterValueException("Could not find phyical network with ID: " + physicalNetworkId);
        }

        final PhysicalNetworkServiceProviderVO ntwkSvcProvider = _physicalNetworkServiceProviderDao.findByServiceProvider(physicalNetwork.getId(),
                networkDevice.getNetworkServiceProvder());
        if (ntwkSvcProvider == null) {
            throw new CloudRuntimeException("Network Service Provider: " + networkDevice.getNetworkServiceProvder() + " is not enabled in the physical network: "
                    + physicalNetworkId + "to add this device");
        } else if (ntwkSvcProvider.getState() == PhysicalNetworkServiceProvider.State.Shutdown) {
            throw new CloudRuntimeException("Network Service Provider: " + ntwkSvcProvider.getProviderName() + " is in shutdown state in the physical network: "
                    + physicalNetworkId + "to add this device");
        }

        HostVO nuageVspHost = null;
        NuageVspDeviceVO nuageVspDevice = null;
        List<NuageVspDeviceVO> nuageVspDevices = _nuageVspDao.listByPhysicalNetwork(physicalNetworkId);
        if (nuageVspDevices == null || nuageVspDevices.size() == 0) {
            throw new CloudRuntimeException("Nuage VSD is not configured on physical network " + physicalNetworkId);
        } else {
            nuageVspDevice = nuageVspDevices.iterator().next();
            nuageVspHost = _hostDao.findById(nuageVspDevice.getHostId());
            _hostDao.loadDetails(nuageVspHost);
        }

        Map<String, String> paramsTobeUpdated = new HashMap<String, String>();
        //params.put("hostname", cmd.getHostName());
        if (StringUtils.isNotBlank(command.getHostName()) &&
                !command.getHostName().equals(nuageVspHost.getDetails().get("hostname"))) {
            paramsTobeUpdated.put("name", "Nuage VSD - " + command.getHostName());
            paramsTobeUpdated.put("hostname", command.getHostName());
        }
        //params.put("cmsuser", cmd.getUserName());
        if (StringUtils.isNotBlank(command.getUserName()) &&
                !command.getUserName().equals(nuageVspHost.getDetails().get("cmsuser"))) {
            paramsTobeUpdated.put("cmsuser", command.getUserName());
        }
        //String cmsUserPasswordBase64 = org.apache.commons.codec.binary.StringUtils.newStringUtf8(Base64.encodeBase64(cmd.getPassword().getBytes()));
        //params.put("cmsuserpass", cmsUserPasswordBase64);
        if (StringUtils.isNotBlank(command.getPassword())) {
            String encodedNewPassword = org.apache.commons.codec.binary.StringUtils.newStringUtf8(Base64.encodeBase64(command.getPassword().getBytes()));
            if (!encodedNewPassword.equals(nuageVspHost.getDetails().get("cmsuserpass"))) {
                paramsTobeUpdated.put("cmsuserpass", org.apache.commons.codec.binary.StringUtils.newStringUtf8(Base64.encodeBase64(command.getPassword().getBytes())));
            }
        }
        //params.put("port", String.valueOf(port));
        if (command.getPort() != null &&
                command.getPort() != Integer.parseInt(nuageVspHost.getDetails().get("port"))) {
            paramsTobeUpdated.put("port", String.valueOf(command.getPort()));
        }
        //params.put("apirelativepath", "/nuage/api/" + cmd.getApiVersion());
        if (StringUtils.isNotBlank(command.getApiVersion())) {
            String apiRelativePath = "/nuage/api/" + command.getApiVersion();
            if (!apiRelativePath.equals(nuageVspHost.getDetails().get("apirelativepath"))) {
                paramsTobeUpdated.put("apirelativepath", apiRelativePath);
            }
        }
        //params.put("retrycount", String.valueOf(cmd.getApiRetryCount()));
        if (command.getApiRetryCount() != null &&
                command.getApiRetryCount() != Integer.parseInt(nuageVspHost.getDetails().get("retrycount"))) {
            paramsTobeUpdated.put("retrycount", String.valueOf(command.getApiRetryCount()));
        }
        //params.put("retryinterval", String.valueOf(cmd.getApiRetryInterval()));
        if (command.getApiRetryInterval() != null &&
                command.getApiRetryInterval() != Integer.parseInt(nuageVspHost.getDetails().get("retryinterval"))) {
            paramsTobeUpdated.put("retryinterval", String.valueOf(command.getApiRetryInterval()));
        }

        if (paramsTobeUpdated.size() > 0) {
            Map<String, String> latestParamsValue = new HashMap<String, String>();
            latestParamsValue.putAll(nuageVspHost.getDetails());
            latestParamsValue.putAll(paramsTobeUpdated);
            Map<String, Object> hostdetails = new HashMap<String, Object>();
            hostdetails.putAll(latestParamsValue);

            try {
                resource.configure("", hostdetails);
                UpdateNuageVspDeviceCommand cmd = new UpdateNuageVspDeviceCommand(latestParamsValue);
                UpdateNuageVspDeviceAnswer answer = (UpdateNuageVspDeviceAnswer)_agentMgr.easySend(nuageVspHost.getId(), cmd);
                if (answer == null || !answer.getResult()) {
                    s_logger.error("UpdateNuageVspDeviceCommand failed");
                    if ((null != answer) && (null != answer.getDetails())) {
                        throw new CloudRuntimeException(answer.getDetails());
                    }
                }
                _hostDetailsDao.persist(nuageVspDevice.getHostId(), paramsTobeUpdated);
            } catch (Exception e) {
                throw new CloudRuntimeException("Failed to update NuageVsp device information", e);
            }
        } else {
            s_logger.debug("No change in the NuageVsp device parameters. So, none of the NuageVsp device parameters are modified");
        }

        return nuageVspDevice;
    }

    @Override
    public NuageVspDeviceVO addNuageVspDevice(AddNuageVspDeviceCmd cmd) {
        ServerResource resource = new NuageVspResource();
        final String deviceName = Network.Provider.NuageVsp.getName();
        ExternalNetworkDeviceManager.NetworkDevice networkDevice = ExternalNetworkDeviceManager.NetworkDevice.getNetworkDevice(deviceName);
        final Long physicalNetworkId = cmd.getPhysicalNetworkId();
        PhysicalNetworkVO physicalNetwork = _physicalNetworkDao.findById(physicalNetworkId);
        if (physicalNetwork == null) {
            throw new InvalidParameterValueException("Could not find phyical network with ID: " + physicalNetworkId);
        }
        long zoneId = physicalNetwork.getDataCenterId();

        final PhysicalNetworkServiceProviderVO ntwkSvcProvider = _physicalNetworkServiceProviderDao.findByServiceProvider(physicalNetwork.getId(),
                networkDevice.getNetworkServiceProvder());
        if (ntwkSvcProvider == null) {
            throw new CloudRuntimeException("Network Service Provider: " + networkDevice.getNetworkServiceProvder() + " is not enabled in the physical network: "
                    + physicalNetworkId + "to add this device");
        } else if (ntwkSvcProvider.getState() == PhysicalNetworkServiceProvider.State.Shutdown) {
            throw new CloudRuntimeException("Network Service Provider: " + ntwkSvcProvider.getProviderName() + " is in shutdown state in the physical network: "
                    + physicalNetworkId + "to add this device");
        }

        if (_nuageVspDao.listByPhysicalNetwork(physicalNetworkId).size() != 0) {
            throw new CloudRuntimeException("A NuageVsp device is already configured on this physical network");
        }

        Map<String, String> params = new HashMap<String, String>();
        params.put("guid", UUID.randomUUID().toString());
        params.put("zoneId", String.valueOf(physicalNetwork.getDataCenterId()));
        params.put("physicalNetworkId", String.valueOf(physicalNetwork.getId()));
        params.put("name", "Nuage VSD - " + cmd.getHostName());
        params.put("hostname", cmd.getHostName());
        params.put("cmsuser", cmd.getUserName());
        String cmsUserPasswordBase64 = org.apache.commons.codec.binary.StringUtils.newStringUtf8(Base64.encodeBase64(cmd.getPassword().getBytes()));
        params.put("cmsuserpass", cmsUserPasswordBase64);
        int port = cmd.getPort();
        if (0 == port) {
            port = 443;
        }
        params.put("port", String.valueOf(port));
        params.put("apirelativepath", "/nuage/api/" + cmd.getApiVersion());
        params.put("retrycount", String.valueOf(cmd.getApiRetryCount()));
        params.put("retryinterval", String.valueOf(cmd.getApiRetryInterval()));

        Map<String, Object> hostdetails = new HashMap<String, Object>();
        hostdetails.putAll(params);

        try {
            resource.configure(cmd.getHostName(), hostdetails);

            final Host host = _resourceMgr.addHost(zoneId, resource, Host.Type.L2Networking, params);
            if (host != null) {
                return Transaction.execute(new TransactionCallback<NuageVspDeviceVO>() {
                    @Override
                    public NuageVspDeviceVO doInTransaction(TransactionStatus status) {
                        NuageVspDeviceVO nuageVspDevice = new NuageVspDeviceVO(host.getId(), physicalNetworkId, ntwkSvcProvider.getProviderName(), deviceName);
                        _nuageVspDao.persist(nuageVspDevice);

                        DetailVO detail = new DetailVO(host.getId(), "nuagevspdeviceid", String.valueOf(nuageVspDevice.getId()));
                        _hostDetailsDao.persist(detail);

                        return nuageVspDevice;
                    }
                });
            } else {
                throw new CloudRuntimeException("Failed to add Nuage Vsp Device due to internal error.");
            }
        } catch (ConfigurationException e) {
            throw new CloudRuntimeException(e.getMessage());
        }
    }

    @Override
    public NuageVspDeviceResponse createNuageVspDeviceResponse(NuageVspDeviceVO nuageVspDeviceVO) {
        HostVO nuageVspHost = _hostDao.findById(nuageVspDeviceVO.getHostId());
        _hostDao.loadDetails(nuageVspHost);

        NuageVspDeviceResponse response = new NuageVspDeviceResponse();
        response.setDeviceName(nuageVspDeviceVO.getDeviceName());
        PhysicalNetwork pnw = ApiDBUtils.findPhysicalNetworkById(nuageVspDeviceVO.getPhysicalNetworkId());
        if (pnw != null) {
            response.setPhysicalNetworkId(pnw.getUuid());
        }
        response.setId(nuageVspDeviceVO.getUuid());
        response.setProviderName(nuageVspDeviceVO.getProviderName());
        response.setHostName(nuageVspHost.getDetail("hostname"));
        response.setPort(Integer.parseInt(nuageVspHost.getDetail("port")));
        String apiRelativePath = nuageVspHost.getDetail("apirelativepath");
        response.setApiVersion(apiRelativePath.substring(apiRelativePath.lastIndexOf('/') + 1));
        response.setApiRetryCount(Integer.parseInt(nuageVspHost.getDetail("retrycount")));
        response.setApiRetryInterval(Long.parseLong(nuageVspHost.getDetail("retryinterval")));
        response.setObjectName("nuagevspdevice");
        return response;
    }

    @Override
    public boolean deleteNuageVspDevice(DeleteNuageVspDeviceCmd cmd) {
        Long nuageDeviceId = cmd.getNuageVspDeviceId();
        NuageVspDeviceVO nuageVspDevice = _nuageVspDao.findById(nuageDeviceId);
        if (nuageVspDevice == null) {
            throw new InvalidParameterValueException("Could not find a Nuage Vsp device with id " + nuageDeviceId);
        }

        // Find the physical network we work for
        Long physicalNetworkId = nuageVspDevice.getPhysicalNetworkId();
        PhysicalNetworkVO physicalNetwork = _physicalNetworkDao.findById(physicalNetworkId);
        if (physicalNetwork != null) {
            // Lets see if there are networks that use us
            // Find the nuage networks on this physical network
            List<NetworkVO> networkList = _networkDao.listByPhysicalNetwork(physicalNetworkId);

            // Networks with broadcast type lswitch are ours
            for (NetworkVO network : networkList) {
                if (network.getBroadcastDomainType() == Networks.BroadcastDomainType.Vsp) {
                    if ((network.getState() != Network.State.Shutdown) && (network.getState() != Network.State.Destroy)) {
                        throw new CloudRuntimeException("This Nuage Vsp device can not be deleted as there are one or more logical networks provisioned by Cloudstack.");
                    }
                }
            }
        }

        HostVO nuageHost = _hostDao.findById(nuageVspDevice.getHostId());
        Long hostId = nuageHost.getId();

        nuageHost.setResourceState(ResourceState.Maintenance);
        _hostDao.update(hostId, nuageHost);
        _resourceMgr.deleteHost(hostId, false, false);

        _nuageVspDao.remove(nuageDeviceId);
        return true;
    }

    @Override
    public List<NuageVspDeviceVO> listNuageVspDevices(ListNuageVspDevicesCmd cmd) {
        Long physicalNetworkId = cmd.getPhysicalNetworkId();
        Long nuageVspDeviceId = cmd.getNuageVspDeviceId();
        List<NuageVspDeviceVO> responseList = new ArrayList<NuageVspDeviceVO>();

        if (physicalNetworkId == null && nuageVspDeviceId == null) {
            throw new InvalidParameterValueException("Either physical network Id or Nuage device Id must be specified");
        }

        if (nuageVspDeviceId != null) {
            NuageVspDeviceVO nuageVspDevice = _nuageVspDao.findById(nuageVspDeviceId);
            if (nuageVspDevice == null) {
                throw new InvalidParameterValueException("Could not find Nuage Vsp device with id: " + nuageVspDeviceId);
            }
            responseList.add(nuageVspDevice);
        } else {
            PhysicalNetworkVO physicalNetwork = _physicalNetworkDao.findById(physicalNetworkId);
            if (physicalNetwork == null) {
                throw new InvalidParameterValueException("Could not find a physical network with id: " + physicalNetworkId);
            }
            responseList = _nuageVspDao.listByPhysicalNetwork(physicalNetworkId);
        }

        return responseList;
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        initNuageVspVpcOffering();
        initNuageScheduledTasks();
        return true;
    }

    @DB
    private void initNuageVspVpcOffering() {
        //configure default Nuage VSP vpc offering
        Transaction.execute(new TransactionCallbackNoReturn() {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status) {
                if (_vpcOffDao.findByUniqueName(nuageVPCOfferingName) == null) {
                    s_logger.debug("Creating default Nuage VPC offering " + nuageVPCOfferingName);

                    Map<Service, Set<Provider>> svcProviderMap = new HashMap<Service, Set<Provider>>();
                    Set<Provider> nuageProviders = new HashSet<Provider>();
                    nuageProviders.add(Network.Provider.NuageVsp);
                    svcProviderMap.put(Service.Connectivity, nuageProviders);
                    svcProviderMap.put(Service.Dhcp, nuageProviders);
                    svcProviderMap.put(Service.StaticNat, nuageProviders);
                    svcProviderMap.put(Service.SourceNat, nuageProviders);

                    Set<Provider> nuageVpcProviders = new HashSet<Provider>();
                    nuageVpcProviders.add(Network.Provider.NuageVspVpc);
                    svcProviderMap.put(Service.NetworkACL, nuageVpcProviders);
                    svcProviderMap.put(Service.UserData, nuageVpcProviders);
                    createVpcOffering(nuageVPCOfferingName, nuageVPCOfferingDisplayText, svcProviderMap, true, State.Enabled, null);
                }
            }
        });
    }

    @DB
    protected VpcOffering createVpcOffering(final String name, final String displayText, final Map<Network.Service, Set<Network.Provider>> svcProviderMap, final boolean isDefault,
            final State state, final Long serviceOfferingId) {
        return Transaction.execute(new TransactionCallback<VpcOffering>() {
            @Override
            public VpcOffering doInTransaction(TransactionStatus status) {
                // create vpc offering object
                VpcOfferingVO offering = new VpcOfferingVO(name, displayText, isDefault, serviceOfferingId);

                if (state != null) {
                    offering.setState(state);
                }
                s_logger.debug("Adding vpc offering " + offering);
                offering = _vpcOffDao.persist(offering);
                // populate services and providers
                if (svcProviderMap != null) {
                    for (Network.Service service : svcProviderMap.keySet()) {
                        Set<Provider> providers = svcProviderMap.get(service);
                        if (providers != null && !providers.isEmpty()) {
                            for (Network.Provider provider : providers) {
                                VpcOfferingServiceMapVO offService = new VpcOfferingServiceMapVO(offering.getId(), service, provider);
                                _vpcOffSvcMapDao.persist(offService);
                                s_logger.trace("Added service for the vpc offering: " + offService + " with provider " + provider.getName());
                            }
                        } else {
                            throw new InvalidParameterValueException("Provider is missing for the VPC offering service " + service.getName());
                        }
                    }
                }
                return offering;
            }
        });
    }

    public List<String> getDnsDetails(Network network) {
        List<String> dnsServers = null;
        Boolean configureDns = Boolean.valueOf(_configDao.getValue(NuageVspManager.NuageVspConfigDns.key()));
        if (configureDns) {
            Boolean configureExternalDns = Boolean.valueOf(_configDao.getValue(NuageVspManager.NuageVspDnsExternal.key()));
            DataCenterVO dc = _dataCenterDao.findById(network.getDataCenterId());
            dnsServers = new ArrayList<String>();
            if (configureExternalDns) {
                if (dc.getDns1() != null && dc.getDns1().length() > 0) {
                    dnsServers.add(dc.getDns1());
                }
                if (dc.getDns2() != null && dc.getDns2().length() > 0) {
                    dnsServers.add(dc.getDns2());
                }
            } else {
                if (dc.getInternalDns1() != null && dc.getInternalDns1().length() > 0) {
                    dnsServers.add(dc.getInternalDns1());
                }
                if (dc.getInternalDns2() != null && dc.getInternalDns2().length() > 0) {
                    dnsServers.add(dc.getInternalDns2());
                }
            }
        }
        return dnsServers;
    }

    private void initNuageScheduledTasks() {
        ThreadFactory threadFactory = new ThreadFactory() {
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "Nuage Vsp sync task");
                if (thread.isDaemon())
                    thread.setDaemon(false);
                if (thread.getPriority() != Thread.NORM_PRIORITY)
                    thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };

        int numOfSyncThreads = NuageVspApiUtil.getConfigProperties() == null ? NuageVspConstants.NUM_OF_PERIODIC_THREADS : NuageVspApiUtil.getConfigProperties().getInt(
                "noOfSyncThreads", NuageVspConstants.NUM_OF_PERIODIC_THREADS);
        scheduler = Executors.newScheduledThreadPool(numOfSyncThreads, threadFactory);

        int syncUpIntervalInMinutes = NuageVspApiUtil.getConfigProperties() == null ? NuageVspConstants.SYNC_UP_INTERVAL_IN_MINUTES : NuageVspApiUtil.getConfigProperties().getInt(
                "syncUpIntervalInMinutes", NuageVspConstants.SYNC_UP_INTERVAL_IN_MINUTES);

        scheduler.scheduleWithFixedDelay(new NuageVspSyncTask(NuageVspEntity.FLOATING_IP), ONE_MINUTE_MULTIPLIER * 15, ONE_MINUTE_MULTIPLIER * syncUpIntervalInMinutes,
                TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(new NuageVspSyncTask(NuageVspEntity.ENTERPRISE_NTWK_MACRO), ONE_MINUTE_MULTIPLIER * 15, ONE_MINUTE_MULTIPLIER * syncUpIntervalInMinutes,
                TimeUnit.MILLISECONDS);
    }

    public class NuageVspSyncTask implements Runnable {

        private NuageVspEntity nuageVspEntity;

        public NuageVspSyncTask(NuageVspEntity nuageVspEntity) {
            this.nuageVspEntity = nuageVspEntity;
        }

        public NuageVspEntity getNuageVspEntity() {
            return nuageVspEntity;
        }

        @Override
        public void run() {
            nuageVspSync.syncWithNuageVsp(nuageVspEntity);
        }

    }

    @Override
    public String getConfigComponentName() {
        return NuageVspManager.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {NuageVspConfigDns, NuageVspDnsExternal, NuageVspIpAccessControl};
    }
}
