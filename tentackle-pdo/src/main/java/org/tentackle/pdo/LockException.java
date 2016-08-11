/**
 * Tentackle - http://www.tentackle.org
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


/**
 * Runtime exception thrown for failed lock operations.
 *
 * @author harald
 */
public class LockException extends PersistenceException implements Loggable {

  private static final long serialVersionUID = -5355906838551059054L;

  private final TokenLock tokenLock;

  /**
   * Constructs a new locking exception for a given session
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param session the session
   * @param tokenLock the token lock information
   */
  public LockException(Session session, TokenLock tokenLock) {
    super(session);
    this.tokenLock = tokenLock;
  }

  /**
   * Constructs a new locking exception for a given session
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param session the session
   * @param message the message
   */
  public LockException(Session session, String message) {
    super(session, message);
    this.tokenLock = null;
  }

  /**
   * Gets the token lock providing all information about the locker.
   *
   * @return the token lock
   */
  public TokenLock getTokenLock() {
    return tokenLock;
  }

  @Override
  public String getMessage() {
    if (tokenLock == null) {
      return super.getMessage();
    }
    return tokenLock.toString();
  }

  @Override
  public Logger.Level getLogLevel() {
    // don't log the stacktrace, it's an expected exception
    return null;
  }

}
