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

package org.tentackle.reflect.interceptabletest;

import org.tentackle.reflect.Interceptable;

/**
 * This is the interface describing the class to be handled.
 *
 * @author harald
 */
public interface Calculator extends Interceptable {

  /**
   * Adds two integers.
   *
   * @param op1 operand one
   * @param op2 operand two
   * @return the result
   */
  @LogIt
  @Offset(100)
  int add(int op1, int op2);

}
