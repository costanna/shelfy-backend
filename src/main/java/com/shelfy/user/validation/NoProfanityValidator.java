package com.shelfy.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class NoProfanityValidator implements ConstraintValidator<NoProfanity, String> {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = normalize(value);
        return ProfanityWordList.WORDS.stream().noneMatch(normalized::contains);
    }

    private String normalize(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        String withoutDiacritics = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return DIACRITICS.matcher(withoutDiacritics).replaceAll("");
    }
}
