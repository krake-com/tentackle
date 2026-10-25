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

package org.tentackle.trip.creators;

import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripInstanceCreator;
import org.tentackle.trip.TripInstantiationException;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for instance-creators of throwables that must be created via a constructor taking
 * more than the message and the cause.<br>
 * Typically used for the JDK's exceptions, whose fields are not accessible and often final.
 * Their values are transferred via public getters (see {@link org.tentackle.trip.provider.ThrowableComponentProvider})
 * and passed to the constructor.
 * <p>
 * The cause is initialized via {@link Throwable#initCause(Throwable)} unless already set by the constructor,
 * and the stack trace is applied afterward.
 * If the throwable cannot be created, a {@link TripInstantiationException} carrying the message and cause
 * is returned instead.
 *
 * @param <T> the throwable type
 */
public abstract class AbstractThrowableInstanceCreator<T extends Throwable> implements TripInstanceCreator<T> {

  /**
   * Parent constructor.
   */
  protected AbstractThrowableInstanceCreator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Returns null, since the throwable is always created from the deserialized values.
   *
   * @param clazz the class to create an instance for
   * @return null
   */
  @Override
  public T create(Class<T> clazz) {
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T create(Class<T> clazz, Map<TripComponent<T, ?>, Object> values) {
    Values namedValues = new Values(values);
    T throwable;
    try {
      throwable = create(clazz, namedValues);
    }
    catch (ReflectiveOperationException | RuntimeException e) {
      throwable = null;
    }
    Throwable cause = namedValues.get("cause");
    if (throwable == null) {
      throwable = (T) new TripInstantiationException(clazz, namedValues.get("message"), cause);
    }
    else if (cause != null && throwable.getCause() == null) {
      try {
        throwable.initCause(cause);
      }
      catch (IllegalStateException isx) {
        // the constructor already initialized the cause (to null)
      }
    }
    StackTraceElement[] stackTrace = namedValues.get("stackTrace");
    if (stackTrace != null) {
      throwable.setStackTrace(stackTrace);
    }
    return throwable;
  }

  @Override
  public boolean isCreationFromValuesSupported(Class<T> clazz) {
    return true;
  }


  /**
   * Creates the throwable from the deserialized values.
   *
   * @param clazz the class to create an instance for
   * @param values the values by component name
   * @return the throwable, null if it cannot be created
   * @throws ReflectiveOperationException if invoking the constructor failed
   */
  protected abstract T create(Class<T> clazz, Values values) throws ReflectiveOperationException;

  /**
   * Creates the throwable via a public constructor of the given class.
   *
   * @param clazz the class to create an instance for
   * @param parameterTypes the constructor's parameter types
   * @param args the arguments
   * @return the throwable, null if there is no such constructor
   * @throws ReflectiveOperationException if invoking the constructor failed
   */
  protected T newInstance(Class<T> clazz, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
    Constructor<T> constructor;
    try {
      constructor = clazz.getConstructor(parameterTypes);
    }
    catch (NoSuchMethodException e) {
      return null;
    }
    return constructor.newInstance(args);
  }


  /**
   * The deserialized values by component name.
   */
  protected static class Values {

    private final Map<String, Object> values = new HashMap<>();

    /**
     * Creates the values.
     *
     * @param values the values by component
     * @param <T> the throwable type
     */
    protected <T> Values(Map<TripComponent<T, ?>, Object> values) {
      for (Map.Entry<TripComponent<T, ?>, Object> entry : values.entrySet()) {
        this.values.put(entry.getKey().getName(), entry.getValue());
      }
    }

    /**
     * Gets a value.
     *
     * @param name the component name
     * @param <V> the value type
     * @return the value, null if missing
     */
    @SuppressWarnings("unchecked")
    public <V> V get(String name) {
      return (V) values.get(name);
    }

    /**
     * Gets an int value.
     *
     * @param name the component name
     * @param defaultValue the value if missing
     * @return the value
     */
    public int getInt(String name, int defaultValue) {
      return values.get(name) instanceof Integer value ? value : defaultValue;
    }

    /**
     * Gets a boolean value.
     *
     * @param name the component name
     * @return the value, false if missing
     */
    public boolean getBoolean(String name) {
      return Boolean.TRUE.equals(values.get(name));
    }

  }

}
