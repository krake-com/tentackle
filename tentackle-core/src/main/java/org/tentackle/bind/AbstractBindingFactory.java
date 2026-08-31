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

import org.tentackle.log.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Base implementation of a binding factory.
 *
 * @author harald
 */
public class AbstractBindingFactory implements BindingFactory {

  private static final Logger LOGGER = Logger.get(AbstractBindingFactory.class);


  /**
   * Map of model classes to binding element classes
   */
  private final Map<Class<?>, Class<? extends BindableElement>> elementClassMap = new ConcurrentHashMap<>();

  // same but with evaluated inheritance
  private final Map<Class<?>, Class<? extends BindableElement>> allElementClassMap = new ConcurrentHashMap<>();


  private final BindableCache bindableCache;      // the bindable cache



  /**
   * Creates a binding factory.
   */
  public AbstractBindingFactory() {
    bindableCache = new DefaultBindableCache(this);
  }




  @Override
  public BindableCache getBindableCache() {
    return bindableCache;
  }


  @Override
  public Class<? extends BindableElement> putBindableElementClass(Class<?> modelClass,
                                                                  Class<? extends BindableElement> elementClass) {
    return elementClassMap.put(modelClass, elementClass);
  }


  @Override
  public Class<? extends BindableElement> getBindableElementClass(final Class<?> modelClass) {
    Class<? extends BindableElement> elementClass = allElementClassMap.get(modelClass);
    if (elementClass == null) {
      // not already evaluated
      Class<?> clazz = modelClass;
      while (clazz != null && (elementClass = elementClassMap.get(clazz)) == null) {
        // no mapping found, try superclass of model class
        clazz = clazz.getSuperclass();
      }
      // a value of BindableElement.class means: no mapping
      allElementClassMap.put(modelClass, elementClass == null ? BindableElement.class : elementClass);
    }
    else if (elementClass == BindableElement.class) {
      elementClass = null;    // no mapping
    }
    return elementClass;
  }


  @Override
  public BindingMember createBindingMember(Class<?> parentClass, BindingMember parentMember,
                                                  String memberName, String memberPath,
                                                  BindableElement element) {
    return new DefaultBindingMember(parentClass, parentMember, memberName, memberPath, element);
  }


  @Override
  public BindableElement createBindableElement(Class<?> modelClass, String camelName) {
    BindableElement element;
    Class<? extends BindableElement> elementClass = getBindableElementClass(modelClass);
    if (elementClass != null) {
      try {
        Constructor<? extends BindableElement> con = elementClass.getConstructor(String.class);
        element = con.newInstance(camelName);
      }
      catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
             InvocationTargetException ex) {
        throw new BindingException("could not instantiate bindable element " + elementClass.getName(), ex);
      }
    }
    else {
      // if all else fails: createBinding a default binding
      element = new DefaultBindableElement(camelName);
    }
    LOGGER.fine("created binding {0}", element);
    return element;
  }

}
