package net.nuage.vsp.client.exception;

public class AuthenticationException extends NuageVspException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String errorMessage) {
        super(errorMessage);
    }
}
