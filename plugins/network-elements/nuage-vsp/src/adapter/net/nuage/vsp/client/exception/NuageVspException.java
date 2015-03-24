package net.nuage.vsp.client.exception;

import net.nuage.vsp.client.common.RequestType;

public class NuageVspException extends Exception {
    private static final long serialVersionUID = 1L;

    private int httpErrorCode;

    private int nuageErrorCode;

    private String entityType;

    private RequestType requestType;

    private String nuageErrorDetails;

    public NuageVspException(int httpErrorCode, int nuageErrorCode, String errorMessage) {
        super(errorMessage);
        this.httpErrorCode = httpErrorCode;
        this.nuageErrorCode = nuageErrorCode;
    }

    public NuageVspException(int httpErrorCode, String errorMessage, int nuageErrorCode, String nuageErrorDetails, String entityType, RequestType requestType) {
        super(errorMessage);
        this.httpErrorCode = httpErrorCode;
        this.entityType = entityType;
        this.requestType = requestType;
        this.nuageErrorCode = nuageErrorCode;
        this.nuageErrorDetails = nuageErrorDetails;
    }

    public NuageVspException(int httpErrorCode, String errorMessage, String entityType, RequestType requestType) {
        super(errorMessage);
        this.httpErrorCode = httpErrorCode;
        this.entityType = entityType;
        this.requestType = requestType;
    }

    public NuageVspException(String errorMessage) {
        super(errorMessage);
    }

    public int getHttpErrorCode() {
        return httpErrorCode;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    public void setHttpErrorCode(int httpErrorCode) {
        this.httpErrorCode = httpErrorCode;
    }

    public int getNuageErrorCode() {
        return nuageErrorCode;
    }

    public void setNuageErrorCode(int nuageErrorCode) {
        this.nuageErrorCode = nuageErrorCode;
    }

    public String getNuageErrorDetails() {
        return nuageErrorDetails;
    }

    public void setNuageErrorDetails(String nuageErrorDetails) {
        this.nuageErrorDetails = nuageErrorDetails;
    }
}
