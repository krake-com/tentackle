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
    Throwable cause = null;
    StackTraceElement[] stackTrace = null;
    for (Map.Entry<TripComponent<T, ?>, Object> unassigned : componentValues.entrySet()) {
      TripComponent<T, ?> key = unassigned.getKey();
      if ("message".equals(key.getName())) {
        message = (String) unassigned.getValue();
      }
      else if ("cause".equals(key.getName())) {
        cause = (Throwable) unassigned.getValue();
      }
      else if ("stackTrace".equals(key.getName())) {
        stackTrace = (StackTraceElement[]) unassigned.getValue();
      }
      // If there are other components, either annotate with CanonicalConstructor or
      // add a TripComponentProvider/TripInstanceCreator add an application-specific TripType and
      // implement readComponents() method to handle them.
    }

    try {
      if (cause != null) {
        Constructor<T> constructor = getObjectClass().getDeclaredConstructor(String.class, Throwable.class);
        value = constructor.newInstance(message, cause);
      }
      else {
        Constructor<T> constructor = getObjectClass().getDeclaredConstructor(String.class);
        value = constructor.newInstance(message);
      }
    }
    catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
      // return wrapped exception
      value = (T) new TripInstantiationException(getObjectClass(), message, cause);
    }

    if (stackTrace != null) {
      value.setStackTrace(stackTrace);
    }
    return value;
  }

}
