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
package com.cloud.vm.dao;

import javax.ejb.Local;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import org.apache.cloudstack.resourcedetail.ResourceDetailsDaoBase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.UserVmDetailVO;

@Component
@Local(value = UserVmDetailsDao.class)
public class UserVmDetailsDaoImpl extends ResourceDetailsDaoBase<UserVmDetailVO> implements UserVmDetailsDao {
    public static final String DHCP_OPTION_PREFIX = "dhcp:";
    private final SearchBuilder<UserVmDetailVO> dhcpOptionSearch;

    public UserVmDetailsDaoImpl() {
        dhcpOptionSearch = createSearchBuilder();
        dhcpOptionSearch.and("resourceId", dhcpOptionSearch.entity().getResourceId(), SearchCriteria.Op.EQ);
        dhcpOptionSearch.and(dhcpOptionSearch.entity().getName(), SearchCriteria.Op.LIKE).values("dhcp:%");
        dhcpOptionSearch.done();
    }

    @Override
    public void addDetail(long resourceId, String key, String value, boolean display) {
        super.addDetail(new UserVmDetailVO(resourceId, key, value, display));
    }

    public Map<Integer, String> listDhcpOptions(Long resourceId) {
        Map<Integer, String> dhcpOptions = new HashMap<>();
        SearchCriteria<UserVmDetailVO> sc = dhcpOptionSearch.create();
        sc.setParameters("resourceId", resourceId);
        List<UserVmDetailVO> results = search(sc, null);

        for (UserVmDetailVO vmDetail : results) {
            if (vmDetail.getName().startsWith(DHCP_OPTION_PREFIX)) {

                String optionValue = StringUtils.removeStart(vmDetail.getName(), DHCP_OPTION_PREFIX);
                try {
                    Integer option = Integer.parseInt(optionValue);
                    dhcpOptions.put(option, vmDetail.getValue());
                } catch (NumberFormatException ignored){}
            }
        }

        return dhcpOptions;
    }

}
