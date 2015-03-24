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

import java.util.List;

import javax.inject.Inject;

import com.cloud.api.response.NuageVspResourceResponse;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.NuageVspDeviceVO;
import com.cloud.network.dao.NuageVspDao;
import net.nuage.vsp.client.rest.NuageVspConstants;
import com.cloud.offering.NetworkOffering;
import net.nuage.vsp.client.rest.NuageVspApi;
import net.nuage.vsp.client.common.RequestType;
import net.nuage.vsp.client.common.model.NuageVspEntity;
import net.nuage.vsp.client.exception.NuageVspException;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.NetworkOfferingResponse;
import org.apache.cloudstack.api.response.PhysicalNetworkResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.utils.exception.CloudRuntimeException;

@APICommand(name = "issueNuageVspResourceRequest", responseObject = NuageVspResourceResponse.class, description="Issues a Nuage VSP REST API resource request")
public class IssueNuageVspResourceRequestCmd extends BaseCmd {
    private static final Logger s_logger = Logger.getLogger(IssueNuageVspResourceRequestCmd.class.getName());
    private static final String s_name = "nuagevspresourceresponse";

    @Inject
    protected AccountManager _accountMgr;
    @Inject
    protected DomainDao _domainDao;
    @Inject
    protected NuageVspDao _nuageConfigDao;
    @Inject
    HostDao _hostDao;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name=ApiConstants.NETWORK_OFFERING_ID, type=CommandType.UUID, entityType = NetworkOfferingResponse.class,
            required=true, description="the network offering id")
    private Long networkOfferingId;

    @Parameter(name=ApiConstants.ZONE_ID, type=CommandType.UUID, entityType = ZoneResponse.class,
            required=true, description="the Zone ID for the network")
    private Long zoneId;

    @Parameter(name=ApiConstants.PHYSICAL_NETWORK_ID, type=CommandType.UUID, entityType = PhysicalNetworkResponse.class,
            description="the ID of the physical network in to which Nuage VSP Controller is added")
    private Long physicalNetworkId;

    @Parameter(name= VspConstants.NUAGE_VSP_API_METHOD, type=CommandType.STRING, required=true, description="the Nuage VSP REST API method type")
    private String method;

    @Parameter(name= VspConstants.NUAGE_VSP_API_RESOURCE, type=CommandType.STRING, required=true, description="the resource in Nuage VSP")
    private String resource;

    @Parameter(name= VspConstants.NUAGE_VSP_API_RESOURCE_ID, type=CommandType.STRING, description="the ID of the resource in Nuage VSP")
    private String resourceId;

    @Parameter(name= VspConstants.NUAGE_VSP_API_CHILD_RESOURCE, type=CommandType.STRING, description="the child resource in Nuage VSP")
    private String childResource;

    @Parameter(name= VspConstants.NUAGE_VSP_API_RESOURCE_FILTER, type=CommandType.STRING, description="the resource filter in Nuage VSP")
    private String resourceFilter;

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public Long getNetworkOfferingId() {
        return networkOfferingId;
    }

    public Long getZoneId() {
        Long physicalNetworkId = getPhysicalNetworkId();

        if (physicalNetworkId == null && zoneId == null) {
            throw new InvalidParameterValueException("Zone id is required");
        }

        return zoneId;
    }

    public Long getPhysicalNetworkId() {
        NetworkOffering offering = _entityMgr.findById(NetworkOffering.class, networkOfferingId);
        if (offering == null) {
            throw new InvalidParameterValueException("Unable to find network offering by id " + networkOfferingId);
        }

        if (physicalNetworkId != null) {
            if (offering.getGuestType() == Network.GuestType.Shared) {
                return physicalNetworkId;
            } else {
                throw new InvalidParameterValueException("Physical network id can be specified for networks of guest ip type " + Network.GuestType.Shared + " only.");
            }
        } else {
            if (zoneId == null) {
                throw new InvalidParameterValueException("ZoneId is required as physicalNetworkId is null");
            }
            return _networkService.findPhysicalNetworkId(zoneId, offering.getTags(), offering.getTrafficType());
        }
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getChildResource() {
        return childResource;
    }

    public void setChildResource(String childResource) {
        this.childResource = childResource;
    }

    public String getResourceFilter() {
        return resourceFilter;
    }

    public void setResourceFilter(String resourceFilter) {
        this.resourceFilter = resourceFilter;
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        try {
            String currentUserName = CallContext.current().getCallingUser().getUsername();
            long accountId = CallContext.current().getCallingAccount().getAccountId();
            Account account = _accountMgr.getAccount(accountId);
            DomainVO currentDomain = _domainDao.findById(account.getDomainId());

            RequestType requestTypeEnum = RequestType.valueOf(RequestType.class, method.toUpperCase());
            if (doesAPISupportRequestType(requestTypeEnum)) {
                List<NuageVspDeviceVO> nuageVspDevices = _nuageConfigDao.listByPhysicalNetwork(getPhysicalNetworkId());
                if (nuageVspDevices != null && (!nuageVspDevices.isEmpty())) {
                    NuageVspDeviceVO config = nuageVspDevices.iterator().next();
                    HostVO nuageVspHost = _hostDao.findById(config.getHostId());
                    _hostDao.loadDetails(nuageVspHost);
                    String restRelativePath = new StringBuffer().append("https://").append(nuageVspHost.getDetail("hostname")).append(":").append(nuageVspHost.getDetail("port")).append(nuageVspHost.getDetail("apirelativepath")).toString();
                    String[] cmsUserInfo = new String[]{NuageVspConstants.CMS_USER_ENTEPRISE_NAME, nuageVspHost.getDetail("cmsuser"), org.apache.commons.codec.binary.StringUtils.newStringUtf8(Base64.decodeBase64(nuageVspHost.getDetail("cmsuserpass")))};
                    int noofRetry = Integer.parseInt(nuageVspHost.getDetail("retrycount"));
                    long retryInterval = Long.parseLong(nuageVspHost.getDetail("retryinterval"));

                    String resourceInfo = NuageVspApi.executeRestApi(requestTypeEnum, currentDomain.getName(), currentUserName, NuageVspEntity.lookup(resource), resourceId, childResource != null ? NuageVspEntity.lookup(childResource) : null, resourceFilter, restRelativePath, cmsUserInfo, noofRetry, retryInterval, false);
                    NuageVspResourceResponse response = new NuageVspResourceResponse();
                    response.setResourceInfo(StringUtils.isBlank(resourceInfo) ? "" : resourceInfo);
                    response.setObjectName("nuagevspresource");
                    response.setResponseName(getCommandName());
                    this.setResponseObject(response);
                    s_logger.info("Added Nuage VSP resource info in the response.");
                } else {
                    String errorMessage = "No Nuage VSP Controller configured on physical network " + getPhysicalNetworkId();
                    s_logger.error(errorMessage);
                    throw new NuageVspException(errorMessage);
                }
            }
        } catch (InvalidParameterValueException invalidParamExcp) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, invalidParamExcp.getMessage());
        } catch (CloudRuntimeException runtimeExcp) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, runtimeExcp.getMessage());
        } catch (Exception e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
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

    private boolean doesAPISupportRequestType(RequestType requestType) throws Exception {
        if (requestType.equals(RequestType.CREATE) || requestType.equals(RequestType.MODIFY) || requestType.equals(RequestType.MODIFYRELATED)) {
            throw new Exception("This method does not support CREATE or MODIFY operation");
        }
        return true;
    }
}