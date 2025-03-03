package com.google.errorprone.annotations;

import com.google.common.annotations.GwtCompatible;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@GwtCompatible
@Retention(RUNTIME)
@Target({METHOD, TYPE})
@TypeQualifierDefault({FIELD, METHOD, PARAMETER})
@Nonnull
public @interface CanIgnoreReturnValue {}
