/*
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

package org.tentackle.misc;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A generic sink to accept some object.<br>
 * Useful in lambdas is some data has to be set within the enclosing method.
 *
 * @author harald
 * @param <T> the object type
 */
public class Sink<T> implements Consumer<T>, Supplier<T> {

  private T object;

  @Override
  public void accept(T t) {
    object = t;
  }

  @Override
  public T get() {
    return object;
  }

}
