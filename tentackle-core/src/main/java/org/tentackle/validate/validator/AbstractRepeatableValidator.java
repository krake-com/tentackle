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

import org.tentackle.validate.RepeatableValidator;

import java.lang.annotation.Annotation;

/**
 * An abstract repeatable validator.
 *
 * @param <T> the container validation annotation
 * @author harald
 */
public abstract class AbstractRepeatableValidator<T extends Annotation> implements RepeatableValidator {

  protected T annotation;                       // the container annotation

  /**
   * Creates a repeatable validator.
   */
  public AbstractRepeatableValidator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String toString() {
    return getClass().getName();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setAnnotation(Annotation annotation) {
    this.annotation = (T) annotation;
  }

  @Override
  public T getAnnotation() {
    return annotation;
  }

}
