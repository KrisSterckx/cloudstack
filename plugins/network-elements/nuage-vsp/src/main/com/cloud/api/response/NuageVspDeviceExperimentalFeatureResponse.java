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
package com.cloud.api.response;

import com.cloud.network.NuageVspDeviceVO;
import com.cloud.serializer.Param;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

@EntityReference(value = NuageVspDeviceVO.class)
public class NuageVspDeviceExperimentalFeatureResponse extends BaseResponse {
    @Param(description = "the name of the experimental feature")
    private String name;

    @Param(description = "the description of the experimental feature")
    private String description;

    @Param(description = "the state of the experimental feature")
    private String state;

    public NuageVspDeviceExperimentalFeatureResponse(String name, String description, String state) {
        this.name = name;
        this.description = description;
        this.state = state;
        this.setObjectName("experimentalfeatures");
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getState() {
        return state;
    }
}
