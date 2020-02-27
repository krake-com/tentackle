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

import org.tentackle.task.AbstractTask;


/**
 * A task being executed by the {@link SessionTaskDispatcher}.
 *
 * @author harald
 */
public abstract class AbstractSessionTask extends AbstractTask implements SessionDependable {

  private static final long serialVersionUID = -702889862433380249L;

  private transient Session session;          // the session to use
  private transient boolean sessionImmutable; // true if session cannot be changed


  /**
   * Sets the db.<br>
   * Done by {@link SessionTaskDispatcher}.
   *
   * @param session the session
   */
  public void setSession(Session session) {
    if (isSessionImmutable() && this.session != session) {
      throw new PersistenceException(this.session, "illegal attempt to change the immutable session of " + this +
                                                   " from " + this.session + " to " + session);
    }
    this.session = session;
  }

  /**
   * Gets the db the runnable <em>must</em> use.
   *
   * @return the db
   */
  public Session getSession() {
    return session;
  }

  @Override
  public void setSessionImmutable(boolean sessionImmutable) {
    this.sessionImmutable = sessionImmutable;
  }

  @Override
  public boolean isSessionImmutable() {
    return sessionImmutable;
  }
}
