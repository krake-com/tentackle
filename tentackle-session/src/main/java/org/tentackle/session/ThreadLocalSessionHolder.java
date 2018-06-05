/*
 * Tentackle - a framework for java desktop applications.
 * http://www.tentackle.org
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
// $Id$

package org.tentackle.session;

/**
 * A session holder returning the thread-local session.<br>
 * Nice if no domain context available, e.g. for low-level AbstractDbObjects.
 *
 * @author harald
 */
public class ThreadLocalSessionHolder implements SessionHolder {

  private static final long serialVersionUID = 6201552901046919635L;

  private boolean sessionImmutable;

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
    throw new UnsupportedOperationException("session is thread-local by definition and cannot be changed");
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
