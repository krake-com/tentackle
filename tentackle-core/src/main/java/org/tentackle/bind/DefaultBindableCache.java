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


package org.tentackle.bind;

import org.tentackle.common.StringHelper;
import org.tentackle.log.Logger;
import org.tentackle.reflect.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for bindable elements per class.<br>
 *
 * @author harald
 */
public class DefaultBindableCache implements BindableCache {

  private static final Logger LOGGER = Logger.get(DefaultBindableCache.class);


  /**
   * The binding factory.
   */
  private final BindingFactory bindingFactory;

  /**
   * The cached list of bindables by key.
   * <p>
   * The map is sorted by the camelcase name of the bindable element.
   */
  private final Map<Key,SortedMap<String,BindableElement>> clazzMap = new ConcurrentHashMap<>();


  /**
   * Creates a bindable cache.
   *
   * @param bindingFactory the binding factory to create binding elements
   */
  public DefaultBindableCache(BindingFactory bindingFactory) {
    this.bindingFactory = bindingFactory;
  }


  /**
   * Gets the map of bindables for given class.
   *
   * @param clazz the class to retrieve the bindables for
   * @param declaredOnly true if only declared elements, else inherited as well
   * @return the tree map (camelName -&gt; bindable element).
   */
  @Override
  public SortedMap<String,BindableElement> getBindableMap(Class<?> clazz, boolean declaredOnly) {

    Key key = createKey(clazz, declaredOnly);
    SortedMap<String,BindableElement> bindableMap = clazzMap.get(key);

    if (bindableMap == null) {

      // parse once

      bindableMap = new TreeMap<>();

      Method[] methods;
      if (declaredOnly) {
        if (clazz.isInterface()) {
          methods = clazz.getMethods();
        }
        else  {
          methods = clazz.getDeclaredMethods();
        }
      }
      else  {
        methods = ReflectionHelper.getAllMethods(clazz, null, true, null, true);
      }

      // find all declared methods with the @Bindable annotation
      for (Method method : methods) {
        if (!method.isBridge()) {
          Bindable annotation = method.getAnnotation(Bindable.class);
          if (annotation != null) {
            BindableElement element = null;
            String bindingOptions = annotation.options();

            String camelName = ReflectionHelper.getGetterPathName(method);
            if (camelName != null) {
              element = bindingFactory.createBindableElement(method.getReturnType(), camelName);
              element.setGetter(method);
            }
            else {
              camelName = ReflectionHelper.getSetterPathName(method);
              if (camelName != null) {
                element = bindingFactory.createBindableElement(method.getParameterTypes()[0], camelName);
                element.setSetter(method);
              }
              else {
                camelName = ReflectionHelper.getFromPathName(method);
                if (camelName != null) {
                  element = bindingFactory.createBindableElement(method.getParameterTypes()[0], camelName);
                  element.setFrom(method);
                }
              }
            }

            if (element != null) {
              camelName = StringHelper.firstToUpper(camelName);
              LOGGER.finer("bindable method {0} found", method);
              BindableElement oldInfo = bindableMap.get(camelName);
              if (oldInfo != null) {
                // merge with existing info
                oldInfo.addBindingOptions(bindingOptions);
                if (element.getGetter() != null) {
                  if (oldInfo.getGetter() == null) {
                    oldInfo.setGetter(element.getGetter());
                  }
                  // else: keep @overridden covariant method, if !declaredOnly
                }
                else if (element.getSetter() != null) {
                  if (oldInfo.getSetter() != null) {
                    throw new BindingException("more than one setters annotated with @Bindable in "
                                               + clazz.getName() + " for " + camelName + ": " +
                                               element.getSetter().getName() + " <> " + oldInfo.getSetter().getName());
                  }
                  oldInfo.setSetter(element.getSetter());
                }
                else if (element.getFrom() != null) {
                  if (oldInfo.getFrom() != null) {
                    throw new BindingException("more than one 'from' methods annotated with @Bindable in "
                                               + clazz.getName() + " for " + camelName + ": " +
                                               element.getFrom().getName() + " <> " + oldInfo.getFrom().getName());
                  }
                  oldInfo.setFrom(element.getFrom());
                }
              }
              else {
                // new binding
                element.setBindingOptions(bindingOptions);
                bindableMap.put(camelName, element);
              }
            }
          }
        }
      }

      // get all fields with the bindable annotation
      for (Field field :
              (declaredOnly
              ? clazz.getDeclaredFields()
              : ReflectionHelper.getAllFields(clazz, null, true, null, true))) {
        Bindable annotation = field.getAnnotation(Bindable.class);
        if (annotation != null) {
          LOGGER.finer("bindable field {0} found", field);
          String bindingOptions = annotation.options();
          String camelName = StringHelper.firstToUpper(field.getName());
          BindableElement element = bindableMap.get(camelName);
          if (element != null) {
            // merge
            element.setField(field);
            element.addBindingOptions(bindingOptions);
          }
          else {
            // new
            element = bindingFactory.createBindableElement(field.getType(), camelName);
            element.setField(field);
            element.setBindingOptions(bindingOptions);
            bindableMap.put(camelName, element);
          }
        }
      }

      // cache it!
      clazzMap.put(key, bindableMap);
    }

    return bindableMap;
  }


  /**
   * The key for the cache map.
   */
  private static class Key {

    private final Class<?> clazz;
    private final boolean declaredOnly;

    private Key(Class<?> clazz, boolean declaredOnly) {
      this.clazz = clazz;
      this.declaredOnly = declaredOnly;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final Key other = (Key) obj;
      if (this.clazz != other.clazz && (this.clazz == null || !this.clazz.equals(other.clazz))) {
        return false;
      }
      return this.declaredOnly == other.declaredOnly;
    }

    @Override
    public int hashCode() {
      int hash = 3;
      hash = 29 * hash + (this.clazz != null ? this.clazz.hashCode() : 0);
      hash = 29 * hash + (this.declaredOnly ? 1 : 0);
      return hash;
    }

  }


  /**
   * Create a cache key.
   *
   * @param clazz the class to inspect
   * @param declaredOnly true if only declared elements, else inherited as well
   * @return the created key
   */
  private Key createKey(Class<?> clazz, boolean declaredOnly) {
    return new Key(clazz, declaredOnly);
  }

}
