package com.shelfy.user.dto;

import com.shelfy.user.LanguagePreference;
import com.shelfy.user.ThemePreference;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdatePreferencesRequest(
        ThemePreference themePreference,
        LanguagePreference languagePreference,
        Boolean remindersEnabled,
        @Min(value = 0, message = "La hora debe estar entre 0 y 23")
        @Max(value = 23, message = "La hora debe estar entre 0 y 23")
        Integer reminderHour
) {
}
