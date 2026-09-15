package com.shelfy.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
