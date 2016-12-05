package net.nuage.vsp.client.exception;

/**
 * Created by maximusf on 5/20/15.
 */
public class NuageJsonParsingException extends NuageVspException {
    private static final long serialVersionUID = 1L;

    public NuageJsonParsingException(String errorMessage) {
        super(errorMessage);
    }

    public NuageJsonParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}

