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

// Created on November 18, 2003, 7:52 PM

package org.tentackle.persist.rmi;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;
import org.tentackle.persist.CommitTxRunnable;
import org.tentackle.persist.DbTransaction;
import org.tentackle.persist.DbTransactionHandle;
import org.tentackle.persist.ModificationLog;
import org.tentackle.persist.PersistenceVisitor;
import org.tentackle.persist.RollbackTxRunnable;

/**
 * Remote delegate interface for {@link org.tentackle.persist.Db}.
 *
 * @author harald
 */
public interface DbRemoteDelegate extends RemoteDelegate {

  int getSessionId() throws RemoteException;

  void setSessionGroupId(int number) throws RemoteException;

  int groupWith(int number) throws RemoteException;

  int getSessionGroupId() throws RemoteException;

  boolean isAlive() throws RemoteException;

  void setAlive(boolean alive) throws RemoteException;

  DbTransaction begin(String txName) throws RemoteException;

  boolean commit(long txVoucher) throws RemoteException;

  boolean rollback(long txVoucher) throws RemoteException;

  void setReadOnly(boolean flag) throws RemoteException;

  void setCountModificationAllowed(boolean flag) throws RemoteException;

  void setLogModificationAllowed(boolean flag) throws RemoteException;

  void setLogModificationTxEnabled(boolean flag) throws RemoteException;

  void setLogModificationTxId(long txId) throws RemoteException;

  long getLogModificationTxId() throws RemoteException;

  boolean setLogModificationDeferred(boolean flag) throws RemoteException;

  List<ModificationLog> popModificationLogsOfTransaction() throws RemoteException;

  DbTransactionHandle registerCommitTxRunnable(CommitTxRunnable runnable) throws RemoteException;

  CommitTxRunnable unregisterCommitTxRunnable(DbTransactionHandle handle) throws RemoteException;

  Collection<CommitTxRunnable> getCommitTxRunnables() throws RemoteException;

  DbTransactionHandle registerRollbackTxRunnable(RollbackTxRunnable runnable) throws RemoteException;

  RollbackTxRunnable unregisterRollbackTxRunnable(DbTransactionHandle handle) throws RemoteException;

  Collection<RollbackTxRunnable> getRollbackTxRunnables() throws RemoteException;

  DbTransactionHandle registerPersistenceVisitor(PersistenceVisitor visitor) throws RemoteException;

  PersistenceVisitor unregisterPersistenceVisitor(DbTransactionHandle handle) throws RemoteException;

  Collection<PersistenceVisitor> getPersistenceVisitors() throws RemoteException;

}
