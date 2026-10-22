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

package org.tentackle.fx.rdc;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for the constructors of {@link RdcRuntimeException}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class RdcRuntimeExceptionTest {

  @Test
  public void testConstructors() {
    Throwable cause = new IllegalStateException("cause");

    RdcRuntimeException ex = new RdcRuntimeException();
    assertNull(ex.getMessage());
    assertNull(ex.getCause());

    ex = new RdcRuntimeException("message");
    assertEquals(ex.getMessage(), "message");
    assertNull(ex.getCause());

    ex = new RdcRuntimeException("message", cause);
    assertEquals(ex.getMessage(), "message");
    assertSame(ex.getCause(), cause);

    ex = new RdcRuntimeException(cause);
    assertSame(ex.getCause(), cause);
    assertEquals(ex.getMessage(), cause.toString());
  }

}
