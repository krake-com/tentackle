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

package org.tentackle.pdo;

import org.testng.annotations.Test;

import org.tentackle.common.Timestamp;
import org.tentackle.session.Session;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link LockException}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class LockExceptionTest {

  private TokenLockInfo tokenLockInfo() {
    return new TokenLockInfo(7L, new Timestamp(1_000_000L), new Timestamp(2_000_000L));
  }

  @Test
  public void testLockedByAnotherUser() {
    TokenLockInfo info = tokenLockInfo();
    LockException ex = new LockException((Session) null, info);
    assertTrue(ex.isLockedByAnotherUser());
    assertSame(ex.getTokenLockInfo(), info);
    assertNull(ex.getContextName());
    assertEquals(ex.getMessage(), info.toString());
    assertTrue(ex.isTemporary(), "lock exceptions are temporary");
    assertNull(ex.getLogLevel(), "must not be logged automatically");
  }

  @Test
  public void testLockedByContext() {
    LockException ex = new LockException((Session) null, "myContext");
    assertFalse(ex.isLockedByAnotherUser());
    assertNull(ex.getTokenLockInfo());
    assertEquals(ex.getContextName(), "myContext");
    assertEquals(ex.getMessage(), "context=myContext");
    assertTrue(ex.isTemporary());
  }

  @Test
  public void testNullArgumentsRejected() {
    assertThrows(NullPointerException.class, () -> new LockException((Session) null, (TokenLockInfo) null));
    assertThrows(NullPointerException.class, () -> new LockException((Session) null, (String) null));
  }

}
