package com.cloud.network.resource;

import java.util.Map;

import javax.naming.ConfigurationException;

import net.nuage.vsp.client.common.model.NuageVspAPIParams;
import net.nuage.vsp.client.exception.NuageVspAPIUtilException;
import net.nuage.vsp.client.rest.NuageVspApi;
import net.nuage.vsp.client.rest.NuageVspApiUtil;
import net.nuage.vsp.client.rest.NuageVspApiVersion;
import net.nuage.vsp.client.rest.NuageVspConstants;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.cloud.agent.IAgentControl;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.MaintainAnswer;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.PingNuageVspCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupNuageVspCommand;
import com.cloud.agent.api.SyncNuageVspCmsIdAnswer;
import com.cloud.agent.api.SyncNuageVspCmsIdCommand;
import com.cloud.agent.api.UpdateNuageVspDeviceAnswer;
import com.cloud.agent.api.UpdateNuageVspDeviceCommand;
import com.cloud.host.Host;
import com.cloud.resource.ServerResource;
import com.cloud.util.NuageVspUtil;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.crypt.DBEncryptionUtil;
import com.cloud.utils.exception.CloudRuntimeException;

import static com.cloud.agent.api.SyncNuageVspCmsIdCommand.SyncType.AUDIT_WITH_CORRECTION;

public class NuageVspResource extends ManagerBase implements ServerResource {
    private static final Logger s_logger = Logger.getLogger(NuageVspResource.class);

    private String _name;
    private String _guid;
    private String _zoneId;
    private String[] _cmsUserInfo;
    private String _relativePath;
    private int _numRetries;
    private int _retryInterval;
    private boolean _shouldAudit = true;

    private NuageVspApiVersion version;
    private NuageVspAPIParams nuageVspHostParams;

