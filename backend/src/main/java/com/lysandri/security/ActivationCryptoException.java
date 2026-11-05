package com.lysandri.security;

public class ActivationCryptoException extends RuntimeException {
    public ActivationCryptoException(String message) {
        super(message);
    }

    public ActivationCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
