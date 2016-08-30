package net.nuage.vsp.client.common.model;

import com.cloud.utils.StringUtils;

public class NuageVspAPIParams implements Cloneable {
    String restRelativePath = null;

    String[] cmsUserInfo = null;

    int noofRetry = 0;

    long retryInterval = 0l;

    String currentUserName;

    String currentUserEnterpriseName;

    boolean isCmsUser;

    String nuageVspCmsId;

    public NuageVspAPIParams() {
    }

    public NuageVspAPIParams(String restRelativePath, int noofRetry, long retryInterval, String nuageVspCmsId) {
        this.restRelativePath = restRelativePath;
        this.noofRetry = noofRetry;
        this.retryInterval = retryInterval;
        this.nuageVspCmsId = nuageVspCmsId;
        this.isCmsUser = true;
    }

    public NuageVspAPIParams(String restRelativePath, int noofRetry, long retryInterval, String nuageVspCmsId, String currentUserName, String currentUserEnterpriseName) {
        this.restRelativePath = restRelativePath;
        this.noofRetry = noofRetry;
        this.retryInterval = retryInterval;
        this.currentUserName = currentUserName;
        this.currentUserEnterpriseName = currentUserEnterpriseName;
        this.nuageVspCmsId = nuageVspCmsId;
        this.isCmsUser = false;
    }

    public void setCurrentUser(String enterpriseName, String currentUserName) {
        assert (StringUtils.isNotBlank(enterpriseName));
        assert (StringUtils.isNotBlank(currentUserName));

        this.currentUserEnterpriseName = enterpriseName;
        this.currentUserName = currentUserName;
    }

    public void clearCurrentUser() {
        this.currentUserEnterpriseName = null;
        this.currentUserName = null;
    }

    @Override
    public NuageVspAPIParams clone() {
        try {
            return (NuageVspAPIParams) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    public boolean isCmsUser() {
        return currentUserName == null;
    }

    public String getRestRelativePath() {
        return restRelativePath;
    }

    public void setRestRelativePath(String restRelativePath) {
        this.restRelativePath = restRelativePath;
    }

    public String[] getCmsUserInfo() {
        return cmsUserInfo;
    }

    public void setCmsUserInfo(String[] cmsUserInfo) {
        this.cmsUserInfo = cmsUserInfo;
    }

    public int getNoofRetry() {
        return noofRetry;
    }

    public void setNoofRetry(int noofRetry) {
        this.noofRetry = noofRetry;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

    public String getCurrentUserName() {
        return currentUserName;
    }

    public String getCloudstackDomainName() {
        return currentUserEnterpriseName;
    }

    public String getNuageVspCmsId() {
        return nuageVspCmsId;
    }

    public void setNuageVspCmsId(String nuageVspCmsId) {
        this.nuageVspCmsId = nuageVspCmsId;
    }
}