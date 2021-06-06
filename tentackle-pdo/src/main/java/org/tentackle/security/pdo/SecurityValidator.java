/*
 * Copyright (C) 2015 by:
 *
 * Hydro Aluminium Rolled Products GmbH
 * Koblenzer Strasse 122
 * D-41468 Neuss
 * Germany
 * Tel. +49 2131 382 0
 * http://www.hydro.com/Deutschland
 *
 * All Rights Reserved.
 */

package org.tentackle.security.pdo;

import org.tentackle.validate.DefaultSeverity;
import org.tentackle.validate.Validation;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;
import org.tentackle.validate.scope.AllScope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ingot validator annotation.
 *
 * @author harald
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Validation(SecurityValidatorImpl.class)
public @interface SecurityValidator {

  /**
   * An optional validation message.<br>
   * If missing the validator creates its own message.
   *
   * @return the validation message
   */
  String message() default "";

  /**
   * An optional error code.<br>
   * Used to identify the error.
   *
   * @return the error code
   */
  String error() default "";

  /**
   * Priority that determines the execution order of validations.<br>
   * Starts with 0.
   *
   * @return the priority
   */
  int priority() default 0;

  /**
   * The validation scope(s) the validator should be applied to.
   *
   * @return the validation scopes.
   */
	Class<? extends ValidationScope>[] scope() default {AllScope.class };

  /**
   * The application specific validation severities.
   *
   * @return the severities
   */
  Class<? extends ValidationSeverity>[] severity() default {DefaultSeverity.class };

}
