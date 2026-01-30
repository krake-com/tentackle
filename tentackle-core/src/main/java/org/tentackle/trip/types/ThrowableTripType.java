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

package org.tentackle.trip.types;

import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Reference;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripInstantiationException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Trip type for {@link Throwable}.
 *
 * @param <T> the throwable type
 */
// created by ThrowableTypeBuilder, not mapped by @TripTypeService
public final class ThrowableTripType<T extends Throwable> extends ObjectTripType<T> {

  private static final OpCode OP_CODE = DefaultOpCode.THROWABLE;

  /**
   * Creates the type.
   *
   * @param clazz the throwable's class
   */
  public ThrowableTripType(Class<T> clazz) {
    super(clazz);
  }

  @Override
  public boolean isReferencable(T value) {
    // exceptions stand for their own
    return false;
  }

  @Override
  protected OpCode getOpCode() {
    return OP_CODE;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected T readComponents(TripStream stream, OpCode opCode, DictionaryEntry entry, Reference<T> reference, T value, Consumer<T> initializer, Map<TripComponent<T, ?>, Object> componentValues) {
    if (componentValues != null || getCanonicalConstructor() != null) {
      return super.readComponents(stream, opCode, entry, reference, value, initializer, componentValues);
    }

    componentValues = new LinkedHashMap<>();
    super.readComponents(stream, opCode, entry, reference, value, initializer, componentValues);  // value is null

    // no @CanonicalConstructor -> get constructor args from the values-map
    String message = null;
    boolean hasMessage = false;
    Throwable cause = null;
    boolean hasCause = false;
    StackTraceElement[] stackTrace = null;
    boolean hasStackTrace = false;
    for (Map.Entry<TripComponent<T, ?>, Object> unassigned : componentValues.entrySet()) {
      TripComponent<T, ?> key = unassigned.getKey();
      if ("message".equals(key.getName())) {
        message = (String) unassigned.getValue();
        hasMessage = true;
      }
      else if ("cause".equals(key.getName())) {
        cause = (Throwable) unassigned.getValue();
        hasCause = true;
      }
      else if ("stackTrace".equals(key.getName())) {
        stackTrace = (StackTraceElement[]) unassigned.getValue();
        hasStackTrace = true;
      }
      // If there are other components, either annotate with CanonicalConstructor or
      // add a TripComponentProvider/TripInstanceCreator add an application-specific TripType and
      // implement readComponents() method to handle them.
    }

    try {
      for (Constructor<?> constructor : getObjectClass().getDeclaredConstructors()) {
        if (hasCause && hasMessage) {
          if (constructor.getParameterCount() == 2) {
            if (constructor.getParameterTypes()[0] == String.class &&
                constructor.getParameterTypes()[1] == Throwable.class) {
              value = (T) constructor.newInstance(message, cause);
              break;
            }
            if (constructor.getParameterTypes()[1] == String.class &&
                constructor.getParameterTypes()[0] == Throwable.class) {
              value = (T) constructor.newInstance(cause, message);
              break;
            }
          }
        }
        else if (hasCause) {    // cause only
          if (constructor.getParameterCount() == 1 &&
              constructor.getParameterTypes()[0] == Throwable.class) {
            value = (T) constructor.newInstance(cause);
            break;
          }
        }
        else if (hasMessage) {  // message only
          if (constructor.getParameterCount() == 1 &&
              constructor.getParameterTypes()[0] == String.class) {
            value = (T) constructor.newInstance(message);
            break;
          }
        }
        else {  // no message and no cause
          if (constructor.getParameterCount() == 0) {
            value = (T) constructor.newInstance();
            break;
          }
        }
      }
    }
    catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      // return wrapped exception
      value = (T) new TripInstantiationException(getObjectClass(), message, cause);
    }

    if (value == null) {    // no matching constructor
      // return wrapped exception
      value = (T) new TripInstantiationException(getObjectClass(), message, cause);
    }

    if (hasStackTrace) {
      value.setStackTrace(stackTrace);
    }
    return value;
  }

}
