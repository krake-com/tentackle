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

import java.util.Map;

/**
 * Maps names or classes to classes.
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
   * @param clazz the class to map
   * @return the mapped class, never null
   * @throws ClassNotFoundException if there is no mapping
   */
  Class<?> map(Class<?> clazz) throws ClassNotFoundException;


  /**
   * Maps a class or interface to another class or interface by its name.
   *
   * @param name the name or basename of the class to map
   * @return the mapped class, never null
   * @throws ClassNotFoundException if there is no mapping
   */
  Class<?> map(String name) throws ClassNotFoundException;


  /**
   * Maps a class or interface to another class or interface.<br>
   * If there is no mapping for {@code clazz}, its superclasses are tried.
   * If none of them is mapped, the interfaces of {@code clazz} and its superclasses
   * (including their super interfaces) are tried.
   *
   * @param clazz the class to map
   * @return the mapped class, never null
   * @throws ClassNotFoundException if there is no mapping
   */
  Class<?> mapLenient(Class<?> clazz) throws ClassNotFoundException;


  /**
   * Maps a name to a class.<br>
   * Same as {@link #mapLenient(Class)}, but for a class name.
   *
   * @param name the name to map
   * @return the mapped class, never null
   * @throws ClassNotFoundException if there is no mapping
   */
  Class<?> mapLenient(String name) throws ClassNotFoundException;

  /**
   * Gets the default class.<br>
   * Returned if there is no mapping.
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

  /**
   * Gets a copy of the names map.
   *
   * @return the unmodifiable map of names to mapped names
   */
  Map<String, String> getMap();

  /**
   * Adds a class mapped listener.
   *
   * @param listener invoked whenever a class is mapped the first time
   */
  void addListener(ClassMappedListener listener);

  /**
   * Removes a class mapped listener.
   *
   * @param listener the listener
   * @return true if removed, false if no such listener
   */
  boolean removeListener(ClassMappedListener listener);

  /**
   * Removes all class mapped listeners.
   */
  void removeAllListeners();

}
