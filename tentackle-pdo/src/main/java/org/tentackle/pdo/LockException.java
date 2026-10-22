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

import org.tentackle.log.Loggable;
import org.tentackle.log.Logger;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

import java.io.Serial;
import java.util.Objects;


/**
 * Runtime exception thrown for failed lock operations.
 * <p>
 * By default, {@link LockException}s are marked as temporary.
 *
 * @author harald
 * @see org.tentackle.pdo.trip.LockExceptionInstanceCreator
 */
public final class LockException extends PersistenceException implements Loggable {

  @Serial
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("serial")
  private final TokenLockInfo tokenLockInfo;
  private final String contextName;

  /**
   * Constructs a new locking exception for a given session and lock info.<br>
   * Thrown if already locked by another user.
   *
   * @param session the session
   * @param tokenLockInfo the token lock information
   */
  public LockException(Session session, TokenLockInfo tokenLockInfo) {
    super(session);
    this.tokenLockInfo = Objects.requireNonNull(tokenLockInfo, "tokenLockInfo");
    this.contextName = null;
    setTemporary(true);
  }

  /**
   * Constructs a new locking exception for a given session and context name.<br>
   * Thrown if locked by the same user, but within another named domain context.
   *
   * @param session the session
   * @param contextName the already locked context
   */
  public LockException(Session session, String contextName) {
    super(session);
    this.tokenLockInfo = null;
    this.contextName = Objects.requireNonNull(contextName, "contextName");
    setTemporary(true);
  }


  /**
   * Gets the token lock information about the locker.
   *
   * @return the token lock, null if caused by wrong domain context
   */
  public TokenLockInfo getTokenLockInfo() {
    return tokenLockInfo;
  }

  /**
   * Gets the name of the domain context.
   *
   * @return the context name, null if locked by another user
   */
  public String getContextName() {
    return contextName;
  }

  /**
   * Returns whether locked by another user or in another context.
   *
   * @return true if the reason is another user's lock, false if locked in another context
   */
  public boolean isLockedByAnotherUser() {
    return tokenLockInfo != null;
  }


  @Override
  public String getMessage() {
    if (isLockedByAnotherUser()) {
      return tokenLockInfo.toString();
    }
    return "context=" + contextName;
  }

  @Override
  public Logger.Level getLogLevel() {
    // don't log in server, since it's an expected exception
    return null;
  }

}
