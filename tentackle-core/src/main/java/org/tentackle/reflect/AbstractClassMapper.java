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

import org.tentackle.log.Logger;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base implementation for class mappers.<br>
 * Implements lenient (superclass/interface) and package (class base names) lookup.
 *
 * @author harald
 * @param <S> the service type mapped to
 */
public abstract class AbstractClassMapper<S> {

  private static final Logger LOGGER = Logger.get(AbstractClassMapper.class);


  /**
   * already resolved mappings.
   */
  private final Map<String,S> classMap = new ConcurrentHashMap<>();

  /**
   * the mapper name.
   */
  private final String name;

  /**
   * the log level.
   */
  private Logger.Level logLevel = Logger.Level.FINE;


  /**
   * Creates a class mapper.
   *
   * @param name the mapper's name
   */
  public AbstractClassMapper(String name) {
    this.name = name;
  }


  /**
   * Gets the list of package names.
   *
   * @return the array of package names, null or empty array if none.
   */
  public abstract String[] getPackageNames();

  /**
   * Gets the mapping of service names.<br>
   * The key is the name of the service to map, and the value is the name of the mapped object.
   *
   * @return the names-map, never null.
   */
  public abstract Map<String,S> getNameMap();


  /**
   * Gets the mapper's name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return getName();
  }

  /**
   * Sets the log level.
   *
   * @param logLevel the log level
   */
  public void setLogLevel(Logger.Level logLevel) {
    this.logLevel = logLevel;
  }

  /**
   * Gets the log level.<br>
   * The default is {@link Logger.Level#FINE}.
   *
   * @return the log level
   */
  public Logger.Level getLogLevel() {
    return logLevel;
  }

  /**
   * Maps with concurrent hashmap cache.
   *
   * @param className the name of the class to map
   * @param lenient true if try superclasses/interfaces
   * @return the mapped object, never null
   * @throws ClassNotFoundException if no mapping found for {@code className}
   */
  public S map(String className, boolean lenient) throws ClassNotFoundException {
    S mappedClazz = classMap.get(className);    // no computeIfAbsent cause of CNF ex below
    if (mappedClazz == null) {
      mappedClazz = mapNameWithPackages(className, lenient);
      if (mappedClazz == null) {
        throw new ClassNotFoundException("no " + name + " mapping for '" + className +
                                         (lenient ? "' in packages " + getPackageNamesAsString() : "'"));
      }
      // not synchronized is ok cause of ConcurrentHashMap.
      // putting a key/value pair more than once in a multithreaded application is ok as well.
      if (classMap.put(className, mappedClazz) == null) {
        // we log only if really added. This is because a class may be added twice
        // if mapNameWithPackages instantiates a class that itself invokes mapping again
        LOGGER.log(logLevel, "{0}: added {1} -> {2}", name, className, mappedClazz);
      }
    }
    return mappedClazz;
  }


  /**
   * Maps all super interfaces of an interface class.
   *
   * @param clazz the interface
   * @return the mapped result, null if no mapping found
   */
  private S mapInterfaces(Class<?> clazz) {
    // check all super interfaces
    S result = null;
    for (Class<?> iFace: clazz.getInterfaces()) {
      result = getNameMap().get(iFace.getName());
      if (result != null) {
        // mapping found
        break;
      }
      else {
        // no mapping: recursively check superinterface
        result = mapInterfaces(iFace);
        if (result != null) {
          break;
        }
      }
    }
    return result;
  }

  /**
   * Maps a classname.
   *
   * @param className the name of the class to map
   * @param lenient true if try superclasses/interfaces
   * @return the mapped class, null if no mapping found
   * @throws java.lang.ClassNotFoundException if {@code className} is not a valid classname
   */
  private S mapName(String className, boolean lenient) throws ClassNotFoundException {
    if (lenient) {
      Class<?> initialClass = null;   // class corresponding to initial className, null if not loaded yet
      Class<?> superClass = null;     // current superclass

      while (className != null) {
        S value = getNameMap().get(className);
        if (value != null) {
          return value;   // explicit mapping found!
        }
        // no explicit mapping: try superclass/interfaces
        if (initialClass == null) {
          initialClass = superClass = findClass(className);
        }
        superClass = findSuperClass(superClass);
        if (superClass == null) {   // go up to and including Object.class!
          break;    // is an interface or record, or we reached Object.class
        }
        // continue with the name of superclass
        className = ReflectionHelper.getMappingName(superClass);
      }
      // no mapping found for class and all superclasses: try interfaces
      while (initialClass != null && initialClass != Object.class) {
        S value = mapInterfaces(initialClass);
        if (value != null) {
          return value;
        }
        initialClass = initialClass.getSuperclass();
      }
      return null;
    }
    else  {
      // exact match required
      return getNameMap().get(className);
    }
  }

  /**
   * Finds a class with support for array suffixes.
   *
   * @param className the class name
   * @return the class
   * @throws ClassNotFoundException if the class cannot be found
   */
  private Class<?> findClass(String className) throws ClassNotFoundException {
    int arrayNdx = className.indexOf('[');
    String arraySuffix = null;
    if (arrayNdx >= 0) {
      arraySuffix = className.substring(arrayNdx);
      className = className.substring(0, arrayNdx);
    }
    Class<?> clazz = Class.forName(className);
    if (arraySuffix != null) {
      int dimensions = arraySuffix.length() / 2;
      for (int i = 0; i < dimensions; i++) {
        clazz = Array.newInstance(clazz, 0).getClass();
      }
    }
    return clazz;
  }

  /**
   * Finds the superclass of a class with support for arrays.
   *
   * @param clazz the class
   * @return the superclass
   */
  private Class<?> findSuperClass(Class<?> clazz) {
    if (clazz.isArray()) {
      int dimensions = 1;
      Class<?> componentType = clazz.getComponentType();
      while (componentType.isArray()) {
        componentType = componentType.getComponentType();
        dimensions++;
      }
      clazz = componentType.getSuperclass();
      if (clazz != null) {
        for (int i = 0; i < dimensions; i++) {
          clazz = Array.newInstance(clazz, 0).getClass();
        }
      }
      return clazz;
    }
    return clazz.getSuperclass();
  }

  /**
   * Maps a classname and optionally applies package names.
   *
   * @param className the name of the class to map
   * @param lenient true if try superclasses/interfaces
   * @return the mapped class, null if no mapping found
   * @throws java.lang.ClassNotFoundException if {@code className} is not a valid classname
   */
  private S mapNameWithPackages(String className, boolean lenient) throws ClassNotFoundException {
    String[] packageNames = getPackageNames();
    if (packageNames != null && className.indexOf('.') < 0) {   // if not FQCN
      for (String packageName : packageNames) {
        S result = mapName(packageName + "." + className, lenient);
        if (result != null) {
          return result;
        }
      }
      // try default (primitive type? "char[]"? ...)
    }
    return mapName(className, lenient);
  }

  /**
   * Gets the package names as a String as follows:
   * [package1, package2, ... ]
   *
   * @return the package names
   */
  private String getPackageNamesAsString() {
    StringBuilder buf = new StringBuilder("[");
    String[] packageNames = getPackageNames();
    if (packageNames != null) {
      boolean first = true;
      for (String packageName: packageNames) {
        if (first) {
          first = false;
        }
        else  {
          buf.append(", ");
        }
        buf.append(packageName);
      }
    }
    buf.append("]");
    return buf.toString();
  }

}
