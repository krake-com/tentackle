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

import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.ClientInfoStatus;
import java.sql.DataTruncation;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

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

  @Test
  public void testSQLException() {
    SQLException sx = new SQLException("failed", "08001", 17, new IOException("io"));
    sx.setNextException(new SQLException("next", "08002", 18));
    SQLException copy = ObjectSerializer.getInstance().copy(sx);
    assertSame(copy.getClass(), SQLException.class);
    assertEquals(copy.getMessage(), "failed");
    assertEquals(copy.getSQLState(), "08001");
    assertEquals(copy.getErrorCode(), 17);
    assertSame(copy.getCause().getClass(), IOException.class);
    assertEquals(copy.getCause().getMessage(), "io");
    assertNotNull(copy.getNextException());
    assertEquals(copy.getNextException().getMessage(), "next");
    assertEquals(copy.getNextException().getSQLState(), "08002");
    assertNull(copy.getNextException().getNextException());
    assertEquals(copy.getStackTrace(), sx.getStackTrace());
  }

  @Test
  public void testSQLExceptionSubclasses() {
    SQLIntegrityConstraintViolationException cx = new SQLIntegrityConstraintViolationException("duplicate", "23505", 1);
    SQLIntegrityConstraintViolationException cxCopy = ObjectSerializer.getInstance().copy(cx);
    assertSame(cxCopy.getClass(), SQLIntegrityConstraintViolationException.class);
    assertEquals(cxCopy.getSQLState(), "23505");

    BatchUpdateException bx = new BatchUpdateException("batch", "22001", 2, new long[] { 1, 5_000_000_000L }, null);
    BatchUpdateException bxCopy = ObjectSerializer.getInstance().copy(bx);
    assertEquals(bxCopy.getMessage(), "batch");
    assertEquals(bxCopy.getSQLState(), "22001");
    assertEquals(bxCopy.getErrorCode(), 2);
    assertEquals(bxCopy.getLargeUpdateCounts(), new long[] { 1, 5_000_000_000L });

    DataTruncation dx = new DataTruncation(3, true, false, 100, 50);
    DataTruncation dxCopy = ObjectSerializer.getInstance().copy(dx);
    assertEquals(dxCopy.getIndex(), 3);
    assertTrue(dxCopy.getParameter());
    assertEquals(dxCopy.getRead(), false);
    assertEquals(dxCopy.getDataSize(), 100);
    assertEquals(dxCopy.getTransferSize(), 50);

    SQLClientInfoException ix = new SQLClientInfoException("info", Map.of("app", ClientInfoStatus.REASON_VALUE_INVALID));
    SQLClientInfoException ixCopy = ObjectSerializer.getInstance().copy(ix);
    assertEquals(ixCopy.getMessage(), "info");
    assertEquals(ixCopy.getFailedProperties(), ix.getFailedProperties());
  }

}
