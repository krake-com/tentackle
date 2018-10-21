/*
 * Tentackle - https://tentackle.org.
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

import java.io.Serializable;
import org.tentackle.common.ServiceFactory;


interface InterceptableFactory$Singleton {
  InterceptableFactory INSTANCE = ServiceFactory.createService(
              InterceptableFactory.class, DefaultInterceptableFactory.class);
}


/**
 * Factory to create interceptable objects.
 *
 * @author harald
 */
public interface InterceptableFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static InterceptableFactory getInstance() {
    return InterceptableFactory$Singleton.INSTANCE;
  }

  /**
   * Creates an interceptable.
   *
   * @param <T> the object class
   * @param clazz the class of the interceptable, usually an interface
   * @return the created object
   */
  <T extends Interceptable & Serializable> T createInterceptable(Class<T> clazz);

}
