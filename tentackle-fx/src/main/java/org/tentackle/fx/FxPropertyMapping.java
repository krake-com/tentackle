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

import org.tentackle.misc.ImmutableException;
import org.tentackle.misc.ObjectUtilities;
import org.tentackle.reflect.PropertyMapping;

import javafx.fxml.FXMLLoader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Extended {@link PropertyMapping} to support lists.
 *
 * @param <T> the type of the bean providing the property
 */
public class FxPropertyMapping<T> extends PropertyMapping<T> {

  /**
   * Creates a property mapping.
   *
   * @param name   the name of the property
   * @param getter the getter
   * @param setter the setter, null if readonly
   */
  public FxPropertyMapping(String name, Method getter, Method setter) {
    super(name, getter, setter);
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void set(T object, Object value) {
    try {
      if (isReadOnly()) {
        if (List.class.isAssignableFrom(getValueType())) {  // getStylesheets, getChildren, for ex.
          List list = (List) getGetter().invoke(object);    // must work and list cannot be null!
          if (value instanceof List values) {
            list.addAll(values);
          }
          else if (value != null) {
            Collections.addAll(list, value.toString().split(FXMLLoader.ARRAY_COMPONENT_DELIMITER));
          }
          else {
            throw new FxRuntimeException("setting list property " + this + " failed: value is null");
          }
        }
        else {
          throw new ImmutableException("property " + this + " is read-only");
        }
      }
      else {
        getSetter().invoke(object, ObjectUtilities.getInstance().convert(getValueType(), value));
      }
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new FxRuntimeException("setting property " + this + " failed", e);
    }
  }

}
