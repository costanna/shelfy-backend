package com.shelfy.user.dto;

import com.shelfy.user.LanguagePreference;
import com.shelfy.user.ThemePreference;

/** Ambos campos son opcionales: se actualiza solo lo que llegue. */
public record UpdatePreferencesRequest(
        ThemePreference themePreference,
        LanguagePreference languagePreference
) {
}
