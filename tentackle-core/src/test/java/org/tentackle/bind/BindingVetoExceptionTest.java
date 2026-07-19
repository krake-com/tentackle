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

package org.tentackle.bind;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link BindingVetoException} flag combinations.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class BindingVetoExceptionTest {

  @Test
  public void testMessageOnly() {
    BindingVetoException ex = new BindingVetoException("nope");
    assertEquals(ex.getMessage(), "nope");
    assertFalse(ex.isResyncRequested());
    assertFalse(ex.isRethrowEnabled());
  }

  @Test
  public void testResyncRequested() {
    BindingVetoException ex = new BindingVetoException("nope", true);
    assertEquals(ex.getMessage(), "nope");
    assertTrue(ex.isResyncRequested());
    assertFalse(ex.isRethrowEnabled());

    BindingVetoException noResync = new BindingVetoException("nope", false);
    assertFalse(noResync.isResyncRequested());
    assertFalse(noResync.isRethrowEnabled());
  }

  @Test
  public void testRethrowEnabled() {
    BindingVetoException ex = new BindingVetoException(true, "nope");
    assertEquals(ex.getMessage(), "nope");
    assertTrue(ex.isRethrowEnabled());
    assertFalse(ex.isResyncRequested());

    BindingVetoException noRethrow = new BindingVetoException(false, "nope");
    assertFalse(noRethrow.isRethrowEnabled());
    assertFalse(noRethrow.isResyncRequested());
  }
}
