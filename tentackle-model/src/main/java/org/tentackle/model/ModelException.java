/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.model;

import java.io.Serial;
import java.util.Collection;

/**
 * Model parsing exception.
 *
 * @author harald
 */
public class ModelException extends Exception {

  @Serial
  private static final long serialVersionUID = 1L;

  private final transient ModelElement element;   // the related model element

  /** string prepended to the source location. */
  public static final String AT_ELEMENT = " @ ";


  /**
   * Creates a model exception with <code>null</code> as its detail message and no related element.
   */
  public ModelException() {
    super();
    this.element = null;
  }

  /**
   * Creates a model exception.
   *
   * @param message the error message
   * @param element the related model element
   * @param cause the cause
   */
  public ModelException(String message, ModelElement element, Throwable cause) {
    super(message, cause);
    this.element = element;
  }

  /**
   * Creates a model exception.
   *
   * @param message the error message
   * @param element the related model element
   */
  public ModelException(String message, ModelElement element) {
    super(message);
    this.element = element;
  }

  /**
   * Creates a model exception.
   *
   * @param message the error message
   * @param cause the cause
   */
  public ModelException(String message, Throwable cause) {
    super(message, cause);
    this.element = null;
  }

  /**
   * Creates a model exception.
   *
   * @param message the error message
   */
  public ModelException(String message) {
    super(message);
    this.element = null;
  }

  /**
   * Creates a model exception from a collection of errors.
   *
   * @param errors the errors
   */
  public ModelException(Collection<ModelError> errors) {
    super(ModelError.toString(errors));
    element = pickFirstElement(errors);
  }


  /**
   * Gets the related model element.
   *
   * @return the element, null if none
   */
  public ModelElement getElement() {
    return element;
  }

  @Override
  public String getMessage() {
    String message = super.getMessage();
    if (element != null && (message == null || !message.contains(AT_ELEMENT)) && element.getSourceInfo() != null) {
      if (message == null) {
        return AT_ELEMENT + element.getSourceInfo();
      }
      if (!message.contains(element.getSourceInfo().toString())) {
        return message + AT_ELEMENT + element.getSourceInfo();
      }
    }
    return message;
  }

  /**
   * Returns whether the given element is related to this exception.
   *
   * @param otherElement some other model element
   * @return true if related
   */
  public boolean isRelatedTo(ModelElement otherElement) {
    ModelElement elem = element;
    while (elem != null) {
      if (elem.equals(otherElement)) {
        return true;
      }
      elem = elem.getParent();
    }
    return false;
  }


  /**
   * Picks the first model element from a collection of errors.
   *
   * @param errors the errors
   * @return the element, null if none
   */
  private ModelElement pickFirstElement(Collection<ModelError> errors) {
    for (ModelError error: errors) {
      if (error.getElement() != null) {
        return error.getElement();
      }
    }
    return null;
  }

}
