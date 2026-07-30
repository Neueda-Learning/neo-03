package com.neobank.module.integrations.idprovider;

/**
 * An identity source did not answer. Carries the classified reason so the attempt log can say
 * which kind of not-answering it was.
 */
public class ProviderUnavailableException extends RuntimeException {

    private final transient AttemptResult result;

    public ProviderUnavailableException(AttemptResult result, String message) {
        super(message);
        this.result = result;
    }

    public ProviderUnavailableException(AttemptResult result, String message, Throwable cause) {
        super(message, cause);
        this.result = result;
    }

    public AttemptResult result() {
        return result;
    }
}
