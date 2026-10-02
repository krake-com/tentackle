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

import java.io.Serial;

/**
 * A task being executed by a {@link SessionTaskDispatcher}.<br>
 * The dispatcher applies its session when the task is added.
 *
 * @author harald
 */
public abstract class AbstractSessionTask extends AbstractTask implements SessionDependable {

  @Serial
  private static final long serialVersionUID = 1L;

  private transient Session session;          // the session to use
  private transient boolean sessionImmutable; // true if session cannot be changed


  /**
   * Parent constructor.
   */
  public AbstractSessionTask() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Sets the session.<br>
   * Done by the {@link DefaultSessionTaskDispatcher} when the task is added.
   *
   * @param session the session
   * @throws PersistenceException if the session is immutable and the new session differs
   */
  public void setSession(Session session) {
    if (isSessionImmutable() && this.session != session) {
      throw new PersistenceException(this.session, "illegal attempt to change the immutable session of " + this +
                                                   " from " + this.session + " to " + session);
    }
    this.session = session;
  }

  /**
   * Gets the session the task <em>must</em> use.
   *
   * @return the session
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
