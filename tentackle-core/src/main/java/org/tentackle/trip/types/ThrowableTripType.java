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

import org.tentackle.log.Logger;
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Reference;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripInstantiationException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.impl.FieldTripComponent;
import org.tentackle.trip.impl.MethodTripComponent;

import java.lang.reflect.Constructor;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
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

  private static final Logger LOGGER = Logger.get(ThrowableTripType.class);

  private static final OpCode OP_CODE = DefaultOpCode.THROWABLE;


  private boolean warningLogged;

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
  protected T createInitializedDefault() {
    try {
      return super.createInitializedDefault();
    }
    catch (TripInstantiationException ix) {
      // missing no-arg constructor?
      if (!warningLogged) {
        LOGGER.warning(
          "{0} -> trying common constructors (consider providing a TripInstanceCreator!)",
          ix.getMessage());
        warningLogged = true;
      }
      try {
        try {
          try {
            return getObjectClass().getDeclaredConstructor(String.class).newInstance((String) null);
          }
          catch (IllegalAccessException ia) {
            Constructor<T> constructor = getObjectClass().getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance((String) null);
          }
        }
        catch (NoSuchMethodException e1) {
          try {
            try {
              return getObjectClass().getDeclaredConstructor(Throwable.class).newInstance((Throwable) null);
            }
            catch (IllegalAccessException ia) {
              Constructor<T> constructor = getObjectClass().getDeclaredConstructor(Throwable.class);
              constructor.setAccessible(true);
              return constructor.newInstance((Throwable) null);
            }
          }
          catch (NoSuchMethodException e2) {
            try {
              try {
                return getObjectClass().getDeclaredConstructor(String.class, Throwable.class)
                                       .newInstance(null, null);
              }
              catch (IllegalAccessException ia) {
                Constructor<T> constructor = getObjectClass().getDeclaredConstructor(String.class, Throwable.class);
                constructor.setAccessible(true);
                return constructor.newInstance(null, null);
              }
            }
            catch (NoSuchMethodException e3) {
              try {
                try {
                  return getObjectClass().getDeclaredConstructor(Throwable.class, String.class)
                                         .newInstance(null, null);
                }
                catch (IllegalAccessException ia) {
                  Constructor<T> constructor = getObjectClass().getDeclaredConstructor(Throwable.class, String.class);
                  constructor.setAccessible(true);
                  return constructor.newInstance(null, null);
                }
              }
              catch (NoSuchMethodException e4) {
                throw new TripInstantiationException(getObjectClass(), "no suitable constructors for " + getObjectClass());
              }
            }
          }
        }
      }
      catch (InaccessibleObjectException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new TripInstantiationException(getObjectClass(), "cannot instantiate " + getObjectClass(), e);
      }
    }
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
    Map<TripComponent<T, ?>, Object> otherComponentValues = new HashMap<>();

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
      else if (key instanceof FieldTripComponent ||
               key instanceof MethodTripComponent<T,?> methodTripComponent && methodTripComponent.getSetter() != null) {
        // default handling (see comment below)
        otherComponentValues.put(key, unassigned.getValue());
      }
      // If there are other components, you could also annotate with CanonicalConstructor or
      // add a TripComponentProvider/TripInstanceCreator add an application-specific TripType and
      // implement readComponents() method to handle them.
    }

    try {
      for (Constructor<?> constructor : getObjectClass().getDeclaredConstructors()) {
        if (hasCause && hasMessage) {
          if (constructor.getParameterCount() == 2) {
            if (constructor.getParameterTypes()[0] == String.class &&
                constructor.getParameterTypes()[1] == Throwable.class) {
              try {
                value = (T) constructor.newInstance(message, cause);
              }
              catch (IllegalAccessException ix) {
                constructor.setAccessible(true);
                value = (T) constructor.newInstance(message, cause);
              }
              break;
            }
            if (constructor.getParameterTypes()[1] == String.class &&
                constructor.getParameterTypes()[0] == Throwable.class) {
              try {
              value = (T) constructor.newInstance(cause, message);
              }
              catch (IllegalAccessException ix) {
                constructor.setAccessible(true);
                value = (T) constructor.newInstance(cause, message);
              }
              break;
            }
          }
        }
        else if (hasCause) {    // cause only
          if (constructor.getParameterCount() == 1 &&
              constructor.getParameterTypes()[0] == Throwable.class) {
            try {
              value = (T) constructor.newInstance(cause);
            }
            catch (IllegalAccessException ix) {
              constructor.setAccessible(true);
              value = (T) constructor.newInstance(cause);
            }
            break;
          }
        }
        else if (hasMessage) {  // message only
          if (constructor.getParameterCount() == 1 &&
              constructor.getParameterTypes()[0] == String.class) {
            try {
              value = (T) constructor.newInstance(message);
            }
            catch (IllegalAccessException ix) {
              constructor.setAccessible(true);
              value = (T) constructor.newInstance(message);
            }
            break;
          }
        }
        else {  // no message and no cause
          if (constructor.getParameterCount() == 0) {
            try {
              value = (T) constructor.newInstance();
            }
            catch (IllegalAccessException ix) {
              constructor.setAccessible(true);
              value = (T) constructor.newInstance();
            }
            break;
          }
        }
      }
    }
    catch (InstantiationException | IllegalAccessException | InaccessibleObjectException | InvocationTargetException e) {
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

    for (Map.Entry<TripComponent<T, ?>, Object> other : otherComponentValues.entrySet()) {
      TripComponent<T, Object> key = (TripComponent<T, Object>) other.getKey();
      Object val = other.getValue();
      key.setValue(value, val);
    }

    return value;
  }

}
