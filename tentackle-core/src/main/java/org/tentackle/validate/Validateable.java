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

import java.util.List;

/**
 * An object that provides its own validation.
 * <p>
 * By default, all objects can be validated via {@link ValidationUtilities},
 * which is driven by annotations alone.<br>
 * In some cases, however, objects require a more fine-grained validation process,
 * controlled by the object itself. In such cases, the object can implement this interface
 * and optionally provide a custom validation method, which in turn
 * invokes {@link Validateable#validate(String, ValidationScope)}.
 * The default implementation invokes {@link ValidationUtilities#validate(Object, String, ValidationScope)}
 * but can be overridden to do something else.
 * <p>
 * Another use case of implementing this interface without overriding the validation method
 * is to hide the {@code ValidationUtilities.getInstance().validate()} call or to express
 * that this object must be validated or just to enhance readability.
 * <p>
 * Important: those objects must be validated by their own validation method and
 * not by the {@link ValidationUtilities} instance directly, since it does <em>not</em> check whether
 * an object implements {@code Validateable} or not
 * (except for {@link ValidationUtilities#validateCollection(java.util.Collection, String, ValidationScope)}).<br>
 * For an example, see {@code org.tentackle.pdo.PersistentObject#validate()}.
 *
 * @author harald
 */
public interface Validateable {

  /**
   * Validates the object.
   *
   * @param validationPath the validation path
   * @param scope the validation scope
   * @return a list of validation results, empty if okay, never null
   */
  default List<ValidationResult> validate(String validationPath, ValidationScope scope) {
    return ValidationUtilities.getInstance().validate(this, validationPath, scope);
  }

}
