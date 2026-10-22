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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Component provider for {@link Throwable}.<br>
 * The message, cause and stack trace are accessed via their getters, since the JDK's fields are not accessible.
 * Further fields of subclasses are serialized as usual, see {@link #addField}.
 *
 * @param <T> the throwable type
 */
@TripComponentProviderService(Throwable.class)
public class ThrowableComponentProvider<T extends Throwable> implements TripComponentProvider<T> {

  /**
   * Creates the provider.
   */
  public ThrowableComponentProvider() {
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
          if ("detailMessage".equals(field.getName())) {
            Method method = type.getObjectClass().getMethod("getMessage");
            if (!method.isAnnotationPresent(TripIgnore.class)) {
              components.add(TripFactory.getInstance().createComponent(
                type, ordinal++, method, null, false, null));
            }
          }
          else if ("cause".equals(field.getName())) {
            Method method = type.getObjectClass().getMethod("getCause");
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
          else if (addField(components, field, type, ordinal)) {
            ordinal++;   // added
          }
        }
        catch (NoSuchMethodException e) {
          throw new TripRuntimeException("method is not found in " + type.getObjectClass());
        }
      }
    }
    return components.toArray(new TripComponent[0]);
  }

  /**
   * Adds a field to the given collection.
   *
   * @param components the collection to add the field to
   * @param field the field to add
   * @param type the parent's type
   * @param ordinal the ordinal of the field
   * @return true if the field was added, false otherwise
   */
  protected boolean addField(Collection<TripComponent<T, ?>> components, Field field,
                             TripType<T> type, int ordinal) {

    if (field.getDeclaringClass() == Throwable.class && "suppressedExceptions".equals(field.getName())) {
      // skipped since there is no setter, and it's rarely used by applications at all
      return false;
    }

    // some other field -> let's hope for the best that it can be made accessible...
    components.add(TripFactory.getInstance().createComponent(
      type, ordinal, field, false, null));

    return true;
  }

}
