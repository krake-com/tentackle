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
import java.util.Collection;
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
   * The key for the class cache map.
   */
  private record ClassKey(Class<?> clazz, boolean declaredOnly) {}


  /**
   * Element key to sort by ordinal + camelName.
   */
  private record ElementKey(String camelName, int ordinal) implements Comparable<ElementKey> {

    @Override
    public int compareTo(ElementKey o) {
      int rv = Integer.compare(ordinal, o.ordinal);
      if (rv == 0) {
        rv = camelName.compareTo(o.camelName);
      }
      return rv;
    }
  }

  /**
   * The binding factory.
   */
  private final BindingFactory bindingFactory;

  /**
   * The cached list of bindables by key.
   * <p>
   * The map is sorted by the camelcase name of the bindable element.
   */
  private final Map<ClassKey, SortedMap<ElementKey, BindableElement>> clazzMap = new ConcurrentHashMap<>();


  /**
   * Creates a bindable cache.
   *
   * @param bindingFactory the binding factory to create binding elements
   */
  public DefaultBindableCache(BindingFactory bindingFactory) {
    this.bindingFactory = bindingFactory;
  }


  /**
   * Gets the map of bindables for the given class.
   *
   * @param clazz the class to retrieve the bindables for
   * @param declaredOnly true if only declared elements, else inherited as well
   * @return the elements
   */
  @Override
  public Collection<BindableElement> getBindableMap(Class<?> clazz, boolean declaredOnly) {

    ClassKey key = new ClassKey(clazz, declaredOnly);
    SortedMap<ElementKey, BindableElement> bindableMap = clazzMap.get(key);

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
              ElementKey elementKey = new ElementKey(camelName, annotation.ordinal());
              LOGGER.finer("bindable method {0} found", method);
              BindableElement oldInfo = bindableMap.get(elementKey);
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
                element.setOrdinal(annotation.ordinal());
                bindableMap.put(elementKey, element);
              }
            }
          }
        }
      }

      if (clazz.isRecord()) {
        /*
         * In records the "from"- or "with"-methods are usually generated by the DTO wurblet.
         * Since it is up to the developer to annotate the record components right within the declaration or
         * to override a "getter" and annotate that, the DTO wurblet cannot determine the annotations and thus
         * will not annotate the generated methods.
         * Hence, we simply treat a from-method as bindable if the record component's getter was detected as bindable somehow.
         */
        for (BindableElement element : bindableMap.values()) {
          if (element.getFrom() == null && element.getField() == null && element.getSetter() == null && element.getGetter() != null) {
            String methodTail = StringHelper.firstToUpper(element.getGetter().getName());
            String fromName = "from" + methodTail;
            try {
              element.setFrom(element.getGetter().getDeclaringClass().getDeclaredMethod(fromName, element.getGetter().getReturnType()));
            }
            catch (NoSuchMethodException e1) {
              // no "from"-method implemented: try "with"-method
              fromName = "with" + methodTail;
              try {
                element.setFrom(element.getGetter().getDeclaringClass().getDeclaredMethod(fromName, element.getGetter().getReturnType()));
              }
              catch (NoSuchMethodException e2) {
                // neither from- nor with-method implemented
              }
            }
          }
        }
      }
      else {
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
            ElementKey elementKey = new ElementKey(camelName, annotation.ordinal());
            BindableElement element = bindableMap.get(elementKey);
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
              element.setOrdinal(annotation.ordinal());
              bindableMap.put(elementKey, element);
            }
          }
        }
      }

      // cache it!
      clazzMap.put(key, bindableMap);
    }

    return bindableMap.values();
  }

}
