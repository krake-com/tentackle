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
 * Annotation to define whether the annotated class is referencable or not.
 * <p>
 * By default, all primitives, their wrappers and {@code String} are known as so-called
 * <em>value objects</em> and thus not referencable. All other objects, however, are referencable
 * by default.<br>
 * If the class provides a public method {@code boolean isReferencable()}
 * (or another name provided in the {@code value} parameter),
 * that method will be consulted.<br>
 * The method name {@link #NEVER} sets all objects to be non-referencable.
 * The method name {@link #ALWAYS} explicitly sets all objects to be referencable.
 * <p>
 * This annotation is inherited by subclasses.
 * <p>
 * See also {@link TripDeduplicatable}.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TripReferencable {

  /**
   * Not referencable.
   */
  String NEVER = "NEVER";

  /**
   * Always referencable.
   */
  String ALWAYS = "ALWAYS";

  /**
   * The name of the method to be consulted whether referencable or not.
   *
   * @return the name of the method
   */
  String value() default "isReferencable";

}
