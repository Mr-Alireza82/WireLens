package io.github.mralireza82.wirelens.inspection.application.render;

/** The user's input cannot be turned into bytes. The message is written for the user. */
public final class PayloadSyntaxException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public PayloadSyntaxException(String message) {
        super(message);
    }
}
