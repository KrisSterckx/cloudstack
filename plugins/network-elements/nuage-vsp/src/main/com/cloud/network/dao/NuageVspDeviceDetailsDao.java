package com.cloud.network.dao;

import com.cloud.utils.db.GenericDao;
import com.cloud.network.NuageVspDeviceDetailVO;
import org.apache.cloudstack.resourcedetail.ResourceDetailsDao;

public interface NuageVspDeviceDetailsDao extends GenericDao<NuageVspDeviceDetailVO, Long>, ResourceDetailsDao<NuageVspDeviceDetailVO> {
}
