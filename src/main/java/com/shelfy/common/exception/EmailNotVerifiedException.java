package com.shelfy.common.exception;

public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Verifica tu email antes de iniciar sesión. Revisa tu bandeja de entrada.");
    }
}
