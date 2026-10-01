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

package org.tentackle.validate;

import org.tentackle.common.ServiceFactory;

import java.util.List;
import java.util.function.Supplier;


interface ValidationContextFactoryHolder {
  ValidationContextFactory INSTANCE = ServiceFactory.createService(
    ValidationContextFactory.class, DefaultValidationContextFactory.class);
}


/**
 * Factory for validation contexts.
 * <p>
 * Replaceable via &#64;{@link org.tentackle.common.Service}.
 *
 * @author harald
 */
public interface ValidationContextFactory {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static ValidationContextFactory getInstance() {
    return ValidationContextFactoryHolder.INSTANCE;
  }


  /**
   * Creates a validation context.
   *
   * @param validationPath the validation path
   * @param type the type of the object to validate, null if derive from object
   * @param object the element to validate
   * @param parentObject the parent object containing the element
   * @param effectiveScope the effective validation scope
   * @param validationResults the current list of validation results
   * @return the validation context
   */
  ValidationContext create(String validationPath,
                           Class<?> type,
                           Object object,
                           Object parentObject,
                           ValidationScope effectiveScope,
                           List<ValidationResult> validationResults);

  /**
   * Creates a validation context with a lazy object provider.<br>
   * The object to validate is retrieved only when requested by a validator.
   * Allows validators to avoid the invocation of the method to load the object
   * if the pre-condition fails. Useful for lazy list relations, for example.<br>
   * Notice that script conditions receive the object as a script variable and therefore retrieve it.
   *
   * @param validationPath the validation path
   * @param type the type of the object to validate, null if derive from object
   * @param objectSupplier the supplier providing the element to validate
   * @param parentObject the parent object containing the element
   * @param effectiveScope the effective validation scope
   * @param validationResults the current list of validation results
   * @return the validation context
   */
  ValidationContext create(String validationPath,
                           Class<?> type,
                           Supplier<?> objectSupplier,
                           Object parentObject,
                           ValidationScope effectiveScope,
                           List<ValidationResult> validationResults);

}
