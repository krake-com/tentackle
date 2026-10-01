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

package org.tentackle.trip;

import org.tentackle.common.ServiceFactory;


interface TripTypeBuilderFactoryHolder {
  TripTypeBuilderFactory INSTANCE = ServiceFactory.createService(TripTypeBuilderFactory.class);
}


/**
 * A factory for {@link TripTypeBuilder}s.
 * <p>
 * Picks the builder annotated with &#64;{@link TripTypeBuilderService} for the requested class, its superclasses
 * or interfaces (see {@link org.tentackle.reflect.ClassMapper#mapLenient(Class)}).
 * Replaceable via &#64;{@link org.tentackle.common.Service}.
 *
 * @author harald
 */
public interface TripTypeBuilderFactory {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static TripTypeBuilderFactory getInstance() {
    return TripTypeBuilderFactoryHolder.INSTANCE;
  }

  /**
   * Gets a type builder for the requested data type.
   *
   * @param <T> the data type
   * @param clazz the application's data class
   * @return the type builder
   */
  <T> TripTypeBuilder<T> getBuilder(Class<T> clazz);

}
