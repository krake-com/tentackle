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

/**
 * Maps classes to other classes.
 *
 * @author harald
 */
public interface ClassMapper {

  /**
   * Creates a class mapper for a given mapped service.<br>
   * The mapper maintains a map of implementation classes to other classes (usually interfaces).
   *
   * @param name the mapper's name
   * @param mappedClass class of the mapped service
   * @return the classmapper
   * @see org.tentackle.common.MappedService
   */
  static ClassMapper create(String name, Class<?> mappedClass) {
    return ClassMapperFactory.getInstance().create(name, mappedClass);
  }

  /**
   * Maps a class or interface to another class or interface.
   *
   * @param clazz the class to map.
   * @return the mapped class, never null.
   * @throws ClassNotFoundException if there is no mapping.
   */
  Class<?> map(Class<?> clazz) throws ClassNotFoundException;


  /**
   * Maps a class or interface to another class or interface by its name.
   *
   * @param className the name or basename of the class to map.
   * @return the mapped class, never null.
   * @throws ClassNotFoundException if there is no mapping.
   */
  Class<?> map(String className) throws ClassNotFoundException;


  /**
   * Maps a class or interface to another class or interface.<br>
   * If there is no mapping for {@code clazz} its superclasses are tried.
   * If {@code clazz} is an interface, all its super interfaces are tried.
   * <p>
   * Notice: for classes their interfaces will be ignored.
   *
   * @param clazz the class to map.
   * @return the mapped class, never null.
   * @throws ClassNotFoundException if there is no mapping.
   */
  Class<?> mapLenient(Class<?> clazz) throws ClassNotFoundException;


  /**
   * Maps a class or interface to another class or interface by its name.
   *
   * @param className the name or basename of the class to map.
   * @return the mapped class, never null.
   * @throws ClassNotFoundException if there is no mapping.
   */
  Class<?> mapLenient(String className) throws ClassNotFoundException;

  /**
   * Gets the default class.
   *
   * @return the class, null if no default
   */
  Class<?> getDefaultClass();

  /**
   * Sets the default class.<br>
   * If there is no matching class, the mapper returns this class.
   *
   * @param defaultClass the default class
   */
  void setDefaultClass(Class<?> defaultClass);

}
