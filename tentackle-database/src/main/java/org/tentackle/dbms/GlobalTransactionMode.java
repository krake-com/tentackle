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

import org.tentackle.session.PersistenceException;

import java.util.Locale;

/**
 * Determines how a local session takes part in global (distributed) transactions.
 * <p>
 * Configured via the backend property {@value Db#JTA}.
 *
 * @author harald
 * @see GlobalTransactionSupport
 */
public enum GlobalTransactionMode {

  /**
   * Local transactions only (default).<br>
   * A running global transaction is ignored.
   */
  NONE,

  /**
   * Join a global transaction if one is associated with the current thread.<br>
   * Otherwise, a local transaction is used.
   * <p>
   * This is the mode for sessions running in a container or under the control of
   * an external transaction manager that begins and completes the global transactions.
   */
  JOIN,

  /**
   * Join a global transaction if one is associated with the current thread.<br>
   * Otherwise, the outermost {@link Db#begin()} starts a new global transaction and
   * the matching {@link Db#commit(long)} completes it via two-phase commit.
   * <p>
   * This is the mode for standalone applications that need atomic transactions spanning
   * several sessions (databases) or other transactional resources.
   */
  REQUIRED;


  /**
   * Parses the mode from a property value.
   *
   * @param value the property value, null or empty for {@link #NONE}
   * @return the mode
   * @throws PersistenceException if no such mode
   */
  public static GlobalTransactionMode parse(String value) {
    if (value == null || value.isBlank()) {
      return NONE;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
    catch (IllegalArgumentException ex) {
      throw new PersistenceException("invalid global transaction mode '" + value + "'", ex);
    }
  }

}
