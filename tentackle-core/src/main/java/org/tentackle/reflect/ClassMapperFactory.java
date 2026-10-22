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

import org.tentackle.common.ServiceFactory;


interface ClassMapperFactoryHolder {
  ClassMapperFactory INSTANCE = ServiceFactory.createService(ClassMapperFactory.class, DefaultClassMapperFactory.class);
}


/**
 * Factory for {@link ClassMapper}s.
 * <p>
 * Replaceable via &#64;{@link org.tentackle.common.Service}.
 * The default implementation is {@link DefaultClassMapperFactory}.
 *
 * @author harald
 */
public interface ClassMapperFactory {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static ClassMapperFactory getInstance() {
    return ClassMapperFactoryHolder.INSTANCE;
  }

  /**
   * Creates a class mapper for a given mapped service.<br>
   * The mapper maintains a map of implementation classes to other classes (usually interfaces).
   *
   * @param name the mapper's name
   * @param mappedClass class of the mapped service
   * @return the classmapper
   * @see org.tentackle.common.MappedService
   */
  ClassMapper create(String name, Class<?> mappedClass);

}
