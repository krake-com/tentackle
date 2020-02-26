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

package org.tentackle.fx;

import javafx.util.StringConverter;

import org.tentackle.common.StringHelper;

import java.util.Objects;

/**
 * CamelCase {@link StringConverter}.
 *
 * @param <T> the type to convert
 */
public class CamelCaseStringConverter<T> extends StringConverter<T> {

  private final T[] objects;

  /**
   * Creates a converter.
   *
   * @param objects the objects representing the available camelcase values
   */
  public CamelCaseStringConverter(T[] objects) {
    this.objects = Objects.requireNonNull(objects);
  }

  /**
   * Gets the objects array.
   *
   * @return the objects
   */
  public T[] getObjects() {
    return objects;
  }

  @Override
  public String toString(T object) {
    if (object != null) {
      return StringHelper.filterUpperCase(object.toString());
    }
    return null;
  }

  @Override
  public T fromString(String string) {
    T object = null;
    if (string != null) {
      int objectIndex = 0;
      String input = string.toUpperCase();
      for (int i=0; i < input.length(); i++) {
        String key = input.substring(0, i);
        while (objectIndex < objects.length) {
          T obj = objects[objectIndex];
          if (obj != null) {
            String camelName = toString(obj);
            if (camelName.startsWith(input)) {
              object = obj;
              break;
            }
          }
        }
      }
    }
    return object;
  }

}
