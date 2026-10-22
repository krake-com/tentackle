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
 * Validates a component of the object.<br>
 * There are 2 use-cases for this annotation:
 * <ol>
 *   <li>Validate the contents of an application-specific datatype. Those types are not PDOs and as such not
 *   deeply validated. The component must implement {@link org.tentackle.validate.Validateable}.</li>
 *   <li>Explicit validation of a PDO component. By default, components in PDOs are validated after all members of the PDO are validated.
 *   This corresponds to the generated order in the source code and ordinal of the {@code Persistent}-annotation.
 *   With the {@code Validate} annotation, however, the validation order can be changed by providing a priority.
 *   An optional condition can also be used to skip the validation of the component, if the condition is not met.
 *   Notice that the generated validate()-method of the aggregate PDO will not validate the component again,
 *   if annotated with {@code @Validate}.</li>
 * </ol>
 * The component is validated by its {@link org.tentackle.validate.Validateable#validate(String, org.tentackle.validate.ValidationScope)}
 * method. Collections are validated element by element. A {@code null} component is valid.
 *
 * @author harald
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(ValidateContainer.class)
@Validation(ValidateImpl.class)
public @interface Validate {

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
   * The optional condition.
   *
   * @return the condition
   */
  String condition() default "";

}
