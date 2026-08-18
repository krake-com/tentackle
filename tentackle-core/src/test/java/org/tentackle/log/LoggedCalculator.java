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

package org.tentackle.log;

import org.tentackle.log.Logger.Level;
import org.tentackle.reflect.Interceptable;

import java.io.Serializable;

/**
 * An interceptable exercising the {@link Log} interception end-to-end.
 *
 * @author harald
 */
public interface LoggedCalculator extends Interceptable, Serializable {

  /**
   * Adds two integers, logged with {@link Level#FINE}.
   *
   * @param op1 operand one
   * @param op2 operand two
   * @return the sum
   */
  @Log(Level.FINE)
  int add(int op1, int op2);

  /**
   * Multiplies two integers, logged with the default level.
   *
   * @param op1 operand one
   * @param op2 operand two
   * @return the product
   */
  @Log
  int multiply(int op1, int op2);

  /**
   * Always throws, logged with {@link Level#FINE}.
   *
   * @return never returns
   */
  @Log(Level.FINE)
  int boom();

  /**
   * Subtracts two integers, not intercepted at all.
   *
   * @param op1 operand one
   * @param op2 operand two
   * @return the difference
   */
  int subtract(int op1, int op2);

}
