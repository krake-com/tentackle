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

package org.tentackle.script;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link ScriptRuntimeException}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ScriptRuntimeExceptionTest {

  @Test
  public void testIsUnchecked() {
    assertTrue(RuntimeException.class.isAssignableFrom(ScriptRuntimeException.class));
  }

  @Test
  public void testEmptyConstructor() {
    ScriptRuntimeException ex = new ScriptRuntimeException();
    assertNull(ex.getMessage());
    assertNull(ex.getCause());
  }

  @Test
  public void testMessageConstructor() {
    ScriptRuntimeException ex = new ScriptRuntimeException("boom");
    assertEquals(ex.getMessage(), "boom");
    assertNull(ex.getCause());
  }

  @Test
  public void testCauseConstructor() {
    Throwable cause = new IllegalStateException("root");
    ScriptRuntimeException ex = new ScriptRuntimeException(cause);
    assertSame(ex.getCause(), cause);
  }

  @Test
  public void testMessageAndCauseConstructor() {
    Throwable cause = new IllegalStateException("root");
    ScriptRuntimeException ex = new ScriptRuntimeException("boom", cause);
    assertEquals(ex.getMessage(), "boom");
    assertSame(ex.getCause(), cause);
  }
}
