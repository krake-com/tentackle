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

package org.tentackle.persist.lock;

import org.testng.annotations.Test;

import org.tentackle.pdo.Pdo;
import org.tentackle.persist.DemoComponent;
import org.tentackle.persist.LockManager;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link DefaultLockManager}.
 * <p>
 * The lock manager keeps track of the token locks held by the clients and is enabled in server
 * applications only. Outside a server &mdash; in a fat client, a batch job or a test like this one
 * &mdash; it must stay completely passive, which is exactly what is verified here. The bookkeeping
 * itself needs a database and belongs to {@code tentackle-test-pdo}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultLockManagerTest {

  @Test
  public void testRegisteredAsTheLockManagerService() {
    assertEquals(LockManager.getInstance().getClass(), DefaultLockManager.class);
  }

  @Test
  public void testDisabledWithoutAServerApplication() {
    assertFalse(new DefaultLockManager().isEnabled(), "enabled in server applications only");
    assertFalse(LockManager.getInstance().isEnabled());
  }

  @Test
  public void testNoLocksAtAll() {
    DefaultLockManager lockManager = new DefaultLockManager();
    assertTrue(lockManager.getLocks().isEmpty());
    assertTrue(lockManager.getLocks(4711).isEmpty());
    assertNull(lockManager.getLock(Pdo.create(DemoComponent.class)));
  }

  @Test
  public void testDisabledLockManagerIgnoresEverything() {
    // none of these must touch the session, which is null here
    DefaultLockManager lockManager = new DefaultLockManager();
    DemoComponent pdo = Pdo.create(DemoComponent.class);

    lockManager.initialize(null);
    lockManager.update(pdo);
    lockManager.cleanupUserTokens(null, 4711);
    lockManager.commit(null, 1);
    lockManager.rollback(null, 1);

    assertTrue(lockManager.getLocks().isEmpty());
  }

}
