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
 * Data to describe the validation context.
 *
 * @author harald
 */
public class ValidationContext {

  private String validationPath;
  private Class<?> type;
  private Object object;
  private Object parentObject;
  private ValidationScope effectiveScope;


  /**
   * Sets the validation path.<br>
   * The method is invoked before validate().
   *
   * @param validationPath the path in dot-notation corresponding to the object
   */
  public void setValidationPath(String validationPath) {
    this.validationPath = validationPath;
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
   * Sets the object to be validated.<br>
   * Invoked before validate().
   *
   * @param object the object
   */
  public void setObject(Object object) {
    this.object = object;
  }

  /**
   * Gets the validated object.
   * @return the object.
   */
  public Object getObject() {
    return object;
  }

  /**
   * Sets the type of the object.
   *
   * @param type the type
   */
  public void setType(Class<?> type) {
    this.type = type;
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
   * Sets the effective scope for this validator.
   *
   * @param effectiveScope the effective scope
   */
  public void setEffectiveScope(ValidationScope effectiveScope) {
    this.effectiveScope = effectiveScope;
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
   * Sets the root-object containing the annotated element.
   *
   * @param parentObject the root object
   */
  public void setParentObject(Object parentObject) {
    this.parentObject = parentObject;
  }

  /**
   * Gets the parent object
   *
   * @return the object containing the annotated element
   */
  public Object getParentObject() {
    return parentObject;
  }

}
