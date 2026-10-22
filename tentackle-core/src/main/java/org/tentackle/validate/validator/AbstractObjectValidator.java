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

import java.lang.annotation.Annotation;

/**
 * Object validator base class.<br>
 * Object validators have neither a value nor a condition.
 *
 * @author harald
 * @param <T> the annotation type
 */
public abstract class AbstractObjectValidator<T extends Annotation> extends AbstractValidator<T> {

  /**
   * Creates an object validator.
   */
  public AbstractObjectValidator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getValue() {
    return "";    // does not apply to object validators
  }

  @Override
  public String getCondition() {
    return "";    // does not apply to object validators
  }

}
