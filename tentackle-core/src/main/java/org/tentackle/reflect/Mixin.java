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


package org.tentackle.reflect;

import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mixin to implement dynamic proxies.
 * <p>
 * A mixin holds the delegate implementing one interface of a dynamic proxy.
 * The implementation class is determined via a {@link ClassMapper}. A mixin without an
 * implementation class (or whose implementation is a {@link DummyDelegate}) is a dummy mixin.
 *
 * @param <T> the type the mixin is mixed in
 * @param <M> the type of the mixin
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)    // only proxies are referenced, not the mixins
public class Mixin<T, M extends Serializable> implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Key to speed up lookup of constructors.
   */
  private record ConstructorKey(Class<?> implClass, Class<?>[] parameterTypes) {
    // see test/java/.../reflect/RecordTest.java

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ConstructorKey that = (ConstructorKey) o;
      if (implClass != that.implClass) {
        return false;
      }
      return Arrays.equals(parameterTypes, that.parameterTypes);
    }

    @Override
    public int hashCode() {
      int result = implClass != null ? implClass.hashCode() : 0;
      result = 31 * result + Arrays.hashCode(parameterTypes);
      return result;
    }
  }


  /**
   * Cache to speed up constructor lookup.
   */
  @SuppressWarnings("rawtypes")
  private static final Map<ConstructorKey, Constructor> CONSTRUCTOR_MAP = new ConcurrentHashMap<>();

  /**
   * Cache to speed up the decision whether a given class (name) implements the declClass.
   * The map is &lt;given-class&gt; =&gt; &lt;declClass&gt; =&gt; Boolean and thus can be used for all instances
   * of PdoMixin.
   */
  private static final Map<Class<?>,Map<Class<?>,Boolean>> MATCH_CACHE = new ConcurrentHashMap<>();


  private Class<M> implClass;      // the implementation class
  private Class<M> declClass;      // the declaring interface class
  private M delegate;              // the delegate implementation


  /**
   * Creates a mixin.
   *
   * @param mapper the class-mapper to find implementation classes, null if this is a dummy mixin
   * @param clazz the declaring interface class
   * @param superInterface the super interface class the implementation class must implement an extension of,
   *                       or null to use the clazz as the declaring class
   *
   * @throws ClassNotFoundException if no implementation found
   * @throws IllegalArgumentException if the implementation does not implement an extension of superInterface
   */
  @SuppressWarnings("unchecked")
  public Mixin(ClassMapper mapper, Class<T> clazz, Class<?> superInterface)
         throws ClassNotFoundException {

    Objects.requireNonNull(clazz, "clazz");

    if (mapper != null) {
      // get the implementation class via class mapper
      implClass = (Class<M>) mapper.mapLenient(clazz);

      // find the declaring interface
      if (superInterface != null) {
        for (Class<?> iFace: implClass.getInterfaces()) {
          if (superInterface.isAssignableFrom(iFace)) {
            declClass = (Class<M>) iFace;
            break;
          }
        }
        if (declClass == null) {
          throw new IllegalArgumentException("implementation of " + clazz.getName() +
                                             " does not implement an extension of " + superInterface.getName());
        }
      }
      else  {
        declClass = (Class<M>) clazz;   // T == M
      }
    }
    else  {
      declClass = (Class<M>) (superInterface == null ? clazz : superInterface);
    }
  }

  /**
   * Creates a dummy mixin.<br>
   * Dummy mixins just map nothing at all.
   *
   * @param clazz          the declaring interface class
   * @param superInterface the declaring class, or null to use the clazz as the declaring class
   * @throws ClassNotFoundException never thrown for dummy mixins
   */
  public Mixin(Class<T> clazz, Class<?> superInterface) throws ClassNotFoundException {
    this(null, clazz, superInterface);
  }

  /**
   * No-args constructor provided for TRIP.
   */
  public Mixin() {
  }


  /**
   * Returns whether this is a dummy mixin.
   *
   * @return true if dummy
   */
  public boolean isDummy() {
    return implClass == null || DummyDelegate.class.isAssignableFrom(implClass);
  }


  /**
   * Creates the delegate instance for the implementation class.<br>
   * The first public constructor whose parameter types are assignable to the given argument classes is used.
   * Since delegates may be found in superclasses, we need some expensive isAssignableFrom checking.
   * However, the constructors found are cached by a map, which speeds up again.<br>
   * Does nothing for dummy mixins without an implementation class.
   *
   * @param constructorArgumentClasses the constructor argument classes
   * @param constructorArguments the constructor arguments
   *
   * @throws NoSuchMethodException if no matching constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void createDelegate(Class<?>[] constructorArgumentClasses, Object[] constructorArguments)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    // notice: we're not using method handles since invokeWithArguments is much slower than invoking a constructor.
    // don't know why, but that's the observation.
    if (implClass != null) {
      ConstructorKey key = new ConstructorKey(implClass, constructorArgumentClasses);
      Constructor constructor = CONSTRUCTOR_MAP.get(key);   // no computeIfAbsent cause of exception below
      if (constructor == null) {
        // find the constructor and create the object
        Constructor<?>[] cons = implClass.getConstructors();
        for (Constructor<?> con : cons) {
          Class<?>[] params = con.getParameterTypes();
          if (params.length == constructorArgumentClasses.length) {
            boolean allParmsMatch = true;
            for (int i = 0; i < params.length; i++) {
              if (!constructorArgumentClasses[i].isAssignableFrom(params[i])) {
                allParmsMatch = false;
                break;
              }
            }
            if (allParmsMatch) {
              constructor = con;
              break;
            }
          }
        }
        if (constructor == null) {
          StringBuilder paramsAsString = new StringBuilder();
          for (Class<?> paramClass : constructorArgumentClasses) {
            if (!paramsAsString.isEmpty()) {
              paramsAsString.append(", ");
            }
            paramsAsString.append(paramClass.getName());
          }
          throw new NoSuchMethodException("no matching constructor found for " +
                                          implClass.getName() + "(" + paramsAsString + ")");
        }
        CONSTRUCTOR_MAP.put(key, constructor);
      }

      delegate = (M) constructor.newInstance(constructorArguments);
    }
  }


  /**
   * Sets the delegate.
   *
   * @param delegate the implementation instance
   */
  public void setDelegate(M delegate) {
    this.delegate = delegate;
  }


  /**
   * Gets the delegate object.
   *
   * @return the instance of the declaring class, null if not created yet or dummy mixin
   */
  public M getDelegate() {
    return delegate;
  }


  /**
   * Gets the declaring class (an interface).
   * @return the interface class
   */
  public Class<M> getDeclaringClass() {
    return declClass;
  }


  /**
   * Gets the implementing class.
   *
   * @return the class, null for dummy
   */
  public Class<M> getImplementingClass() {
    return implClass;
  }


  /**
   * Determines whether a given interface class extends the declaring class of this mixin
   * or any of its super interfaces.
   *
   * @param clazz the class to test
   * @return true if clazz belongs to this mixin
   */
  public boolean matches(Class<?> clazz) {
    // we cannot simply clazz.isAssignableFrom(implClass) because the implementation
    // could theoretically extend classes that should not be treated as "implemented".
    // Hence, we must check the interfaces.
    Map<Class<?>,Boolean> clazzMap = MATCH_CACHE.get(clazz);
    Boolean m = null;
    if (clazzMap == null) {
      clazzMap = new ConcurrentHashMap<>();
      MATCH_CACHE.put(clazz, clazzMap);
    }
    else {
      m = clazzMap.get(declClass);
    }
    if (m == null) {
      m = matchInterface(clazz, declClass);
      clazzMap.put(declClass, m);
    }
    return m;
  }


  private boolean matchInterface(Class<?> clazz, Class<?> iFace) {
    if (clazz.equals(iFace)) {
      return true;
    }
    // try super interfaces
    for (Class<?> superInterface: iFace.getInterfaces()) {
      if (matchInterface(clazz, superInterface)) {
        return true;
      }
    }
    return false;
  }

}
