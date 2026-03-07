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

import java.util.Map;

/**
 * Creates an instance of a class.<br>
 * Must be annotated with {@link TripInstanceCreatorService}.<br>
 * Instance creators are used if there is no public no-arg constructor, and
 * it is not possible to define a {@link org.tentackle.reflect.CanonicalConstructor}.
 * <p>
 * Instance creators come in two flavors:
 * <ol>
 *   <li>Creating the default instance only.</li>
 *   <li>Creating the instance using the given deserialized values. The creator
 *   covers the complete creation process, i.e., instance creation, invoking
 *   setters, configuration methods, whatever.</li>
 * </ol>
 * Instance creators are often used in conjunction with {@link TripComponentProvider}s.
 *
 * @param <T> the type of the instance to create
 */
public interface TripInstanceCreator<T> {

  /**
   * Creates a default instance of the class.
   *
   * @param clazz the class to create an instance for
   * @return the instance
   */
  T create(Class<T> clazz);

  /**
   * Creates an instance of the class with the given values via constructor.<br>
   * This method is only invoked if {@link #isCreationFromValuesSupported(Class)} returns true.
   *
   * @param clazz the class to create an instance for
   * @param values map of the components to their retrieved values
   * @return the instance
   */
  default T create(Class<T> clazz, Map<TripComponent<T, ?>, Object> values) {
    throw new TripInstantiationException(clazz, "constructor creation from deserialized values is not supported");
  }

  /**
   * Returns whether constructor creation from the deserialized values is supported.
   *
   * @param clazz the class to create an instance for
   * @return true, if constructor creation is supported
   */
  default boolean isCreationFromValuesSupported(Class<T> clazz) {
    return false;
  }

}
