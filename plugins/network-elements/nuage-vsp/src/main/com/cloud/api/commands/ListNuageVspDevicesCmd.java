package com.cloud.api.commands;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PhysicalNetworkResponse;
import org.apache.log4j.Logger;

import com.cloud.api.response.NuageVspDeviceResponse;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.NuageVspDeviceVO;
import com.cloud.network.manager.NuageVspManager;
import com.cloud.utils.exception.CloudRuntimeException;

@APICommand(name = "listNuageVspDevices", responseObject = NuageVspDeviceResponse.class, description = "Lists Nuage VSP devices")
public class ListNuageVspDevicesCmd extends BaseListCmd {
    private static final Logger s_logger = Logger.getLogger(ListNuageVspDevicesCmd.class.getName());
    private static final String s_name = "listnuagevspdeviceresponse";
    @Inject
    NuageVspManager _nuageVspManager;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID, type = CommandType.UUID, entityType = PhysicalNetworkResponse.class,
            description = "the Physical Network ID")
    private Long physicalNetworkId;

    @Parameter(name = VspConstants.NUAGE_VSP_DEVICE_ID, type = CommandType.UUID, entityType = NuageVspDeviceResponse.class,
            description = "the Nuage VSP device ID")
    private Long nuageVspDeviceId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getNuageVspDeviceId() {
        return nuageVspDeviceId;
    }

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        try {
            List<NuageVspDeviceVO> nuageDevices = _nuageVspManager.listNuageVspDevices(this);
            ListResponse<NuageVspDeviceResponse> response = new ListResponse<NuageVspDeviceResponse>();
            List<NuageVspDeviceResponse> nuageDevicesResponse = new ArrayList<NuageVspDeviceResponse>();

            if (nuageDevices != null && !nuageDevices.isEmpty()) {
                for (NuageVspDeviceVO nuageDeviceVO : nuageDevices) {
                    NuageVspDeviceResponse nuageDeviceResponse = _nuageVspManager.createNuageVspDeviceResponse(nuageDeviceVO);
                    nuageDevicesResponse.add(nuageDeviceResponse);
                }
            }

            response.setResponses(nuageDevicesResponse);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } catch (InvalidParameterValueException invalidParamExcp) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, invalidParamExcp.getMessage());
        } catch (CloudRuntimeException runtimeExcp) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, runtimeExcp.getMessage());
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

}

