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

package org.tentackle.model;

import java.util.Collection;
import java.util.Objects;

/**
 * Describes a model error.
 *
 * @author harald
 */
public class ModelError {

  /**
   * Creates a string from a collection of errors.<br>
   * Multiple errors are separated by a newline.
   *
   * @param errors the errors
   * @return the error string
   */
  public static String toString(Collection<ModelError> errors) {
    StringBuilder buf = new StringBuilder();
    for (ModelError error: errors) {
      if (!buf.isEmpty()) {
        buf.append('\n');
      }
      buf.append(error);
    }
    return buf.toString();
  }



  private final ModelElement element;   // related element
  private final String message;         // error message

  /**
   * Creates a model error.
   *
   * @param element the related element
   * @param message the error message
   */
  public ModelError(ModelElement element, String message) {
    this.element = element;
    this.message = message;
  }

  /**
   * Creates a model error from a model exception.
   *
   * @param modelException the model exception
   */
  public ModelError(ModelException modelException) {
    this(modelException.getElement(), modelException.getMessage());
  }

  /**
   * Gets the related element.
   *
   * @return the model element, null if none
   */
  public ModelElement getElement() {
    return element;
  }

  /**
   * Gets the error message.
   *
   * @return the error
   */
  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    boolean needSep = false;
    if (element != null) {
      needSep = true;
      buf.append(element.getName());
      SourceInfo sourceInfo = element.getSourceInfo();
      if (sourceInfo != null) {
        buf.append(" (").append(sourceInfo).append(')');
      }
    }
    if (message != null) {
      if (needSep) {
        buf.append(": ");
      }
      buf.append(message);
    }
    return buf.toString();
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 19 * hash + Objects.hashCode(this.element);
    hash = 19 * hash + Objects.hashCode(this.message);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ModelError other = (ModelError) obj;
    if (!Objects.equals(this.message, other.message)) {
      return false;
    }
    return Objects.equals(this.element, other.element);
  }

}
