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
 * Bridge between local sessions and a global (distributed) transaction manager, such as JTA.
 * <p>
 * The bridge is only consulted by sessions configured with a {@link GlobalTransactionMode}
 * other than {@link GlobalTransactionMode#NONE}. An implementation is provided as a
 * {@link org.tentackle.common.Service} by an optional module, e.g. {@code tentackle-jta}.
 * <p>
 * Global transactions are associated with the current thread. All methods are invoked
 * from the thread the transaction is associated with.
 * <p>
 * Implementations throw a {@link org.tentackle.session.PersistenceException} if an operation fails.
 * If the cause is transient (a deadlock, for example), the exception must be flagged
 * {@link org.tentackle.common.TentackleRuntimeException#setTemporary(boolean) temporary}, so that
 * a transaction can be retried.
 *
 * @author harald
 */
public interface GlobalTransactionSupport {

  /**
   * Returns whether a global transaction is associated with the current thread.
   *
   * @return true if a global transaction is active
   */
  boolean isGlobalTransactionActive();

  /**
   * Starts a new global transaction and associates it with the current thread.
   *
   * @param session the session starting the global transaction
   * @param txName the transaction name, null if unnamed
   */
  void begin(Db session, String txName);

  /**
   * Enlists the session's attached connection in the global transaction of the current thread.<br>
   * The listener must be invoked when the global transaction completes.
   *
   * @param session the session, attached to its connection
   * @param listener the completion listener
   */
  void enlist(Db session, GlobalTransactionListener listener);

  /**
   * Commits the global transaction of the current thread.<br>
   * Invoked only by the session that started the global transaction.
   * <p>
   * If the global transaction was rolled back instead, a {@link org.tentackle.session.PersistenceException}
   * is thrown. If the rollback was caused by an exception thrown by a {@link GlobalTransactionListener},
   * that exception is rethrown.
   *
   * @param session the session that started the global transaction
   */
  void commit(Db session);

  /**
   * Rolls back the global transaction of the current thread.<br>
   * Invoked only by the session that started the global transaction.
   *
   * @param session the session that started the global transaction
   */
  void rollback(Db session);

  /**
   * Marks the global transaction of the current thread so that its only possible outcome is a rollback.
   *
   * @param session the session requesting the rollback
   */
  void setRollbackOnly(Db session);

}
