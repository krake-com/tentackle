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

package org.tentackle.session;

import java.io.Serial;

/**
 * A session holder returning the thread-local session.<br>
 * Nice if no domain context is available, e.g., for low-level {@code AbstractDbObject}s.
 * <p>
 * The session cannot be changed: {@link #setSession(Session)} throws an
 * {@link UnsupportedOperationException} unless the given session is the current thread-local session.
 *
 * @author harald
 */
public class ThreadLocalSessionHolder implements SessionHolder {

  @Serial
  private static final long serialVersionUID = 1L;

  private boolean sessionImmutable;


  /**
   * Creates a thread-local session holder.
   */
  public ThreadLocalSessionHolder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public Session getSession() {
    Session session = Session.getCurrentSession();
    if (session == null) {      // avoid NPE
      throw new PersistenceException("thread-local session not set");
    }
    return session;
  }

  @Override
  public void setSession(Session session) {
    if (Session.getCurrentSession() != session) {
      throw new UnsupportedOperationException("session is thread-local by definition and cannot be changed");
    }
  }

  @Override
  public boolean isSessionThreadLocal() {
    return true;
  }

  @Override
  public void setSessionImmutable(boolean sessionImmutable) {
    this.sessionImmutable = sessionImmutable;
  }

  @Override
  public boolean isSessionImmutable() {
    return sessionImmutable;
  }

  @Override
  public SessionInfo getSessionInfo() {
    return getSession().getSessionInfo();
  }

  @Override
  public int getSessionInstanceNumber() {
    return getSession().getInstanceNumber();
  }

}
