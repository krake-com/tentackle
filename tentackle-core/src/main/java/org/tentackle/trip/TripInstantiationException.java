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

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalGetter;

import java.io.Serial;
import java.util.Objects;

/**
 * Trip instantiation exception.<br>
 * This exception is thrown if an object to be serialized or deserialized cannot be instantiated.
 * Usually, this is caused by a missing no-arg constructor, no {@link org.tentackle.reflect.CanonicalConstructor}
 * or no {@link TripInstanceCreator}.
 */
public class TripInstantiationException extends TripRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final Class<?> clazz;       // the class we cannot instantiate an object for

  /**
   * Constructs a new trip instantiation exception.
   *
   * @param clazz the class we cannot instantiate an object for
   * @param message the detail message
   * @param cause the cause, null for none
   */
  @CanonicalConstructor
  public TripInstantiationException(Class<?> clazz, String message, Throwable cause) {
    super(message, cause);
    this.clazz = Objects.requireNonNull(clazz);
  }

  /**
   * Constructs a new trip instantiation exception.
   *
   * @param clazz the class we cannot instantiate an object for
   * @param message the detail message
   */
  public TripInstantiationException(Class<?> clazz, String message) {
    this(clazz, message, null);
  }

  /**
   * Constructs a new trip instantiation exception.
   *
   * @param clazz the class we cannot instantiate an object for
   */
  public TripInstantiationException(Class<?> clazz) {
    this(clazz, null, null);
  }


  /**
   * Gets the class we cannot instantiate an object for.
   *
   * @return the class, never null
   */
  @CanonicalGetter(ordinal = 0)
  public Class<?> getClazz() {
    return clazz;
  }

  @Override
  @CanonicalGetter(ordinal = 1)
  public String getMessage() {
    return super.getMessage();
  }

  @Override
  @CanonicalGetter(ordinal = 2)
  public Throwable getCause() {
    return super.getCause();
  }

  @Override
  public String toString() {
    return super.toString() + " [" + clazz.getName() + "]";
  }

}
