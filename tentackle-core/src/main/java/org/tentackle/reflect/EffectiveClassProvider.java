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

import java.util.List;

/**
 * An object that provides the effective class.
 * <p>
 * Mainly implemented by interfaces used via dynamic proxies where getClass() delivers
 * the (usually wrong) proxy class.
 *
 * @param <T> the effective type
 * @author harald
 */
public interface EffectiveClassProvider<T> {

  /**
   * Gets the effective class of given object.
   *
   * @param object the object
   * @return the effective class, null if object is null
   */
  static Class<?> getEffectiveClass(Object object) {
    Class<?> clazz;
    if (object != null) {
      if (object instanceof EffectiveClassProvider) {
        clazz = ((EffectiveClassProvider<?>) object).getEffectiveClass();
      }
      else {
        clazz = object.getClass();
      }
    }
    else {
      clazz = null;
    }
    return clazz;
  }

  /**
   * Gets the effective class.
   *
   * @return the effective class
   */
  @NotInterceptable
  Class<T> getEffectiveClass();

  /**
   * Gets the effective super classes.
   *
   * @return the super classes, never null
   */
  @NotInterceptable
  List<Class<? super T>> getEffectiveSuperClasses();

}
