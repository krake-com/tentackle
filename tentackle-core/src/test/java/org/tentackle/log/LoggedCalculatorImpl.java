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

import org.tentackle.reflect.InterceptionService;

import java.io.Serial;

/**
 * The implementation of the {@link LoggedCalculator}.
 *
 * @author harald
 */
@InterceptionService(LoggedCalculator.class)
public class LoggedCalculatorImpl implements LoggedCalculator {

  /** the exception thrown by {@link #boom()}. */
  public static final String BOOM_MESSAGE = "boom";

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates the implementation.
   *
   * @param calc the interceptable
   */
  public LoggedCalculatorImpl(LoggedCalculator calc) {
    // the interceptable is of no interest for this fixture
  }

  @Override
  public int add(int op1, int op2) {
    return op1 + op2;
  }

  @Override
  public int multiply(int op1, int op2) {
    return op1 * op2;
  }

  @Override
  public int boom() {
    throw new IllegalStateException(BOOM_MESSAGE);
  }

  @Override
  public int subtract(int op1, int op2) {
    return op1 - op2;
  }

}
