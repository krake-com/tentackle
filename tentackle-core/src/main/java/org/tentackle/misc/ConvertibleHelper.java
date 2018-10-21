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

package org.tentackle.misc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.common.TentackleRuntimeException;

/**
 * Helper class for {@link Convertible}s.
 *
 * @author harald
 */
public final class ConvertibleHelper {

  private static final ConcurrentHashMap<Class<? extends Convertible<?>>, Method> METHOD_MAP =
          new ConcurrentHashMap<>();


  /**
   * Creates an instance of a {@link Convertible} from some other type.
   * <p>
   * Works by reflection.
   *
   * @param <C> the class implementing {@link Convertible}
   * @param <T> the external type to be converted from
   * @param clazz the class implementing {@link Convertible}
   * @param external the external value
   * @return the created convertible
   */
  @SuppressWarnings("unchecked")
  public static <C extends Convertible<T>, T> C toInternal(Class<C> clazz, T external) {
    Method method = METHOD_MAP.get(clazz);
    if (method == null) {
      // find method "I toInternal(E external)" in clazz
      try {
        method = clazz.getDeclaredMethod("toInternal", external.getClass());
        if (Modifier.isStatic(method.getModifiers())) {
          METHOD_MAP.put(clazz,method);
        }
      }
      catch (NoSuchMethodException nsm) {
        throw new RuntimeException("class " + clazz.getName() + " does not provide static method 'toInternal'");
      }
    }
    try {
      return (C) method.invoke(null, external);
    }
    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new TentackleRuntimeException("invocation of " + method + " failed", e);
    }
  }


  /**
   * prevent instantiation.
   */
  private ConvertibleHelper() {}

}
