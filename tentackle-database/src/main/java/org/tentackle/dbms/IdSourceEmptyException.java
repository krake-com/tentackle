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

package org.tentackle.dbms;

import java.io.Serial;

/**
 * Runtime exception thrown when an {@link IdSource} becomes empty.<br>
 * Provided for application-specific id sources with a limited range of IDs.
 * 
 * @author harald
 */
public class IdSourceEmptyException extends IdSourceException {
  
  @Serial
  private static final long serialVersionUID = 1L;


 /**
   * Constructs a new id source empty exception for a given session
   * with <code>null</code> as its detail message.
   *
   * @param db the session
   */
  public IdSourceEmptyException(Db db) {
    super(db);
  }

  /**
   * Constructs a new id source empty exception for a given session with the specified detail message.
   *
   * @param   db the session
   * @param   message   the detail message.
   */
  public IdSourceEmptyException(Db db, String message) {
    super(db, message);
  }

  /**
   * Constructs a new id source empty exception for a given session with the specified detail message and
   * cause.
   *
   * @param  db the session
   * @param  message the detail message
   * @param  cause the cause
   */
  public IdSourceEmptyException(Db db, String message, Throwable cause) {
    super(db, message, cause);
  }

  /**
   * Constructs a new id source empty exception for a given session with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>.
   *
   * @param  db the session
   * @param  cause the cause
   */
  public IdSourceEmptyException(Db db, Throwable cause) {
    super(db, cause);
  }

  /**
   * Constructs a new id source empty exception without a session and
   * with <code>null</code> as its detail message.
   */
  public IdSourceEmptyException() {
    super();
  }

  /**
   * Constructs a new id source empty exception without a session and with the specified detail message.
   *
   * @param   message   the detail message.
   */
  public IdSourceEmptyException(String message) {
    super(message);
  }

  /**
   * Constructs a new id source empty exception without a session, but with the specified detail message and
   * cause.
   *
   * @param  message the detail message
   * @param  cause the cause
   */
  public IdSourceEmptyException(String message, Throwable cause) {
    super(message, cause);
  }

}
