package com.shelfy.review.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HalfStepValidator.class)
public @interface HalfStep {

    String message() default "Debe ir en pasos de 0.5";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
