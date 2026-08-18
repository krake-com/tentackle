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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

  /**
   * The throwable classes the fallback warning has already been logged for.<br>
   * Trip types are stateless singletons, so the log-once bookkeeping must not live in the instance.
   * Keeping it here retains the per-class granularity of the warning and makes it exactly once,
   * even if several threads deserialize the same throwable class concurrently.
   */
  private static final Set<Class<?>> WARNED_CLASSES = ConcurrentHashMap.newKeySet();

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
      if (WARNED_CLASSES.add(getObjectClass())) {
        LOGGER.warning(
          "{0} -> trying common constructors (consider providing a TripInstanceCreator!)",
          ix.getMessage());
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
        // a null message carries no information for the constructor selection below
        hasMessage = message != null;
      }
      else if ("cause".equals(key.getName())) {
        cause = (Throwable) unassigned.getValue();
        // ditto for a null cause
        hasCause = cause != null;
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

    boolean constructed = false;
    try {
      for (Constructor<?> constructor : getObjectClass().getDeclaredConstructors()) {
        if (hasCause && hasMessage) {
          if (constructor.getParameterCount() == 2) {
            if (constructor.getParameterTypes()[0] == String.class &&
                constructor.getParameterTypes()[1] == Throwable.class) {
              value = newThrowable(constructor, message, cause);
              constructed = true;
              break;
            }
            if (constructor.getParameterTypes()[1] == String.class &&
                constructor.getParameterTypes()[0] == Throwable.class) {
              value = newThrowable(constructor, cause, message);
              constructed = true;
              break;
            }
          }
        }
        else if (hasCause) {    // cause only
          if (constructor.getParameterCount() == 1 &&
              constructor.getParameterTypes()[0] == Throwable.class) {
            value = newThrowable(constructor, cause);
            constructed = true;
            break;
          }
        }
        else if (hasMessage) {  // message only
          if (constructor.getParameterCount() == 1 &&
              constructor.getParameterTypes()[0] == String.class) {
            value = newThrowable(constructor, message);
            constructed = true;
            break;
          }
        }
        else {  // no message and no cause
          if (constructor.getParameterCount() == 0) {
            value = newThrowable(constructor);
            constructed = true;
            break;
          }
        }
      }
      if (!constructed && hasCause && hasMessage) {
        // no (message, cause)-style constructor, e.g. NullPointerException:
        // fall back to a message-only constructor and initCause()
        for (Constructor<?> constructor : getObjectClass().getDeclaredConstructors()) {
          if (constructor.getParameterCount() == 1 &&
              constructor.getParameterTypes()[0] == String.class) {
            value = newThrowable(constructor, message);
            try {
              value.initCause(cause);
            }
            catch (IllegalStateException isx) {
              // the constructor already initialized the cause
            }
            constructed = true;
            break;
          }
        }
      }
    }
    catch (InstantiationException | IllegalAccessException | InaccessibleObjectException |
           InvocationTargetException e) {
      // return wrapped exception
      value = (T) new TripInstantiationException(getObjectClass(), message, cause);
      constructed = true;
    }

    if (!constructed && value == null) {    // no matching constructor and no default instance
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


  /**
   * Creates a throwable via the given constructor.
   *
   * @param constructor the constructor
   * @param args the constructor arguments
   * @return the created throwable
   * @throws InstantiationException if the class is abstract
   * @throws IllegalAccessException if the constructor is inaccessible even after setAccessible
   * @throws InvocationTargetException if the constructor throws an exception
   */
  @SuppressWarnings("unchecked")
  private T newThrowable(Constructor<?> constructor, Object... args)
    throws InstantiationException, IllegalAccessException, InvocationTargetException {

    try {
      return (T) constructor.newInstance(args);
    }
    catch (IllegalAccessException ix) {
      constructor.setAccessible(true);
      return (T) constructor.newInstance(args);
    }
  }

}
