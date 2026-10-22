/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.security.pdo;

import org.tentackle.validate.Validation;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;
import org.tentackle.validate.scope.AllScope;
import org.tentackle.validate.severity.DefaultSeverity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Security rule validator annotation.
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
   * If missing, the validator creates its own message.
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
   * Priority that determines the execution order of validations.
   *
   * @return the priority
   */
  int priority() default 0;

  /**
   * The validation scope(s) the validator should be applied to.
   *
   * @return the validation scopes.
   */
  Class<? extends ValidationScope>[] scope() default { AllScope.class };

  /**
   * The validation severity.
   *
   * @return the severity
   */
  Class<? extends ValidationSeverity> severity() default DefaultSeverity.class;

}
