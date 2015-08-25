package com.cloud.network.dao;

import com.cloud.network.NuageVspDeviceDetailVO;
import org.apache.cloudstack.resourcedetail.ResourceDetailsDaoBase;
import org.springframework.stereotype.Component;

import javax.ejb.Local;

@Component
@Local(value = NuageVspDeviceDetailsDao.class)
public class NuageVspDeviceDetailsDaoImpl extends ResourceDetailsDaoBase<NuageVspDeviceDetailVO> implements NuageVspDeviceDetailsDao {

    @Override
    public void addDetail(long resourceId, String key, String value, boolean display) {
        NuageVspDeviceDetailVO detail = findDetail(resourceId, key);
        if (detail != null) {
            detail.setValue(value);
            detail.setDisplay(display);
        } else {
            detail = new NuageVspDeviceDetailVO(resourceId, key, value, display);
        }
        persist(detail);
    }

}