    public NuageVspAPIParams validate(Map<String, Object> params) throws ConfigurationException {

        _name = (String)params.get("name");
        if (_name == null) {
            throw new ConfigurationException("Unable to find name");
        }

        _guid = (String)params.get("guid");
        if (_guid == null) {
            throw new ConfigurationException("Unable to find the guid");
        }

        _zoneId = (String)params.get("zoneId");
        if (_zoneId == null) {
            throw new ConfigurationException("Unable to find zone");
        }

        String hostname = (String)params.get("hostname");
        if (hostname == null) {
            throw new ConfigurationException("Unable to find hostname");
        }

        String cmsUser = (String)params.get("cmsuser");
        if (cmsUser == null) {
            throw new ConfigurationException("Unable to find CMS username");
        }

        String cmsUserPass = (String)params.get("cmsuserpass");
        if (cmsUserPass == null) {
            throw new ConfigurationException("Unable to find CMS password");
        }

        String cmsId = (String)params.get("cmsId");
        String port = (String)params.get("port");
        if (port == null) {
            throw new ConfigurationException("Unable to find port");
        }

        String apiVersion = (String)params.get("apiversion");
        String apiRelativePath = (String)params.get("apirelativepath");

        if (StringUtils.isBlank(apiVersion) && StringUtils.isNotBlank(apiRelativePath)) {
            apiVersion = apiRelativePath.substring(apiRelativePath.lastIndexOf('/') + 1);
        }

        if (StringUtils.isBlank(apiVersion)) {
            throw new ConfigurationException("Unable to find API version");
        }

        try {
            version = new NuageVspApiVersion(apiVersion);
        } catch(IllegalArgumentException e) {
            throw new ConfigurationException("Incorrect API version");
        }

        if (!NuageVspApiUtil.isSupportedApiVersion(version)) {
            s_logger.warn(String.format("[UPGRADE] API version %s of Nuage Vsp Device %s should be updated.", apiVersion, hostname));
        }

        int _numRetries;
        int _retryInterval;

        String retryCount = (String)params.get("retrycount");
        if ((retryCount != null) && (!retryCount.isEmpty())) {
            try {
                _numRetries = Integer.parseInt(retryCount);
            } catch (NumberFormatException ex) {
                throw new ConfigurationException("Number of retries has to be between 4 and 10");
            }
            if ((_numRetries < 4) || (_numRetries > 10)) {
                throw new ConfigurationException("Number of retries has to be between 4 and 10");
            }
        } else {
            throw new ConfigurationException("Unable to find number of retries");
        }

        String retryInterval = (String)params.get("retryinterval");
        if ((retryInterval != null) && (!retryInterval.isEmpty())) {
            try {
                _retryInterval = Integer.parseInt(retryInterval);
            } catch (NumberFormatException ex) {
                throw new ConfigurationException("Retry interval has to be between 0 and 10000 ms");
            }
            if ((_retryInterval < 0) || (_retryInterval > 10000)) {
                throw new ConfigurationException("Retry interval has to be between 0 and 10000 ms");
            }
        } else {
            throw new ConfigurationException("Unable to find retry interval");
        }

        _relativePath = new StringBuffer().append("https://").append(hostname).append(":").append(port).append(apiRelativePath).toString();

        _cmsUserInfo = new String[] {NuageVspConstants.CMS_USER_ENTEPRISE_NAME, cmsUser, DBEncryptionUtil.decrypt(cmsUserPass)};

        try {
            NuageVspApi.createHttpClient("https", Integer.valueOf(port));
            NuageVspApi.login(_relativePath, _cmsUserInfo, true);
            s_logger.info("Finished initializing http client by NuageVsp plugin....");
        } catch (Exception e) {
            s_logger.error("Failed to login to Nuage VSD on " + hostname + " as user " + cmsUser + " Exception " + e.getMessage());
            throw new CloudRuntimeException("Failed to login to Nuage VSD on " + hostname + " as user " + cmsUser);
        }

        return nuageVspHostParams;
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        nuageVspHostParams = validate(params);
        return super.configure(name, params);
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public Host.Type getType() {
        return Host.Type.L2Networking;
    }

    @Override
    public StartupCommand[] initialize() {
        StartupNuageVspCommand sc = new StartupNuageVspCommand();
        sc.setGuid(_guid);
        sc.setName(_name);
        sc.setDataCenter(_zoneId);
        sc.setPod("");
        sc.setPrivateIpAddress("");
        sc.setStorageIpAddress("");
        sc.setVersion(NuageVspResource.class.getPackage().getImplementationVersion());
        return new StartupCommand[] {sc};
    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        if ((_relativePath == null) || (_relativePath.isEmpty()) || (_cmsUserInfo == null) || (_cmsUserInfo.length == 0)) {
            s_logger.error("Failed to ping to Nuage VSD");
            _shouldAudit = true;
            return null;
        }
        try {
            NuageVspApi.login(_relativePath, _cmsUserInfo, true);
        } catch (Exception e) {
            s_logger.error("Failed to ping to Nuage VSD on " + _name + " as user " + _cmsUserInfo[1] + " Exception " + e.getMessage());
            _shouldAudit = true;
            return null;
        }
        PingNuageVspCommand pingNuageVspCommand = new PingNuageVspCommand(Host.Type.L2Networking, id, _shouldAudit);
        _shouldAudit = false;
        return pingNuageVspCommand;
    }

    @Override
    public Answer executeRequest(Command cmd) {
        return executeRequest(cmd, _numRetries);
    }

    @Override
    public void disconnected() {
    }

    @Override
    public IAgentControl getAgentControl() {
        return null;
    }

    @Override
    public void setAgentControl(IAgentControl agentControl) {
    }

    private Answer executeRequest(Command cmd, int numRetries) {
        if (cmd instanceof ReadyCommand) {
            return executeRequest((ReadyCommand)cmd);
        } else if (cmd instanceof MaintainCommand) {
            return executeRequest((MaintainCommand)cmd);
        } else if (cmd instanceof UpdateNuageVspDeviceCommand) {
            return executeRequest((UpdateNuageVspDeviceCommand)cmd);
        } else if (cmd instanceof SyncNuageVspCmsIdCommand) {
            return executeRequest((SyncNuageVspCmsIdCommand)cmd);
        }
        s_logger.debug("Received unsupported command " + cmd.toString());
        return Answer.createUnsupportedCommandAnswer(cmd);
    }

    private Answer executeRequest(ReadyCommand cmd) {
        return new ReadyAnswer(cmd);
    }

    private Answer executeRequest(MaintainCommand cmd) {
        return new MaintainAnswer(cmd);
    }

    private Answer executeRequest(UpdateNuageVspDeviceCommand cmd) {

        try {
            _numRetries = Integer.parseInt(cmd.getParametersToBeUpdated().get("retrycount"));
            _retryInterval = Integer.parseInt(cmd.getParametersToBeUpdated().get("retryinterval"));
            _relativePath = new StringBuffer().append("https://").append(cmd.getParametersToBeUpdated().get("hostname")).append(":")
                    .append(cmd.getParametersToBeUpdated().get("port")).append(cmd.getParametersToBeUpdated().get("apirelativepath")).toString();

            String cmsUserPass = DBEncryptionUtil.decrypt(cmd.getParametersToBeUpdated().get("cmsuserpass"));
            _cmsUserInfo = new String[] {NuageVspConstants.CMS_USER_ENTEPRISE_NAME, cmd.getParametersToBeUpdated().get("cmsuser"), cmsUserPass};
            return new UpdateNuageVspDeviceAnswer(cmd, true, "Updated the parameters in the NuageVsp resource");
        } catch (Exception e) {
            return new UpdateNuageVspDeviceAnswer(cmd, e);
        }
    }

    private Answer executeRequest(SyncNuageVspCmsIdCommand cmd) {
        switch (cmd.getSyncType()) {
            case REGISTER:
                return registerNuageVspCmsId(cmd);
            case UNREGISTER:
                return unregisterNuageVspCmsId(cmd);
            default:
                return auditNuageVspCmsId(cmd);
        }
    }

    private SyncNuageVspCmsIdAnswer auditNuageVspCmsId(SyncNuageVspCmsIdCommand cmd) {
        String nuageVspDeviceId = String.valueOf(cmd.getNuageVspDeviceId());
        String currentConfigValue = cmd.getCurrentCmsIdConfigValue();
        boolean configContainsNuageVspDevice = StringUtils.isNotBlank(currentConfigValue) &&
                (currentConfigValue.startsWith(nuageVspDeviceId + ":") || currentConfigValue.contains(";" + nuageVspDeviceId + ":"));

        if (configContainsNuageVspDevice) {
            //Check if the CMS ID is known by the Nuage VSP
            s_logger.debug("Auditing VSD CMS configuration for Nuage VSP device with ID " + cmd.getNuageVspDeviceId());
            try {
                String nuageVspCmsId = NuageVspUtil.findNuageVspDeviceCmsId(cmd.getNuageVspDeviceId(), currentConfigValue);
                NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(cmd.getHostDetails(), null);
                boolean knownCmsId = NuageVspApiUtil.isKnownCmsIdForNuageVsp(nuageVspCmsId, nuageVspAPIParamsAsCmsUser);
                String registeredNuageVspDevice = getRegisteredNuageVspDevice(cmd.getNuageVspDeviceId(), nuageVspCmsId);

                if (!knownCmsId && cmd.getSyncType() == SyncNuageVspCmsIdCommand.SyncType.AUDIT_WITH_CORRECTION) {
                    nuageVspCmsId = NuageVspApiUtil.registerCmsIdForNuageVsp(nuageVspCmsId, "CloudStack", nuageVspAPIParamsAsCmsUser);
                    nuageVspAPIParamsAsCmsUser.setNuageVspCmsId(nuageVspCmsId);
                    return new SyncNuageVspCmsIdAnswer(true, registeredNuageVspDevice, AUDIT_WITH_CORRECTION);
                }
                return new SyncNuageVspCmsIdAnswer(true, registeredNuageVspDevice, SyncNuageVspCmsIdCommand.SyncType.AUDIT);
            } catch (NuageVspAPIUtilException e) {
                s_logger.error("Failed to audit VSD CMS ID", e);
                return new SyncNuageVspCmsIdAnswer(false, null, SyncNuageVspCmsIdCommand.SyncType.AUDIT);
            }
        } else if (cmd.getSyncType() != SyncNuageVspCmsIdCommand.SyncType.AUDIT_ONLY) {
            return registerNuageVspCmsId(cmd);
        }
        return new SyncNuageVspCmsIdAnswer(false, null, SyncNuageVspCmsIdCommand.SyncType.AUDIT);
    }

    private SyncNuageVspCmsIdAnswer registerNuageVspCmsId(SyncNuageVspCmsIdCommand cmd) {
        s_logger.debug("Creating VSD CMS configuration for Nuage VSP device with ID " + cmd.getNuageVspDeviceId());
        try {
            NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(cmd.getHostDetails(), null);
            String cmsId = NuageVspApiUtil.generateCmsIdForNuageVsp("CloudStack", nuageVspAPIParamsAsCmsUser);
            String registeredNuageVspDevice = getRegisteredNuageVspDevice(cmd.getNuageVspDeviceId(), cmsId);
            return new SyncNuageVspCmsIdAnswer(true, registeredNuageVspDevice, SyncNuageVspCmsIdCommand.SyncType.REGISTER);
        } catch (NuageVspAPIUtilException e) {
            s_logger.error("Failed to register VSD CMS ID", e);
            return new SyncNuageVspCmsIdAnswer(false, null, SyncNuageVspCmsIdCommand.SyncType.REGISTER);
        }
    }

    private SyncNuageVspCmsIdAnswer unregisterNuageVspCmsId(SyncNuageVspCmsIdCommand cmd) {
        String nuageVspDeviceId = String.valueOf(cmd.getNuageVspDeviceId());
        String currentConfigValue = cmd.getCurrentCmsIdConfigValue();
        String[] configuredNuageVspDevices = StringUtils.isNotBlank(currentConfigValue) ? currentConfigValue.split(";") : new String[] {};

        for (String configuredNuageVspDevice : configuredNuageVspDevices) {
            if (configuredNuageVspDevice.startsWith(nuageVspDeviceId + ":")) {
                s_logger.debug("Removing VSD CMS configuration for Nuage VSP device with ID " + cmd.getNuageVspDeviceId());
                try {
                    NuageVspAPIParams nuageVspAPIParamsAsCmsUser = NuageVspApiUtil.getNuageVspAPIParametersAsCmsUser(cmd.getHostDetails(), null);
                    boolean removed = NuageVspApiUtil.removeCmsIdForNuageVsp(configuredNuageVspDevice.split(":")[1], nuageVspAPIParamsAsCmsUser);
                    return new SyncNuageVspCmsIdAnswer(removed, configuredNuageVspDevice, SyncNuageVspCmsIdCommand.SyncType.UNREGISTER);
                } catch (NuageVspAPIUtilException e) {
                    s_logger.error("Failed to unregister VSD CMS ID", e);
                    return new SyncNuageVspCmsIdAnswer(false, null, SyncNuageVspCmsIdCommand.SyncType.UNREGISTER);
                }
            }
        }
        return new SyncNuageVspCmsIdAnswer(false, NuageVspUtil.findNuageVspDeviceCmsId(cmd.getNuageVspDeviceId(), currentConfigValue), SyncNuageVspCmsIdCommand.SyncType.UNREGISTER);
    }

    private String getRegisteredNuageVspDevice(long nuageVspDeviceId, String cmsId) {
        return nuageVspDeviceId + ":" + cmsId;
    }
}
