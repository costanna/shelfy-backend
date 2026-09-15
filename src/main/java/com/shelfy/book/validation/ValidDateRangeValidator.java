package com.shelfy.book.validation;

import com.shelfy.book.dto.BookRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, BookRequest> {

    @Override
    public boolean isValid(BookRequest request, ConstraintValidatorContext context) {
        if (request == null || request.startedAt() == null || request.finishedAt() == null) {
            return true;
        }
        if (!request.finishedAt().isBefore(request.startedAt())) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("finishedAt")
                .addConstraintViolation();
        return false;
    }
}
