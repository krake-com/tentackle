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
 *
 */

package org.tentackle.pdo.junit;

import org.junit.jupiter.api.Test;

import org.tentackle.session.ModificationTracker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Junit {@link AbstractPdoTest} without automatic transaction handling.
 */
public class NoTransactionTest extends AbstractPdoTest {

  /**
   * Creates the test.
   */
  public NoTransactionTest() {
    super(TransactionType.NONE);
  }

  @Test
  public void noTransaction() {
    assertNotNull(getDomainContext());
    assertFalse(getSession().isTxRunning());
  }

  @Test
  public void trackerRunning() {
    assertFalse(getSession().isTxRunning());
    assertTrue(ModificationTracker.getInstance().isAlive());
  }

}
