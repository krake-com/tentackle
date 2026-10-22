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

package org.tentackle.security;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link DefaultSecurityResult}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultSecurityResultTest {

  @Test
  public void testAccessors() {
    SecurityResult result = new DefaultSecurityResult("because", true, false);
    assertTrue(result.isAccepted());
    assertFalse(result.isDefault());

    result = new DefaultSecurityResult(null, false, true);
    assertFalse(result.isAccepted());
    assertTrue(result.isDefault());
  }

  /**
   * The rule's message explains the default message, it does not replace it.
   */
  @Test
  public void testExplainAppendsTheMessage() {
    assertEquals(new DefaultSecurityResult("because", false, false).explain("denied"),
                 "denied\nbecause");
  }

  /**
   * Without a message of its own, the default message is handed back unchanged.
   */
  @Test
  public void testExplainWithoutMessage() {
    String defaultMessage = "denied";
    assertSame(new DefaultSecurityResult(null, false, false).explain(defaultMessage), defaultMessage);
  }

  @Test
  public void testToString() {
    assertEquals(new DefaultSecurityResult(null, true, false).toString(), "accepted");
    assertEquals(new DefaultSecurityResult(null, false, false).toString(), "denied");
    assertEquals(new DefaultSecurityResult(null, true, true).toString(), "accepted by default");
    assertEquals(new DefaultSecurityResult("no rules match", false, true).toString(),
                 "denied by default 'no rules match'");
  }

}
