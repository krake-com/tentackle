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

package org.tentackle.trip.provider;

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripComponentProvider;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripIgnore;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Base class for {@link TripComponentProvider} implementations.<br>
 * Provides the given serializable fields (neither static, transient nor annotated with &#64;{@link TripIgnore})
 * of the class and its superclasses as components. The ordinals follow the order of declaration.
 *
 * @param <T> the type
 */
public abstract class AbstractTripComponentProvider<T> implements TripComponentProvider<T> {

  private final Class<T> clazz;
  private final String[] fieldNames;

  /**
   * Creates the provider.
   *
   * @param clazz the class to get the components for
   * @param fieldNames the field names to get the components for
   */
  public AbstractTripComponentProvider(Class<T> clazz, String[] fieldNames) {
    this.clazz = clazz;
    this.fieldNames = fieldNames;
  }

  /**
   * {@inheritDoc}
   *
   * @throws TripRuntimeException if one of the field names does not denote a serializable field
   */
  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public TripComponent<T, ?>[] getComponents(TripType<T> type) {
    TripComponent<T, ?>[] components = new TripComponent[fieldNames.length];
    int ordinal = 0;
    for (Field field : ReflectionHelper.getAllFields(clazz, null, true, null, true)) {
      if (!Modifier.isTransient(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()) &&
          !field.isAnnotationPresent(TripIgnore.class)) {
        for (String fieldName : fieldNames) {
          if (fieldName.equals(field.getName())) {
            components[ordinal] = TripFactory.getInstance().createComponent(type, ordinal, field, false, null);
            ordinal++;
            break;
          }
        }
      }
    }
    if (ordinal < components.length) {
      throw new TripRuntimeException("not all fields " + String.join(", ", fieldNames) +
                                     " found as serializable fields in " + clazz.getName());
    }
    return components;
  }

}
