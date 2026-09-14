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

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.tentackle.ns.pdo.NumberPool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Junit {@link AbstractPdoTest} with one transaction per class.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClassTransactionTest extends AbstractPdoTest {

  private long poolId;    // the pool persisted by the first method

  @Test
  @Order(1)
  public void persistInClassTransaction() {
    assertNotNull(getSession());
    assertTrue(getSession().isTxRunning());
    NumberPool pool = on(NumberPool.class);
    pool.setName("junit-class");
    pool.setRealm("junit");
    pool = pool.persist();
    poolId = pool.getId();
    assertTrue(poolId != 0);
  }

  @Test
  @Order(2)
  public void stillVisibleInSameTransaction() {
    assertTrue(getSession().isTxRunning());
    NumberPool pool = on(NumberPool.class).select(poolId);
    assertNotNull(pool);
    assertEquals("junit-class", pool.getName());
  }

}
