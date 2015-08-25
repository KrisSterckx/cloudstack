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
package com.cloud.api.commands;

import com.cloud.api.response.NuageVspDeviceExperimentalFeatureResponse;
import com.cloud.api.response.NuageVspDeviceResponse;
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
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@APICommand(name = "configureNuageVspDeviceExperimentalFeature", responseObject = ListResponse.class, description = "Update a Nuage VSP device")
public class ConfigureNuageVspDeviceExperimentalFeatureCmd extends BaseCmd {
    private static final Logger s_logger = Logger.getLogger(ConfigureNuageVspDeviceExperimentalFeatureCmd.class.getName());
    private static final String s_name = "configurenuagevspdeviceexperimentalfeaturesresponse";

    @Inject
    ExperimentalFeatureLoader _experimentalFeatureLoader;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = VspConstants.NUAGE_VSP_DEVICE_ID, required = true, type = CommandType.UUID, entityType = NuageVspDeviceExperimentalFeatureResponse.class,
            description = "the Nuage VSP device ID")
    private Long nuageVspDeviceId;

    @Parameter(name = VspConstants.NUAGE_VSP_API_FEATURE, required = true, type = CommandType.STRING, entityType = NuageVspDeviceExperimentalFeatureResponse.class,
            description = "the feature to be enabled / disabled")
    private String featureName;

    @Parameter(name = ApiConstants.ENABLED, required = false, type = CommandType.BOOLEAN, entityType = NuageVspDeviceResponse.class,
            description = "used to enable / disable the feature")
    private Boolean enabled = true;


    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getNuageVspDeviceId() {
        return nuageVspDeviceId;
    }

    public String getFeatureName() {
        return featureName;
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
            _experimentalFeatureLoader.configureExperimentalFeature(getNuageVspDeviceId(), getFeatureName(), getEnabled());

            List<NuageVspDeviceExperimentalFeatureResponse> responses = new ArrayList<NuageVspDeviceExperimentalFeatureResponse>();
            Collection<ExperimentalFeatureLoader.ExperimentalFeature> expFeatures = _experimentalFeatureLoader.listExperimentalFeatures(getNuageVspDeviceId(), true);
            for (ExperimentalFeatureLoader.ExperimentalFeature expFeature : expFeatures) {
                responses.add(new NuageVspDeviceExperimentalFeatureResponse(expFeature.name(), expFeature.getDescription(), "ENABLED"));
            }

            expFeatures = _experimentalFeatureLoader.listExperimentalFeatures(getNuageVspDeviceId(), false);
            for (ExperimentalFeatureLoader.ExperimentalFeature expFeature : expFeatures) {
                responses.add(new NuageVspDeviceExperimentalFeatureResponse(expFeature.name(), expFeature.getDescription(), "DISABLED"));
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

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }
}