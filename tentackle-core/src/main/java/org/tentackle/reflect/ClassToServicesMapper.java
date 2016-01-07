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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Maps a class or interface to a list of service classes.<br>
 * Used for mapped services to collect all services in the classpath, not only the first.
 *
 * @author harald
 * @param <T> the type mapped to
 */
public class ClassToServicesMapper<T> extends AbstractClassMapper<Set<String>> {

  private final Map<String,Set<Class<T>>> classMap;   // the internal map to speed-up lookup
  private final Map<String,Set<String>> nameMap;      // the service name map
  private final String[] packageNames;                // the optional package names


  /**
   * Constructs a mapper.
   *
   * @param name the mapper's name
   * @param nameMap the mapping of classnames
   * @param packageNames the package names, null or empty array if none
   */
  public ClassToServicesMapper(String name, Map<String,Set<String>> nameMap, String packageNames[]) {
    super(name);
    this.packageNames = packageNames;
    this.nameMap = nameMap;
    classMap = new ConcurrentHashMap<>();
  }


  @Override
  public Map<String,Set<String>> getNameMap() {
    return nameMap;
  }

  @Override
  public String[] getPackageNames() {
    return packageNames;
  }


  /**
   * Maps to the classes.
   *
   * @param className the class name
   * @param lenient true if try superclasses/interfaces
   * @return the mapped classes, never null
   * @throws ClassNotFoundException if no mapping or some mapped classname not found
   */
  @SuppressWarnings("unchecked")
  public Set<Class<T>> mapClass(String className, boolean lenient) throws ClassNotFoundException {
    Set<Class<T>> clazzes = classMap.get(className);
    if (clazzes == null) {    // no mapping so far
      Set<String> mappedNames;
      mappedNames = map(className, lenient);
      clazzes = new HashSet<>();
      for (String name: mappedNames) {
        clazzes.add((Class<T>) Class.forName(name));
      }
      classMap.put(className, clazzes);
    }
    return clazzes;
  }

}
