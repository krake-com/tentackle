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

import java.io.Serializable;

/**
 * An interceptable without any interceptors.<br>
 * Used to verify and measure the plain method invocation.
 *
 * @author harald
 */
public interface BenchInterceptable extends Interceptable, Serializable {

  /**
   * Adds two integers.
   *
   * @param op1 operand one
   * @param op2 operand two
   * @return the sum
   */
  int sum(int op1, int op2);

  /**
   * Gets the name.
   *
   * @return the name
   */
  String name();

  /**
   * Stores an object.
   *
   * @param object the object
   */
  void touch(Object object);

  /**
   * Gets the last touched object.
   *
   * @return the object
   */
  Object touched();

  /**
   * Always fails.
   *
   * @return never
   * @throws BenchException always
   */
  long fail();

  /**
   * Exception thrown by {@link #fail()}.
   */
  class BenchException extends RuntimeException {

    /**
     * Creates the exception.
     */
    public BenchException() {
      super("failed on purpose");
    }
  }

}
