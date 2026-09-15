package com.shelfy.user.dto;

import com.shelfy.user.LanguagePreference;
import com.shelfy.user.ThemePreference;

public record UserResponse(
        Long id,
        String email,
        String name,
        String alias,
        ThemePreference themePreference,
        LanguagePreference languagePreference
) {
}
