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

package org.tentackle.reflect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class or one of its constructors being constructed by a builder.
 * <p>
 * The constructor must accept a single argument of the builder's type.<br>
 * The builder must provide a no-args constructor and methods
 * named identically to the fields of the object to be built,
 * a single parameter with the field's value and
 * returning the builder instance.<br>
 * The builder must also provide a method returning the built object with no parameters.
 * <p>
 * See the DTO-wurblet with the {@code --canonical} option for an example of
 * properly generated code.
 * <p>
 * Notice that a builder-constructed object does not exist until all its component values have been
 * read. When such an object is deserialized by {@link org.tentackle.trip.types.ObjectTripType},
 * none of its components may therefore back-reference the object itself, directly or indirectly.
 * Objects forming such a cycle must provide a no-args constructor instead.
 *
 * @see CanonicalConstructor
 */
@Documented
@Target({ ElementType.CONSTRUCTOR, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface CanonicalBuilder {

  /**
   * The builder class.
   *
   * @return the builder class
   */
  Class<?> value();

}
