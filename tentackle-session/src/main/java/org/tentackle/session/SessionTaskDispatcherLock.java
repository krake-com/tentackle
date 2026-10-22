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

import org.tentackle.task.DefaultTaskDispatcherLock;

/**
 * Lock for the {@link DefaultSessionTaskDispatcher}.<br>
 * Provides access to the dispatcher's session while the lock is held.
 *
 * @author harald
 */
public class SessionTaskDispatcherLock extends DefaultTaskDispatcherLock {

  /**
   * Creates a dispatcher lock.
   *
   * @param dispatcher the locked dispatcher
   * @param key the locking key
   */
  public SessionTaskDispatcherLock(DefaultSessionTaskDispatcher dispatcher, Object key) {
    super(dispatcher, key);
  }


  @Override
  public DefaultSessionTaskDispatcher getTaskDispatcher() {
    return (DefaultSessionTaskDispatcher) super.getTaskDispatcher();
  }

  /**
   * Gets the session associated with this lock.
   *
   * @return the session
   */
  public Session getSession() {
    return getTaskDispatcher().getSession();
  }
}
