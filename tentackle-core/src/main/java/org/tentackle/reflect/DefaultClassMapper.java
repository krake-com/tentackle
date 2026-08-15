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

import org.tentackle.common.Constants;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A default implementation of a class mapper.<br>
 * The map is either initialized from a map or a {@link Properties}-object which in turn
 * could be loaded from a properties file, an XML-file or constructed at runtime.
 *
 * @author harald
 */
public class DefaultClassMapper extends AbstractClassMapper<String> implements ClassMapper {

  private static final Logger LOGGER = Logger.get(DefaultClassMapper.class);

  private Class<?> defaultClass;                      // the optional default class
  private final ClassLoader classLoader;              // the classloader
  private final Map<String, Class<?>> classMap;       // the internal map to speed-up lookup
  private final Map<String, String> nameMap;          // the service name map
  private final Set<ClassMappedListener> listeners;   // mapping listeners
  private final String[] packageNames;                // the optional package names


  /**
   * Constructs a class mapper.
   *
   * @param name the mapper's name
   * @param classLoader the classloader
   * @param nameMap the mapping of classnames
   * @param packageNames the package names, null or empty array if none
   */
  public DefaultClassMapper(String name, ClassLoader classLoader, Map<String,String> nameMap, String[] packageNames) {
    super(name);

    this.classLoader = classLoader;
    this.packageNames = packageNames;
    this.nameMap = nameMap;

    classMap = new ConcurrentHashMap<>();
    listeners = ConcurrentHashMap.newKeySet();
  }

  /**
   * Constructs a class mapper.
   *
   * @param name the mapper's name
   * @param classLoader the classloader
   * @param nameProps the properties holding the mapping.
   * @param packageNames the package names, null or empty array if none
   */
  public DefaultClassMapper(String name, ClassLoader classLoader, Properties nameProps, String[] packageNames) {
    this(name, classLoader, new HashMap<>(), packageNames);
    for (String key: nameProps.stringPropertyNames()) {
      nameMap.put(key, nameProps.getProperty(key));
    }
  }


  /**
   * Creates a class mapper for a given mapped service name.
   *
   * @param name the mapper's name
   * @param mappedServiceName the mapped name
   */
  public DefaultClassMapper(String name, String mappedServiceName) {
    this(name,
         ServiceFactory.getClassLoader(Constants.MAPPED_SERVICE_PATH, mappedServiceName),
         ServiceFactory.getServiceFinder().createNameMap(mappedServiceName), null);
  }

  /**
   * Creates a class mapper for a given mapped service class.<br>
   * This is the most common usage of a class mapper.
   *
   * @param name the mapper's name
   * @param mappedClass the mapped service class
   */
  public DefaultClassMapper(String name, Class<?> mappedClass) {
    this(name, mappedClass.getCanonicalName());
  }


  @Override
  public Map<String,String> getNameMap() {
    return nameMap;
  }

  @Override
  public String[] getPackageNames() {
    return packageNames;
  }


  @Override
  public Class<?> getDefaultClass() {
    return defaultClass;
  }

  @Override
  public void setDefaultClass(Class<?> defaultClass) {
    this.defaultClass = defaultClass;
  }

  @Override
  public Map<String, String> getMap() {
    return Map.copyOf(nameMap);
  }

  /**
   * Maps a name to a class.
   *
   * @param name the class name
   * @param lenient true if try superclasses/interfaces
   * @return the mapped class, never null
   * @throws ClassNotFoundException if no mapping found
   */
  private Class<?> mapClass(String name, boolean lenient) throws ClassNotFoundException {
    Class<?> clazz = classMap.get(name);
    if (clazz == null) {    // no mapping so far
      String mappedName;
      try {
        mappedName = map(name, lenient);
      }
      catch (ClassNotFoundException cx) {
        // no mapping at all
        if (defaultClass != null) {
          LOGGER.fine("{0} -> using default {0}", cx.getMessage(), defaultClass);
          // map to default and remember
          classMap.put(name, defaultClass);
          return defaultClass;
        }
        else {
          throw cx;
        }
      }
      // mappedName resolved:
      clazz = Class.forName(mappedName, true, classLoader);    // may throw ClassNotFoundException!
      // found: remember mapping
      classMap.put(name, clazz);
      for (ClassMappedListener listener: listeners) {
        listener.classMapped(name, clazz);
      }
    }
    return clazz;
  }


  // ------------- implements ClassMapper --------------------------


  @Override
  public Class<?> map(Class<?> clazz) throws ClassNotFoundException {
    return map(clazz.getCanonicalName());
  }

  @Override
  public Class<?> map(String name) throws ClassNotFoundException {
    return mapClass(name, false);
  }

  @Override
  public Class<?> mapLenient(Class<?> clazz) throws ClassNotFoundException {
    return mapLenient(ReflectionHelper.getMappingName(clazz));
  }

  @Override
  public Class<?> mapLenient(String name) throws ClassNotFoundException {
    return mapClass(name, true);
  }

  @Override
  public void addListener(ClassMappedListener listener) {
    listeners.add(listener);
  }

  @Override
  public boolean removeListener(ClassMappedListener listener) {
    return listeners.remove(listener);
  }

  @Override
  public void removeAllListeners() {
    listeners.clear();
  }
}
