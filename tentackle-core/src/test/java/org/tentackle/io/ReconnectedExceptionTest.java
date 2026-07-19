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

package org.tentackle.io;

import org.tentackle.common.TentackleRuntimeException;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link ReconnectedException} constructors.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ReconnectedExceptionTest {

  @Test
  public void testMessageAndCause() {
    Throwable cause = new IllegalStateException("boom");
    ReconnectedException ex = new ReconnectedException("reconnected", cause);
    assertEquals(ex.getMessage(), "reconnected");
    assertSame(ex.getCause(), cause);
    assertTrue(ex instanceof TentackleRuntimeException);
  }

  @Test
  public void testCauseOnly() {
    Throwable cause = new IllegalStateException("boom");
    ReconnectedException ex = new ReconnectedException(cause);
    assertSame(ex.getCause(), cause);
    // Throwable(cause) derives the message from the cause
    assertEquals(ex.getMessage(), cause.toString());
  }
}
