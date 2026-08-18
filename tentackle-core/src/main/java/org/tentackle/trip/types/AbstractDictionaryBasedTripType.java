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

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.Remote;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripComponentProvider;
import org.tentackle.trip.TripDeduplicatable;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripIgnore;
import org.tentackle.trip.TripInstanceCreator;
import org.tentackle.trip.TripInstantiationException;
import org.tentackle.trip.TripReferencable;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripSerializable;
import org.tentackle.trip.TripTypeCategory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Base class for dictionary-aware trip types.
 *
 * @param <T> the type
 */
public abstract class AbstractDictionaryBasedTripType<T> extends AbstractTripType<T> {

  /**
   * The lazily resolved component provider and its resolution flag.<br>
   * Trip types are shared singletons, hence the fields must be volatile: without it, another thread
   * could observe the flag being set while the provider is still null. The flag must always be
   * written <em>after</em> the provider.
   */
  private volatile boolean componentProviderSet;
  private volatile TripComponentProvider<T> componentProvider;

  /**
   * The lazily resolved instance creator and its resolution flag.<br>
   * Volatile for the same reason as {@link #componentProvider}. This one is resolved on the
   * deserialization path (see {@link ObjectTripType#readComponents}) whenever the constructor
   * did not resolve it already, i.e. for delayed construction and non-instantiable types.
   */
  private volatile boolean instanceCreatorSet;
  private volatile TripInstanceCreator<T> instanceCreator;

  /**
   * An optional method provided by {@link TripSerializable} to prepare the object before serialization.
   */
  protected final Method prepareMethod;

  /**
   * An optional method provided by {@link TripSerializable} to validate the object after deserialization.
   */
  protected final Method validateMethod;

  /**
   * Optional method provided by {@link TripDeduplicatable} to
   * decide whether objects are deduplicatable or not.
   */
  protected final Method deduplicatableMethod;

  /**
   * Whether objects are deduplicatable according to {@link TripDeduplicatable}.<br>
   * Null if no annotation present.
   */
  protected final Boolean deduplicatable;

  /**
   * Optional method provided by {@link TripReferencable} to
   * decide whether objects are referencable or not.
   */
  protected final Method referencableMethod;

  /**
   * Whether objects are explicitly not referencable according to {@link TripReferencable}.<br>
   * Null if no annotation present.
   */
  protected final Boolean referencable;

