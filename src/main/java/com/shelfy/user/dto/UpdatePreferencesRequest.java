package com.shelfy.user.dto;

import com.shelfy.user.LanguagePreference;
import com.shelfy.user.ThemePreference;

public record UpdatePreferencesRequest(
        ThemePreference themePreference,
        LanguagePreference languagePreference
) {
}
