package com.cloud.api.commands;

import com.cloud.api.response.NuageVspDeviceExperimentalFeatureResponse;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.util.ExperimentalFeatureLoader;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.cloud.util.ExperimentalFeatureLoader.ExperimentalFeature;

@APICommand(name = "listNuageVspDeviceExperimentalFeatures", responseObject = BaseResponse.class, description = "Lists Nuage VSP device Experimental Features")
public class ListNuageVspDeviceExperimentalFeaturesCmd extends BaseListCmd {
    private static final Logger s_logger = Logger.getLogger(ListNuageVspDeviceExperimentalFeaturesCmd.class.getName());
    private static final String s_name = "listnuagevspdeviceexperimentalfeaturesresponse";

    @Inject
    ExperimentalFeatureLoader _experimentalFeatureLoader;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = VspConstants.NUAGE_VSP_DEVICE_ID, required = true, type = CommandType.UUID, entityType = NuageVspDeviceExperimentalFeatureResponse.class,
            description = "the Nuage VSP device ID")
    private Long nuageVspDeviceId;

    @Parameter(name = ApiConstants.ENABLED, required = false, type = CommandType.BOOLEAN, entityType = NuageVspDeviceExperimentalFeatureResponse.class,
            description = "true to show enabled experimental features")
    private Boolean enabled;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getNuageVspDeviceId() {
        return nuageVspDeviceId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        try {
            List<NuageVspDeviceExperimentalFeatureResponse> responses = new ArrayList<NuageVspDeviceExperimentalFeatureResponse>();
            Collection<ExperimentalFeature> expFeatures;
            if (enabled == null) {
                expFeatures = _experimentalFeatureLoader.listExperimentalFeatures(getNuageVspDeviceId(), true);
                for (ExperimentalFeature expFeature : expFeatures) {
                    responses.add(new NuageVspDeviceExperimentalFeatureResponse(expFeature.name(), expFeature.getDescription(), "ENABLED"));
                }

                expFeatures = _experimentalFeatureLoader.listExperimentalFeatures(getNuageVspDeviceId(), false);
                for (ExperimentalFeature expFeature : expFeatures) {
                    responses.add(new NuageVspDeviceExperimentalFeatureResponse(expFeature.name(), expFeature.getDescription(), "DISABLED"));
                }
            } else {
                expFeatures = _experimentalFeatureLoader.listExperimentalFeatures(getNuageVspDeviceId(), enabled);
                for (ExperimentalFeature expFeature : expFeatures) {
                    responses.add(new NuageVspDeviceExperimentalFeatureResponse(expFeature.name(), expFeature.getDescription(), enabled ? "ENABLED" : "DISABLED"));
                }
            }

            ListResponse<NuageVspDeviceExperimentalFeatureResponse> response = new ListResponse<NuageVspDeviceExperimentalFeatureResponse>();
            response.setResponses(responses);
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

