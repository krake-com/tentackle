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


package org.tentackle.validate;

/**
 * The validation context.<br>
 * No logic in here, just data.
 * The VC itself is immutable.
 *
 * @author harald
 */
public class ValidationContext {

  private final String validationPath;
  private final Class<?> type;
  private final Object object;
  private final Object parentObject;
  private final ValidationScope effectiveScope;


  /**
   * Creates a validation context.
   *
   * @param validationPath the validation path
   * @param type the type of the object to validate
   * @param object the element to validate
   * @param parentObject the object containing the element
   * @param effectiveScope the effective validation scope
   */
  public ValidationContext(String validationPath,
                           Class<?> type,
                           Object object,
                           Object parentObject,
                           ValidationScope effectiveScope) {

    this.validationPath = validationPath;
    this.type = type;
    this.object = object;
    this.parentObject = parentObject;
    this.effectiveScope = effectiveScope;
  }


  /**
   * Gets the validation path.<br>
   * Applications may use it to associate the validation result with a component.
   *
   * @return the validation path
   */
  public String getValidationPath() {
    return validationPath;
  }

  /**
   * Gets the element to validate.<br>
   * This is usually annotated with validator annotation.
   *
   * @return the object to validate
   */
  public Object getObject() {
    return object;
  }

  /**
   * Gets the type of the object.
   *
   * @return the type
   */
  public Class<?> getType() {
    return type;
  }

  /**
   * Gets the effective scope.
   *
   * @return the effective scope
   */
  public ValidationScope getEffectiveScope() {
    return effectiveScope;
  }

  /**
   * Gets the parent object.<br>
   *
   *
   * @return the object containing the element to validate
   */
  public Object getParentObject() {
    return parentObject;
  }

}
