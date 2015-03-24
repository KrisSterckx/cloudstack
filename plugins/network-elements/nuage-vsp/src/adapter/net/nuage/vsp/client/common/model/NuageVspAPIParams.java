package net.nuage.vsp.client.common.model;


public class NuageVspAPIParams {

    String restRelativePath = null;

    String[] cmsUserInfo = null;

    int noofRetry = 0;

    long retryInterval = 0l;

    String currentUserName;

    String cloudstackDomainName;

    boolean isCmsUser;

    public boolean isCmsUser() {
        return isCmsUser;
    }

    public void setCmsUser(boolean isCmsUser) {
        this.isCmsUser = isCmsUser;
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

    public void setCurrentUserName(String currentUserName) {
        this.currentUserName = currentUserName;
    }

    public String getCloudstackDomainName() {
        return cloudstackDomainName;
    }

    public void setCurrentUserEnterpriseName(String cloudstackDomainName) {
        this.cloudstackDomainName = cloudstackDomainName;
    }
}