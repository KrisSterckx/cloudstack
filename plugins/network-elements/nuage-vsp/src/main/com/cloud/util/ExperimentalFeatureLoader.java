package com.cloud.util;

import com.cloud.network.NuageVspDeviceVO;
import com.cloud.network.dao.NuageVspDao;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.ejb.Local;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Component
@Local(value = ExperimentalFeatureLoader.class)
public class ExperimentalFeatureLoader {

    private static final String EXPERIMENTAL_FEATURES = "EXPERIMENTAL_FEATURES";

    public enum ExperimentalFeature {
        CONCURRENT_VSD_OPS("Concurrent operations towards the VSD");

        private String description;

        ExperimentalFeature(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Inject
    private NuageVspDao _nuageVspDao;

    public boolean isExperimentalFeatureEnabledForPhysicalNetwork(long physicalNetworkId, ExperimentalFeature expFeature) {
        List<NuageVspDeviceVO> nuageVspDevices = _nuageVspDao.listByPhysicalNetwork(physicalNetworkId);
        for (NuageVspDeviceVO nuageVspDevice : nuageVspDevices) {
            _nuageVspDao.loadDetails(nuageVspDevice);

            String enabledFeatures = nuageVspDevice.getDetail(EXPERIMENTAL_FEATURES);
            if (StringUtils.isNotBlank(enabledFeatures) && enabledFeatures.contains("[" + expFeature.name() + "]")) {
                return true;
            }
        }
        return false;
    }

    public Collection<ExperimentalFeature> listExperimentalFeatures(long nuageVspDeviceId, boolean enabled) {
        NuageVspDeviceVO nuageVspDevice = _nuageVspDao.findById(nuageVspDeviceId);
        _nuageVspDao.loadDetails(nuageVspDevice);

        List<ExperimentalFeature> allExpFeatures = Lists.newArrayList(ExperimentalFeature.values());
        String enabledFeaturesString = nuageVspDevice.getDetail(EXPERIMENTAL_FEATURES);
        if (StringUtils.isNotBlank(enabledFeaturesString)) {
            List<ExperimentalFeature> enabledExpFeatures = Lists.transform(Arrays.asList(enabledFeaturesString.split("]")), new Function<String, ExperimentalFeature>() {
                @Override
                public ExperimentalFeature apply(String s) {
                    return findExperimentalFeatureByName(s.substring(1));
                }
            });

            if (enabled) {
                return enabledExpFeatures;
            }
            return Collections2.filter(allExpFeatures, Predicates.not(Predicates.in(enabledExpFeatures)));

        } else if (!enabled) {
            return allExpFeatures;
        }
        return Lists.newArrayList();
    }

    public void configureExperimentalFeature(long nuageVspDeviceId, String expFeatureName, boolean load) {
        NuageVspDeviceVO nuageVspDevice = _nuageVspDao.findById(nuageVspDeviceId);
        _nuageVspDao.loadDetails(nuageVspDevice);

        try {
            findExperimentalFeatureByName(expFeatureName);
        } catch (IllegalArgumentException iae) {
            throw new CloudRuntimeException("Unable to configure unknown feature '" + expFeatureName + "'");
        }

        String enabledFeatures = nuageVspDevice.getDetail(EXPERIMENTAL_FEATURES);
        if (load) {
            if (StringUtils.isBlank(enabledFeatures)) {
                nuageVspDevice.setDetail(EXPERIMENTAL_FEATURES, "[" + expFeatureName + "]");
            } else if (!enabledFeatures.contains(expFeatureName)) {
                nuageVspDevice.setDetail(EXPERIMENTAL_FEATURES, enabledFeatures + "[" + expFeatureName + "]");
            }
        } else {
            if (StringUtils.isNotBlank(enabledFeatures)) {
                enabledFeatures = enabledFeatures.replace("[" + expFeatureName + "]", "");
                nuageVspDevice.setDetail(EXPERIMENTAL_FEATURES, enabledFeatures);
            }
        }

        _nuageVspDao.saveDetails(nuageVspDevice);
    }

    private ExperimentalFeature findExperimentalFeatureByName(String expFeatureName) {
        for (ExperimentalFeature expFeature : ExperimentalFeature.values()) {
            if (expFeature.name().equals(expFeatureName)) {
                return expFeature;
            }
        }
        throw new IllegalArgumentException("Experimental feature with name '" + expFeatureName + "' could not be found");
    }
}
