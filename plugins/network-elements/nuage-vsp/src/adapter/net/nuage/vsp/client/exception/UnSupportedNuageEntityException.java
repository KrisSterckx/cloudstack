package net.nuage.vsp.client.exception;

public class UnSupportedNuageEntityException extends Exception {
    private static final long serialVersionUID = 1L;

    public UnSupportedNuageEntityException(String errorMessage) {
        super(errorMessage);
    }
}
