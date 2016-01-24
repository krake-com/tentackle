/*
 * Tentackle - http://www.tentackle.org.
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

import org.tentackle.misc.AbstractObjectFilter;

/**
 * Filters objects of a given class or child class.<br>
 *
 * @param <T> the type of objects inspected
 * @author harald
 */
public class ClassFilter<T> extends AbstractObjectFilter<T> {

  private final Class<?> clazz;
  private final boolean exactMatch;

  /**
   * Creates a class filter.
   *
   * @param clazz the class of the result object
   * @param exactMatch true if exact class, false if instanceof
   */
  public ClassFilter(Class<?> clazz, boolean exactMatch) {
    this.clazz = clazz;
    this.exactMatch = exactMatch;
  }


  @Override
  public boolean isValid(T object) {
    if (object != null) {
      if (exactMatch) {
        return object.getClass() == clazz;
      }
      else  {
        return clazz.isAssignableFrom(object.getClass());
      }
    }
    return false;
  }

}