  /**
   * Creates the type.
   *
   * @param clazz the object's class
   * @param expectedCategories the expected categories, or none if it's not to be checked
   */
  public AbstractDictionaryBasedTripType(Class<T> clazz, TripTypeCategory... expectedCategories) {
    super(clazz, expectedCategories);

    TripSerializable tripSerializable = clazz.getAnnotation(TripSerializable.class);
    if (tripSerializable != null) {
      if (!tripSerializable.value()) {
        throw new TripRuntimeException(clazz + " is not serializable (annotated with @TripSerializable(false))");
      }
      String prepareMethodName = tripSerializable.prepare();
      if (prepareMethodName.isEmpty()) {
        prepareMethod = null;
      }
      else {
        try {
          prepareMethod = clazz.getMethod(prepareMethodName);
          if (prepareMethod.getReturnType() != Void.TYPE) {
            throw new TripRuntimeException("method " + prepareMethodName + " in " + clazz + " must return void");
          }
        }
        catch (NoSuchMethodException e) {
          throw new TripRuntimeException("cannot find method " + prepareMethodName + "() in " + clazz, e);
        }
      }
      String validateMethodName = tripSerializable.validate();
      if (validateMethodName.isEmpty()) {
        validateMethod = null;
      }
      else {
        try {
          validateMethod = clazz.getMethod(validateMethodName);
          if (validateMethod.getReturnType() != Void.TYPE) {
            throw new TripRuntimeException("method " + validateMethodName + " in " + clazz + " must return void");
          }
        }
        catch (NoSuchMethodException e) {
          throw new TripRuntimeException("cannot find method " + validateMethodName + "() in " + clazz, e);
        }
      }
    }
    else {
      prepareMethod = null;
      validateMethod = null;
    }

    if (!clazz.isInterface() && Remote.class.isAssignableFrom(clazz)) {
      // interfaces are allowed to be serialized by ClassTripType.
      // However, implementations must not be serialized!
      throw new TripRuntimeException(clazz + " is a remote object and must not be serialized");
    }

    TripDeduplicatable tripDeduplicatable = clazz.getAnnotation(TripDeduplicatable.class);
    if (tripDeduplicatable != null) {
      if (TripDeduplicatable.NEVER.equals(tripDeduplicatable.value())) {
        deduplicatable = false;
        deduplicatableMethod = null;
      }
      else if (TripDeduplicatable.ALWAYS.equals(tripDeduplicatable.value())) {
        deduplicatable = true;
        deduplicatableMethod = null;
      }
      else {
        deduplicatable = true;
        try {
          deduplicatableMethod = clazz.getMethod(tripDeduplicatable.value());
          if (deduplicatableMethod.getReturnType() != Boolean.TYPE) {
            throw new TripRuntimeException("method " + tripDeduplicatable.value() + " in " + clazz + " must return boolean");
          }
          if (!Modifier.isPublic(deduplicatableMethod.getModifiers())) {
            throw new TripRuntimeException("method " + tripDeduplicatable.value() + " in " + clazz + " must be public");
          }
        }
        catch (NoSuchMethodException e) {
          throw new TripRuntimeException("cannot find method " + tripDeduplicatable.value() + " in " + clazz, e);
        }
      }
    }
    else {
      deduplicatable = null;
      deduplicatableMethod = null;
    }

    TripReferencable tripReferencable = clazz.getAnnotation(TripReferencable.class);
    if (tripReferencable != null) {
      if (TripReferencable.NEVER.equals(tripReferencable.value())) {
        referencable = false;
        referencableMethod = null;
      }
      else if (TripReferencable.ALWAYS.equals(tripReferencable.value())) {
        referencable = true;
        referencableMethod = null;
      }
      else {
        referencable = true;
        try {
          referencableMethod = clazz.getMethod(tripReferencable.value());
          if (referencableMethod.getReturnType() != Boolean.TYPE) {
            throw new TripRuntimeException("method " + tripReferencable.value() + " in " + clazz + " must return boolean");
          }
          if (!Modifier.isPublic(referencableMethod.getModifiers())) {
            throw new TripRuntimeException("method " + tripReferencable.value() + " in " + clazz + " must be public");
          }
        }
        catch (NoSuchMethodException e) {
          throw new TripRuntimeException("cannot find method " + tripReferencable.value() + " in " + clazz, e);
        }
      }
    }
    else {
      referencable = null;
      referencableMethod = null;
    }
  }

