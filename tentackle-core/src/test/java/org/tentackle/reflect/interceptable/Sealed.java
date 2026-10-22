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

package org.tentackle.reflect.interceptable;

import org.tentackle.reflect.Interceptable;
import org.tentackle.reflect.NotInterceptable;

/**
 * Fixture for {@code NotInterceptableProcessorTest}: an {@link Interceptable} with one
 * method the framework would bypass the interceptor chain for, and one ordinary method.
 *
 * @author harald
 */
public interface Sealed extends Interceptable {

  /**
   * A method the framework invokes directly, bypassing interception.
   */
  @NotInterceptable
  void guarded();

  /**
   * An ordinary, interceptable method.
   */
  void open();

}
