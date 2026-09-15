package com.shelfy.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Idiomas soportados por la app. Se serializan en minúscula (en / ca / es). */
public enum LanguagePreference {
    EN,
    CA,
    ES;

    @JsonValue
    public String code() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static LanguagePreference fromCode(String code) {
        return valueOf(code.toUpperCase());
    }
}
