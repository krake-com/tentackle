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

import org.tentackle.trip.TripReferencable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Default implementation of a validation context.
 * <p>
 * Notice: not a record, may be extended by the application.
 * <p>
 * If created with an object supplier, the supplier is invoked by each invocation of {@link #getObject()}.
 *
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)
public class DefaultValidationContext implements ValidationContext {

  private final String validationPath;
  private final Class<?> type;
  private final Object object;
  private final Supplier<?> objectSupplier;
  private final Object parentObject;
  private final ValidationScope effectiveScope;
  private final List<ValidationResult> validationResults;

  private Map<Object, Object> properties;   // lazily created


  /**
   * Creates a validation context.
   *
   * @param validationPath the validation path
   * @param type the type of the object to validate, null if derive from the object
   * @param object the element to validate
   * @param parentObject the parent object containing the element
   * @param effectiveScope the effective validation scope
   * @param validationResults the current list of validation results
   */
  public DefaultValidationContext(String validationPath,
                                  Class<?> type,
                                  Object object,
                                  Object parentObject,
                                  ValidationScope effectiveScope,
                                  List<ValidationResult> validationResults) {

    this.validationPath = validationPath;
    this.type = type;
    this.object = object;
    this.objectSupplier = null;
    this.parentObject = parentObject;
    this.effectiveScope = effectiveScope;
    this.validationResults = validationResults;
  }

  /**
   * Creates a validation context with a lazy object provider.<br>
   * The object to validate is retrieved only when requested by a validator.
   * Allows validators to avoid the invocation of the method to load the object
   * if the pre-condition fails. Useful for lazy list relations, for example.
   *
   * @param validationPath the validation path
   * @param type the type of the object to validate, null if derive from the object
   * @param objectSupplier the supplier providing the element to validate
   * @param parentObject the parent object containing the element
   * @param effectiveScope the effective validation scope
   * @param validationResults the current list of validation results
   */
  public DefaultValidationContext(String validationPath,
                                  Class<?> type,
                                  Supplier<?> objectSupplier,
                                  Object parentObject,
                                  ValidationScope effectiveScope,
                                  List<ValidationResult> validationResults) {

    this.validationPath = validationPath;
    this.type = type;
    this.object = null;
    this.objectSupplier = objectSupplier;
    this.parentObject = parentObject;
    this.effectiveScope = effectiveScope;
    this.validationResults = validationResults;
  }

  /**
   * For TRIP only.
   */
  protected DefaultValidationContext() {
    this.validationPath = null;
    this.type = null;
    this.object = null;
    this.objectSupplier = null;
    this.parentObject = null;
    this.effectiveScope = null;
    this.validationResults = null;
  }

  @Override
  public String getValidationPath() {
    return validationPath;
  }

  @Override
  public Object getObject() {
    return objectSupplier == null ? object : objectSupplier.get();
  }

  @Override
  public Class<?> getType() {
    return type;
  }

  @Override
  public ValidationScope getEffectiveScope() {
    return effectiveScope;
  }

  @Override
  public Object getParentObject() {
    return parentObject;
  }

  @Override
  public List<ValidationResult> getValidationResults() {
    return validationResults;
  }

  @Override
  public String toString() {
    return validationPath;
  }

  @Override
  public Object put(Object key, Object value) {
    return getProperties().put(key, value);
  }

  @Override
  public Object get(Object key) {
    return getProperties().get(key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T get(Class<T> clazz) {
    return (T) getProperties().get(clazz);
  }

  private Map<Object, Object> getProperties() {
    if (properties == null) {
      properties = new HashMap<>();
    }
    return properties;
  }
}
