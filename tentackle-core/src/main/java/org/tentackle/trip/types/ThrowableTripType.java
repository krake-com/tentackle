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
import java.lang.reflect.Modifier;
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
   * The throwable classes the warning about the missing constructor has already been logged for.<br>
   * Trip types are stateless singletons, so the log-once bookkeeping must not live in the instance.
   * Keeping it here retains the per-class granularity of the warning and makes it exactly once,
   * even if several threads deserialize the same throwable class concurrently.
   */
  private static final Set<Class<?>> WARNED_CLASSES = ConcurrentHashMap.newKeySet();

  /**
   * The throwable classes without a usable no-args constructor or instance creator.
   */
  private static final Set<Class<?>> NO_DEFAULT_CLASSES = ConcurrentHashMap.newKeySet();

  /**
   * Placeholder for a null message passed to a constructor.
   */
  private static final Object MESSAGE_SLOT = new Object();

  /**
   * Placeholder for a null cause passed to a constructor.
   */
  private static final Object CAUSE_SLOT = new Object();

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


  /**
   * Creates the default instance.<br>
   * Unlike other objects, a throwable without a (usable) no-args constructor or instance creator is
   * not an error: it is created from the deserialized message and cause, see {@link #readComponents}.
   *
   * @return the instance, null if created from the deserialized values
   */
  @Override
  protected T createInitializedDefault() {
    if (NO_DEFAULT_CLASSES.contains(getObjectClass())) {
      return null;
    }
    try {
      return super.createInitializedDefault();
    }
    catch (TripInstantiationException ix) {
      // missing or inaccessible no-arg constructor
      NO_DEFAULT_CLASSES.add(getObjectClass());
      return null;
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

    T throwable = newThrowable(hasMessage ? message : null, hasCause ? cause : null);
    if (throwable == null) {
      // no usable constructor: fall back to the default instance, if any
      if (value == null) {
        // return wrapped exception
        if (WARNED_CLASSES.add(getObjectClass())) {
          LOGGER.warning("no usable constructor for {0} -> deserialized as {1} (consider providing a TripInstanceCreator!)",
                         getObjectClass().getName(), TripInstantiationException.class.getName());
        }
        throwable = (T) new TripInstantiationException(getObjectClass(), message, cause);
        otherComponentValues.clear();   // they belong to the original class
      }
      else {
        throwable = value;
      }
    }
    value = throwable;

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
   * Creates a throwable from its message and cause.<br>
   * Tries the common constructor signatures in a reasonable order.
   * The cause parameter may be of any type the cause is an instance of, for example,
   * {@code UncheckedIOException(String, IOException)}. Constructors that are not accessible
   * (for example, protected constructors in a JDK package) or throw an exception are skipped.
   * If the chosen constructor does not take the cause, it is initialized via {@link Throwable#initCause}.
   *
   * @param message the message, null if none
   * @param cause the cause, null if none
   * @return the throwable, null if no usable constructor found
   */
  private T newThrowable(String message, Throwable cause) {
    Constructor<?>[] constructors = getObjectClass().getDeclaredConstructors();
    T throwable;
    if (message != null && cause != null) {
      throwable = newThrowable(constructors, cause, message, cause);
      if (throwable == null) {
        throwable = newThrowable(constructors, cause, cause, message);
      }
      if (throwable == null) {
        // no (message, cause)-style constructor, e.g. NullPointerException
        throwable = newThrowable(constructors, cause, message);
      }
    }
    else if (cause != null) {
      throwable = newThrowable(constructors, cause, cause);
      if (throwable == null) {
        throwable = newThrowable(constructors, cause, MESSAGE_SLOT, cause);
      }
      if (throwable == null) {
        throwable = newThrowable(constructors, cause, cause, MESSAGE_SLOT);
      }
      if (throwable == null) {
        throwable = newThrowable(constructors, cause);
      }
    }
    else if (message != null) {
      throwable = newThrowable(constructors, null, message);
      if (throwable == null) {
        throwable = newThrowable(constructors, null, message, CAUSE_SLOT);
      }
      if (throwable == null) {
        throwable = newThrowable(constructors, null, CAUSE_SLOT, message);
      }
    }
    else {
      throwable = newThrowable(constructors, null);
      if (throwable == null) {
        throwable = newThrowable(constructors, null, MESSAGE_SLOT);
      }
      if (throwable == null) {
        throwable = newThrowable(constructors, null, CAUSE_SLOT);
      }
      if (throwable == null) {
        throwable = newThrowable(constructors, null, MESSAGE_SLOT, CAUSE_SLOT);
      }
    }
    return throwable;
  }

  /**
   * Creates a throwable via the first matching constructor.
   *
   * @param constructors the declared constructors
   * @param cause the cause to initialize, if not passed to the constructor, null if none
   * @param args the arguments: a string for the message, a throwable for the cause, or
   *        {@link #MESSAGE_SLOT}/{@link #CAUSE_SLOT} for a null message/cause
   * @return the throwable, null if no matching constructor or the constructor failed
   */
  @SuppressWarnings("unchecked")
  private T newThrowable(Constructor<?>[] constructors, Throwable cause, Object... args) {
    for (Constructor<?> constructor : constructors) {
      if (constructor.getParameterCount() == args.length && matches(constructor.getParameterTypes(), args)) {
        if (!Modifier.isPublic(constructor.getModifiers()) || !Modifier.isPublic(getObjectClass().getModifiers())) {
          if (!constructor.trySetAccessible()) {
            continue;   // e.g. protected constructor in a JDK package
          }
        }
        Object[] values = new Object[args.length];
        boolean causePassed = false;
        for (int i = 0; i < args.length; i++) {
          Object arg = args[i];
          if (arg instanceof Throwable) {
            causePassed = true;
          }
          values[i] = arg == MESSAGE_SLOT || arg == CAUSE_SLOT ? null : arg;
        }
        T throwable;
        try {
          throwable = (T) constructor.newInstance(values);
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException |
               IllegalArgumentException | InaccessibleObjectException e) {
          // try next, e.g. constructor rejects null
          continue;
        }
        if (cause != null && !causePassed) {
          try {
            throwable.initCause(cause);
          }
          catch (IllegalStateException isx) {
            // the constructor already initialized the cause
          }
        }
        return throwable;
      }
    }
    return null;
  }

  /**
   * Checks whether the constructor's parameter types match the arguments.
   *
   * @param parameterTypes the parameter types
   * @param args the arguments
   * @return true if matching
   */
  private static boolean matches(Class<?>[] parameterTypes, Object[] args) {
    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];
      Class<?> type = parameterTypes[i];
      if (arg == MESSAGE_SLOT || arg instanceof String) {
        if (type != String.class) {
          return false;
        }
      }
      else if (arg == CAUSE_SLOT) {
        if (!Throwable.class.isAssignableFrom(type)) {
          return false;
        }
      }
      else if (!Throwable.class.isAssignableFrom(type) || !type.isInstance(arg)) {
        return false;
      }
    }
    return true;
  }

}