  @Override
  public boolean isReferencable(T value) {
    if (referencableMethod == null) {
      // all objects are referencable, except when explicitly marked as NEVER
      return !Boolean.FALSE.equals(referencable);
    }
    try {
      return (Boolean) referencableMethod.invoke(value);
    }
    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new TripRuntimeException("cannot invoke referencable method", e);
    }
  }

  @Override
  public boolean isDictionaryBased() {
    return true;
  }

  /**
   * Gets the component provider.
   *
   * @return the component provider, null if none
   */
  public TripComponentProvider<T> getComponentProvider() {
    if (!componentProviderSet) {
      // Deliberately unlocked: the factory creates a new provider on each call anyway, so concurrent
      // threads may resolve it more than once. The providers are stateless and interchangeable,
      // and locking here would nest a type's lock inside the factory's recursive type creation.
      componentProvider = TripFactory.getInstance().getComponentProvider(getObjectClass());
      componentProviderSet = true;    // must be set last, see field declaration
    }
    return componentProvider;
  }

  /**
   * Gets the instance creator.
   *
   * @return the instance creator, null if none
   */
  public TripInstanceCreator<T> getInstanceCreator() {
    if (!instanceCreatorSet) {
      // deliberately unlocked, see getComponentProvider()
      instanceCreator = TripFactory.getInstance().getInstanceCreator(getObjectClass());
      instanceCreatorSet = true;    // must be set last, see field declaration
    }
    return instanceCreator;
  }


  /**
   * Prepares the object before serialization.
   *
   * @param value the object to prepare
   */
  protected void prepareWrite(T value) {
    if (prepareMethod != null) {
      try {
        prepareMethod.invoke(value);
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        throw new TripRuntimeException("cannot invoke method " + prepareMethod, e);
      }
    }
  }

  /**
   * Validates the object after deserialization.
   *
   * @param value the object to validate
   */
  protected void validateRead(T value) {
    if (validateMethod != null) {
      try {
        validateMethod.invoke(value);
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        throw new TripRuntimeException("cannot invoke method " + validateMethod, e);
      }
    }
  }

  /**
   * Creates an instance of the class with its no-args constructor.<br>
   * If a {@link TripInstanceCreator} is provided, it will be used instead.
   *
   * @return the instance, null if the class is abstract or an interface
   */
  @SuppressWarnings("unchecked")
  protected T createInitializedDefault() {
    if (isInstantiable()) {
      TripInstanceCreator<T> creator = getInstanceCreator();
      if (creator != null) {
        return creator.create(getObjectClass());
      }

      Constructor<?> constructor;
      try {
        constructor = getObjectClass().getDeclaredConstructor();
      }
      catch (NoSuchMethodException e) {
        if (ReflectionHelper.isNonStaticInnerClass(getObjectClass())) {
          // By default, serialization of non-static inner classes is disabled since they cannot exist on their own
          // and will always be serialized along with their enclosing object (and all its non-transient references
          // to other objects). This is probably not intended by the developer.
          // To enable it, annotate the constructor with @CanonicalConstructor or provide a @TripInstanceCreator.
          // This is necessary since we cannot (and don't want to) use
          // sun.reflect.ReflectionFactory.newConstructorForSerialization.
          // Important: even with serialization via TRIP, the inner class must implement Serializable!
          // Otherwise, the synthetic field "this$0" won't be created by the compiler.
          throw new TripInstantiationException(
            getObjectClass(), "non-static inner classes need a @CanonicalConstructor or @TripInstanceCreator", e);
        }
        throw new TripInstantiationException(getObjectClass(), "missing no-args constructor for " + getObjectClass(), e);
      }
      try {
        try {
          return (T) constructor.newInstance();
        }
        catch (IllegalAccessException ix) {
          constructor.setAccessible(true);
          return (T) constructor.newInstance();
        }
      }
      catch (InaccessibleObjectException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new TripInstantiationException(getObjectClass(), "cannot instantiate object", e);
      }
    }
    return null;
  }

  /**
   * Creates the components for this trip type.
   *
   * @param object the initialized object, null if none (delayed initialization)
   * @return the components
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected TripComponent<T, ?>[] createComponents(T object) {
    TripComponentProvider<T> provider = getComponentProvider();
    if (provider == null) {
      Collection<TripComponent<T, ?>> tripComponents = new ArrayList<>();
      int ordinal = 0;
      for (Field field : ReflectionHelper.getAllFields(getObjectClass(), null, true, null, true)) {
        if (isFieldSerializable(field)) {
          try {
            tripComponents.add(TripFactory.getInstance().createComponent(
              this, ordinal, field, object != null, object == null ? null : field.get(object)));
          }
          catch (IllegalAccessException e) {
            try {
              field.setAccessible(true);
              tripComponents.add(TripFactory.getInstance().createComponent(
                this, ordinal, field, true, field.get(object)));
            }
            catch (InaccessibleObjectException | IllegalAccessException ex) {
              throw new TripRuntimeException("cannot get default value for " + field, ex);
            }
          }
          ordinal++;
        }
      }
      return tripComponents.toArray(new TripComponent[0]);
    }
    return provider.getComponents(this);
  }


  /**
   * Checks whether the field is serializable.
   *
   * @param field the field to check
   * @return true if serializable, false otherwise
   */
  protected boolean isFieldSerializable(Field field) {
	  return !Modifier.isTransient(field.getModifiers()) &&
           !Modifier.isStatic(field.getModifiers()) &&
           !field.isAnnotationPresent(TripIgnore.class);
  }

}
