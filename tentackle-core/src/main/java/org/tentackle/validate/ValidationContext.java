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
 * The validation context.
 * <p>
 * Passed to the validators and provides the element to validate, its parent object, the effective scope
 * and the results collected so far. The {@code VAR_...} constants are the names of the variables
 * provided to scripts.
 *
 * @author harald
 */
public interface ValidationContext {

  /** script variable for the validation context itself (to access properties via put/get). */
  String VAR_CONTEXT = "context";

  /** script variable for effective scope. */
  String VAR_SCOPE = "scope";

  /** script variable for validation path. */
  String VAR_PATH = "path";

  /** script variable for the parent object. */
  String VAR_OBJECT = "object";

  /** script variable for the value. */
  String VAR_VALUE = "value";

  /** script variable for the parent class. */
  String VAR_CLASS = "clazz";


  /**
   * Gets the validation path.<br>
   * Applications may use it to associate the validation result with a component.
   *
   * @return the validation path
   */
  String getValidationPath();

  /**
   * Gets the element to validate.<br>
   * This is usually annotated with a validator annotation.
   *
   * @return the element to validate
   */
  Object getObject();

  /**
   * Gets the type of the object.
   *
   * @return the type, null if derive from object
   */
  Class<?> getType();

  /**
   * Gets the effective scope.
   *
   * @return the effective scope
   */
  ValidationScope getEffectiveScope();

  /**
   * Gets the parent object.
   *
   * @return the object containing the element to validate
   */
  Object getParentObject();

  /**
   * Gets the list of validation results collected for the current object so far.
   *
   * @return the results, may be null if n/a
   */
  List<ValidationResult> getValidationResults();

  /**
   * Sets an application-specific validation property.<br>
   * May be used to pass information between the field validators of the same field
   * or object validators of the same object. Notice that the {@link ValidationResult}
   * holds a reference to the {@code ValidationContext} as well.
   *
   * @param key the key to locate the property
   * @param value the property value
   * @return null if property added, else the replaced property
   */
  Object put(Object key, Object value);

  /**
   * Gets an application-specific validation property.
   *
   * @param key the key to locate the property
   * @return the property value, null if no such key
   */
  Object get(Object key);

  /**
   * Gets a validation property stored with its class as the key.<br>
   * Same as {@code (T) get((Object) clazz)}.
   *
   * @param <T> the property type
   * @param clazz the property class
   * @return the property value, null if no such property
   */
  <T> T get(Class<T> clazz);

}
