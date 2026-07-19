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

package org.tentackle.dbms;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link DefaultReconnectionPolicy}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultReconnectionPolicyTest {

  @Test
  public void testBlockingPolicy() {
    DefaultReconnectionPolicy policy = new DefaultReconnectionPolicy(true, 1500);
    assertTrue(policy.isBlocking());
    assertEquals(policy.timeToReconnect(), 1500L);
  }

  @Test
  public void testNonBlockingPolicy() {
    DefaultReconnectionPolicy policy = new DefaultReconnectionPolicy(false, 250);
    assertFalse(policy.isBlocking());
    assertEquals(policy.timeToReconnect(), 250L);
  }

  @Test
  public void testTimeToReconnectIsConstant() {
    DefaultReconnectionPolicy policy = new DefaultReconnectionPolicy(false, 42);
    assertEquals(policy.timeToReconnect(), 42L);
    assertEquals(policy.timeToReconnect(), 42L, "fixed interval must not change between calls");
  }

  @Test
  public void testToStringContainsState() {
    String s = new DefaultReconnectionPolicy(true, 1500).toString();
    assertTrue(s.contains("blocking=true"), s);
    assertTrue(s.contains("1500"), s);
  }

}
