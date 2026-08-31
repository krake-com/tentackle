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

package org.tentackle.persist;

import org.tentackle.app.AbstractApplication;
import org.tentackle.misc.Identifiable;
import org.tentackle.pdo.DomainContext;

/**
 * A minimal server application for the tests.
 * <p>
 * The only reason it exists is
 * {@link org.tentackle.persist.lock.DefaultLockManager#DefaultLockManager()}, which decides in its
 * constructor whether the lock manager is enabled: it is, if an {@link org.tentackle.app.Application}
 * is registered, reports itself as a server and holds a local session. Outside a server the lock
 * manager stays completely passive, which is what {@code DefaultLockManagerTest} covers.
 * <p>
 * The application is deliberately not started: {@link #startup()} does nothing and no session pool,
 * connection manager or preferences are configured. It only has to answer {@code isServer()} and
 * hand out the session of the test.
 *
 * @author harald
 */
public class DemoServerApplication extends AbstractApplication {

  /**
   * Creates the server application.
   *
   * @param context the domain context of the test, providing the session
   */
  public DemoServerApplication(DomainContext context) {
    super("DemoServer", "1.0");
    setDomainContext(context);
  }

  @Override
  public boolean isServer() {
    return true;
  }

  @Override
  protected void startup() {
    // nothing to start: the tests drive the lock manager directly
  }

  @Override
  public <U extends Identifiable> U getUser(DomainContext context, long userId) {
    throw new UnsupportedOperationException("the tests have no user entity");
  }

}
