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
package org.tentackle.validate.validator;

import org.tentackle.validate.DefaultScope;
import org.tentackle.validate.Validation;
import org.tentackle.validate.ValidationScope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The validation results must contain at least one failed result.<br>
 * Used to abort the validation with a {@link org.tentackle.validate.ValidationFailedException}.
 * <p>
 * This validator can also be used before or after an object validator.
 *
 * @author harald
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Validation(FailImpl.class)
@Repeatable(FailContainer.class)
public @interface Fail {

  /**
   * Type of validation results collected so far.
   */
  enum Results {

    /**
     * Don't consult the validation results at all.<br>
     * Usually makes only sense in conjunction with a condition.
     */
    IGNORED,

    /**
     * Fail if at least one result has been collected, no matter whether it
     * is marked as failed or just a warning.
     */
    ANY,

    /**
     * Fail only if at least one result has been collected, that is marked as failed.<br>
     * This is the default.
     */
    FAILED
  }

  /**
   * Priority that determines the execution order of validations.<br>
   * Higher values will be invoked first.
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
   * The optional condition.<br>
   * Used in conjunction with {@link #value()}.
   *
   * @return the condition
   */
  String condition() default "";

  /**
   * Controls how the validation results are checked.
   *
   * @return the check type, default is {@link Results#FAILED}
   */
  Results value() default Results.FAILED;

}
