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

import java.lang.annotation.Annotation;

/**
 * The repeatable container validator.
 * <p>
 * Notice that {@link ValidationUtilities} does not use this interface, but unwraps the
 * &#64;{@link RepeatableValidation} containers itself.
 *
 * @author harald
 */
public interface RepeatableValidator {

  /**
   * Sets the annotation of the container validator.
   *
   * @param annotation the annotation
   */
  void setAnnotation(Annotation annotation);

  /**
   * Gets the annotation of this container validator.
   *
   * @return the annotation
   */
  Annotation getAnnotation();

  /**
   * Gets the contained annotations.
   *
   * @return the annotations
   */
  Annotation[] getAnnotations();

}
