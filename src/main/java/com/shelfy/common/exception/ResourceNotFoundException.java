package com.shelfy.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s no encontrado con id %s".formatted(resource, id));
    }
}
