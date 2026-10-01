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
import java.util.List;

/**
 * A list of validators.
 * <p>
 * Notice that {@link ValidationUtilities} does not use this interface, but unwraps the
 * &#64;{@link RepeatableValidation} containers itself.
 * <p>
 * Lists of validators are defined as follows:
 * <pre>
 *    &lt;Annotation&gt;.List ({
 *           &lt;Annotation&gt;(...),
 *           &lt;Annotation&gt;(...)
 *        })
 * </pre>
 *
 * @author harald
 */
public interface ValidatorList {

  /**
   * Sets the annotation of the validator list.
   *
   * @param annotation the annotation
   */
  void setAnnotation(Annotation annotation);


  /**
   * Gets the annotation of this validator list.
   *
   * @return the annotation
   */
  Annotation getAnnotation();


  /**
   * Gets the annotations.
   *
   * @return the annotations
   */
  List<? extends Annotation> getAnnotationList();

}
