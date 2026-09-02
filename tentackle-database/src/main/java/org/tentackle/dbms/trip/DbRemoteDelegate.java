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

package org.tentackle.dbms.trip;

import org.tentackle.dbms.DbTransactionHandle;
import org.tentackle.dbms.ModificationLog;
import org.tentackle.dbms.PersistenceVisitor;
import org.tentackle.dbms.RemoteTransaction;
import org.tentackle.session.SavepointHandle;
import org.tentackle.session.TransactionIsolation;
import org.tentackle.session.TransactionWritability;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Remote delegate interface for {@link org.tentackle.dbms.Db}.
 *
 * @author harald
 */
public interface DbRemoteDelegate extends RemoteDbDelegate {

  int getSessionId();

  void groupWith(int sessionGroupId);

  boolean isAlive();

  void setAlive(boolean alive);

  RemoteTransaction begin(String txName, TransactionIsolation transactionIsolation, TransactionWritability transactionWritability);

  boolean commit(long txVoucher);

  boolean rollback(long txVoucher, boolean withLog);

  SavepointHandle setSavepoint();

  SavepointHandle setSavepoint(String name);

  void rollback(SavepointHandle handle);

  void releaseSavepoint(SavepointHandle handle);

  void setReadOnly(boolean flag);

  void setCountModificationAllowed(boolean flag);

  void setLogModificationAllowed(boolean flag);

  void setLogModificationTxEnabled(boolean flag);

  void setLogModificationTxId(long txId);

  long getLogModificationTxId();

  boolean setLogModificationDeferred(boolean flag);

  boolean setConstraintsDeferred(boolean flag);

  List<ModificationLog> popModificationLogsOfTransaction();

  DbTransactionHandle registerPersistenceVisitor(PersistenceVisitor visitor);

  PersistenceVisitor unregisterPersistenceVisitor(DbTransactionHandle handle);

  Collection<PersistenceVisitor> getPersistenceVisitors();

  <T extends Serializable> T setProperty(String key, T value);

  <T extends Serializable> T getProperty(String key);

  void clearProperties();

}
