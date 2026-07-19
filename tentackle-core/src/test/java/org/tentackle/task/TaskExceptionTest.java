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

package org.tentackle.task;

import org.tentackle.common.TentackleRuntimeException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link TaskException} constructors.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class TaskExceptionTest {

  @Test
  public void testIsTentackleRuntimeException() {
    assertTrue(new TaskException() instanceof TentackleRuntimeException);
  }

  @Test
  public void testDefaultConstructor() {
    TaskException ex = new TaskException();
    assertNull(ex.getMessage());
    assertNull(ex.getCause());
  }

  @Test
  public void testMessageConstructor() {
    TaskException ex = new TaskException("failed");
    assertEquals(ex.getMessage(), "failed");
    assertNull(ex.getCause());
  }

  @Test
  public void testMessageAndCauseConstructor() {
    Throwable cause = new IllegalStateException("root");
    TaskException ex = new TaskException("failed", cause);
    assertEquals(ex.getMessage(), "failed");
    assertSame(ex.getCause(), cause);
  }

  @Test
  public void testCauseConstructor() {
    Throwable cause = new IllegalStateException("root");
    TaskException ex = new TaskException(cause);
    assertSame(ex.getCause(), cause);
    // detail message is derived from the cause
    assertEquals(ex.getMessage(), cause.toString());
  }
}
