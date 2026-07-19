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

package org.tentackle.sql;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link BackendException} and {@link ScriptRunnerException}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class BackendExceptionTest {

  @Test
  public void testDefaultConstructor() {
    BackendException ex = new BackendException();
    assertNull(ex.getMessage());
    assertNull(ex.getCause());
  }

  @Test
  public void testMessageConstructor() {
    BackendException ex = new BackendException("boom");
    assertEquals(ex.getMessage(), "boom");
    assertNull(ex.getCause());
  }

  @Test
  public void testMessageAndCauseConstructor() {
    Throwable cause = new IllegalStateException("root");
    BackendException ex = new BackendException("boom", cause);
    assertEquals(ex.getMessage(), "boom");
    assertSame(ex.getCause(), cause);
  }

  @Test
  public void testScriptRunnerException() {
    Throwable cause = new IllegalStateException("root");
    List<ScriptRunnerResult> results = List.of(new ScriptRunnerResult("ok"));
    ScriptRunnerException ex = new ScriptRunnerException("failed", "SELECT 1", results, cause);
    assertTrue(ex instanceof BackendException);
    assertEquals(ex.getMessage(), "failed");
    assertEquals(ex.getSql(), "SELECT 1");
    assertSame(ex.getResults(), results);
    assertSame(ex.getCause(), cause);
  }
}
