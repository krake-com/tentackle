/**
 * Tentackle - http://www.tentackle.org
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
package org.tentackle.validate.validator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.tentackle.validate.DefaultScope;
import org.tentackle.validate.DefaultSeverity;
import org.tentackle.validate.Validation;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;

/**
 * The annotated element must be false.
 * Supported types are <code>boolean</code> and <code>Boolean</code>
 * <p/>
 * <code>null</code> elements are considered valid.
 * <p>
 * This validator can also be used as an object validator with the value
 * providing a compoundvalue, usually a script that must evaluate to false.

 * @author harald
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Validation(FalseImpl.class)
@Repeatable(FalseContainer.class)
public @interface False {

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
  Class<? extends ValidationScope>[] scope() default {DefaultScope.class};

  /**
   * The application specific validation severities.
   *
   * @return the severities
   */
  Class<? extends ValidationSeverity>[] severity() default {DefaultSeverity.class};

  /**
   * The optional condition.
   *
   * @return the condition
   */
  String condition() default "";

  /**
   * Determines whether the validation process stops if this validator fails.
   *
   * @return true if stop, false if continue with other validators (default)
   */
  boolean stop() default false;

  /**
   * Replaces the annoted object with an expression.<br>
   * Used in conjunction with scripts.
   *
   * @return value the value
   */
  String value() default "";

}
