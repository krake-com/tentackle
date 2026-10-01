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
 * Annotation to define whether instances of the annotated class are serializable or not.
 * <p>
 * By default, all classes are serializable and fields are serializable as long as they
 * are not marked {@code transient}. However, if instances of a class are
 * not meant to be serialized in general, this annotation can be used to disable serialization.
 * <p>
 * This annotation is inherited by subclasses.
 * <p>
 * See also {@link TripDeduplicatable}.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TripSerializable {

  /**
   * Whether instances of this class are serializable or not.
   *
   * @return true if serializable (default), false if not
   */
  boolean value() default true;

  /**
   * An optional name of a public method to be invoked on the object before serialization.<br>
   * The method must be non-static, public, return void, and have no parameters.
   *
   * @return the method name, empty string if none
   */
  String prepare() default "";

  /**
   * An optional name of a public method to be invoked on the object after deserialization.<br>
   * The method must be non-static, public, return void, and have no parameters.
   *
   * @return the method name, empty string if none
   */
  String validate() default "";

}
