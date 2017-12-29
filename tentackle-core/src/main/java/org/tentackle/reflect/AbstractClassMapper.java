/**
 * Tentackle - http://www.tentackle.org
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;

/**
 * A generic class mapper.<br>
 * Implements lenient (superclass/interface) and package (class basenames) lookup.
 *
 * @author harald
 * @param <S> the service type mapped to
 */
public abstract class AbstractClassMapper<S> {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractClassMapper.class);


  /**
   * already resolved mappings.
   */
  private final ConcurrentHashMap<String,S> classMap = new ConcurrentHashMap<>();

  /**
   * the mapper name.
   */
  private final String name;

  /**
   * Creates a classmapper.
   *
   * @param name the mapper's name
   */
  public AbstractClassMapper(String name) {
    this.name = name;
  }


  /**
   * Gets the list of package names.
   *
   * @return the list of package names, null or empty array if none.
   */
  abstract public String[] getPackageNames();


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
   * Gets the mapping of service names.<br>
   * The key is name of the service to map and the value is the name of the mapped object.
   *
   * @return the names map, never null.
   */
  public abstract Map<String,S> getNameMap();


  /**
   * Maps all super interfaces of an interface class.
   *
   * @param clazz the interface
   * @return the mapped result, null if no mapping found
   */
  private S mapInterfaces(Class<?> clazz) {
    // check all super interfaces
    S result = null;
    for (Class<?> interfaze: clazz.getInterfaces()) {
      result = getNameMap().get(interfaze.getName());
      if (result != null) {
        // mapping found
        break;
      }
      else {
        // no mapping: recursively check superinterface
        result = mapInterfaces(interfaze);
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
      Class<?> clazz = null;             // class corresponding to initial className, null if not loaded yet
      boolean isInterface = false;    // true if className corresponds to an interface
      while (className != null) {
        S value = getNameMap().get(className);
        if (value != null) {
          return value;
        }
        // no explicit mapping: try superclass/interfaces
        if (clazz == null) {
          // determine clazz once and check if interface
          clazz = Class.forName(className);
          isInterface = clazz.isInterface();
        }
        if (isInterface) {
          // check all super interfaces (recursively)
          return mapInterfaces(clazz);
        }
        else {
          clazz = clazz.getSuperclass();
          if (clazz == null) {
            break;
          }
          className = clazz.getName();
          // continue with name of superclass
        }
      }
      return null;    // no mapping
    }
    else  {
      // exact match required
      return getNameMap().get(className);
    }
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
    if (className.indexOf('.') < 0) {
      String packageNames[] = getPackageNames();
      if (packageNames != null) {
        for (String packageName: packageNames) {
          S result = mapName(packageName + "." + className, lenient);
          if (result != null)  {
            return result;
          }
        }
      }
      return null;    // no such class in all packages
    }
    else  {
      return mapName(className, lenient);
    }
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
    S mappedClazz = classMap.get(className);
    if (mappedClazz == null) {
      mappedClazz = mapNameWithPackages(className, lenient);
      if (mappedClazz == null) {
        throw new ClassNotFoundException("no " + name + " mapping for '" + className +
                                         (lenient ? "' in packages " + getPackageNamesAsString() : "'"));
      }
      // this is ok unsynchronized cause of ConcurrentHashMap.
      // putting a key/value pair more than once in a multi-threaded application is ok as well.
      if (classMap.put(className, mappedClazz) == null) {
        // we log only if really added. This is because a class may be added twice
        // if mapNameWithPackages instantiates a class that itself invokes mapping again
        LOGGER.info("{0}: added {1} -> {2}", name, className, mappedClazz.toString());
      }
    }
    LOGGER.fine("{0}: mapped {1} -> {2}", name, className, mappedClazz.toString());
    return mappedClazz;
  }



  /**
   * Gets the packages names as a String as follows:
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
