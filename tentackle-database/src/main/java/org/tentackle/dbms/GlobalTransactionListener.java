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

/**
 * Callback of a session enlisted in a global transaction.<br>
 * Registered by the session via {@link GlobalTransactionSupport#enlist(Db, GlobalTransactionListener)}
 * and invoked by the transaction manager when the global transaction completes.
 * <p>
 * Corresponds to a JTA {@code Synchronization}.
 *
 * @author harald
 */
public interface GlobalTransactionListener {

  /**
   * Invoked before the two-phase commit starts.<br>
   * Runs the pre-commits and flushes pending statements.
   * An exception thrown here rolls back the global transaction.
   */
  void beforeCompletion();

  /**
   * Invoked after the global transaction has been committed or rolled back.<br>
   * Notice that this method may be invoked from a thread other than the session's
   * owner thread, for example, if the transaction manager rolled back a timed-out transaction.
   *
   * @param committed true if committed, false if rolled back
   */
  void afterCompletion(boolean committed);

}
