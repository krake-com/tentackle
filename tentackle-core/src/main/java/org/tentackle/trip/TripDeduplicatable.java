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

package org.tentackle.trip;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to define whether instances of the annotated class are subject to deduplication or not.
 * <p>
 * By default, only strings and records are deduplicatable. However, if instances of a regular class are
 * immutable (usually DTOs or so-called value objects), this annotation can be used to enable deduplication.<br>
 * Furthermore, if the class provides a public method {@code boolean isDeduplicatable()}
 * (or another name provided in the {@code value} parameter),
 * that method will be consulted.<br>
 * The method name {@link #NEVER} explicitly disables deduplication.
 * The method name {@link #ALWAYS} enables deduplication.
 * <p>
 * This annotation is inherited by subclasses!
 * <p>
 * See also {@link TripReferencable}.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TripDeduplicatable {

  /**
   * Turns off deduplication.
   */
  String NEVER = "NEVER";

  /**
   * Turns on deduplication w/o consulting any method.
   */
  String ALWAYS = "ALWAYS";

  /**
   * The name of the method to be consulted for deduplication.
   *
   * @return the name of the method
   */
  String value() default "isDeduplicatable";

}
