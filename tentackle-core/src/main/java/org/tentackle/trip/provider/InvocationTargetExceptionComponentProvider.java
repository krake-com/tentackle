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
import org.tentackle.trip.TripComponentProviderService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripIgnore;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Component provider for {@link InvocationTargetException}.
 * <p>
 * This exception is not marked as final, so it could be subclassed by applications.
 *
 * @param <T> the InvocationTargetException type
 */
@TripComponentProviderService(InvocationTargetException.class)
public class InvocationTargetExceptionComponentProvider<T extends InvocationTargetException> implements TripComponentProvider<T> {

  /**
   * Creates the provider.
   */
  public InvocationTargetExceptionComponentProvider() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public TripComponent<T, ?>[] getComponents(TripType<T> type) {
    Collection<TripComponent<T, ?>> components = new ArrayList<>();
    int ordinal = 0;
    for (Field field : ReflectionHelper.getAllFields(type.getObjectClass(), null, true, null, true)) {
      if (!Modifier.isTransient(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()) &&
          !field.isAnnotationPresent(TripIgnore.class)) {
        try {
          if ("target".equals(field.getName())) {
            Method method = type.getObjectClass().getMethod("getTargetException");
            components.add(TripFactory.getInstance().createComponent(
              type, ordinal++, method, null, false, null));
          }
          else if ("detailMessage".equals(field.getName())) {
            Method method = type.getObjectClass().getMethod("getMessage");
            if (!method.isAnnotationPresent(TripIgnore.class)) {
              components.add(TripFactory.getInstance().createComponent(
                type, ordinal++, method, null, false, null));
            }
          }
          else if ("stackTrace".equals(field.getName())) {
            Method getter = type.getObjectClass().getMethod("getStackTrace");
            Method setter = type.getObjectClass().getMethod("setStackTrace", StackTraceElement[].class);
            if (!getter.isAnnotationPresent(TripIgnore.class) &&
                !setter.isAnnotationPresent(TripIgnore.class)) {
              components.add(TripFactory.getInstance().createComponent(
                type, ordinal++, getter, setter, false, null));
            }
          }
        }
        catch (NoSuchMethodException e) {
          throw new TripRuntimeException("method is not found in " + type.getObjectClass());
        }
      }
    }
    return components.toArray(new TripComponent[0]);
  }

}
