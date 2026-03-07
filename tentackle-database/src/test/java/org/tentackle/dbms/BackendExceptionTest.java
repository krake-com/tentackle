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

import org.tentackle.common.ObjectSerializer;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.ScriptRunnerException;
import org.tentackle.sql.ScriptRunnerResult;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;

/**
 * Some TRIP tests for tentackle-sql.<br>
 * The module tentackle-sql has no dependency to tentackle-core,
 * so we run the tests here.
 */
@SuppressWarnings("missing-explicit-ctor")
public class BackendExceptionTest {

  @Test
  public void testBackendException() {
    BackendException bx = new BackendException("test");
    BackendException copy = ObjectSerializer.getInstance().copy(bx);
    assertNotSame(copy, bx);
    assertEquals(copy.getMessage(), bx.getMessage());
  }

  @Test
  public void testScriptRunnerException() {
    List< ScriptRunnerResult > results = new ArrayList<>();
    results.add(new ScriptRunnerResult("warnings"));
    ScriptRunnerException sx = new ScriptRunnerException("test", "SELECT 1", results, new SQLException("sql"));
    ScriptRunnerException copy = ObjectSerializer.getInstance().copy(sx);
    assertNotSame(copy, sx);
    assertEquals(copy.getMessage(), sx.getMessage());
    assertEquals(copy.getSql(), sx.getSql());
    assertNotNull(copy.getResults());
    assertNotNull(sx.getResults());
    assertEquals(copy.getResults().size(), sx.getResults().size());
  }

}
