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

import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbTransactionHandle;
import org.tentackle.dbms.DbUtilities;
import org.tentackle.dbms.ModificationLog;
import org.tentackle.dbms.PersistenceVisitor;
import org.tentackle.dbms.RemoteTransaction;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SavepointHandle;
import org.tentackle.session.TransactionIsolation;
import org.tentackle.session.TransactionWritability;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * Remote delegate implementation for {@link Db}.
 *
 * @author  harald
 */
public class DbRemoteDelegateImpl extends RemoteDbDelegateImpl<Db>
                                  implements DbRemoteDelegate {


  private final Method beginMethod;             // begin method for db


  public DbRemoteDelegateImpl(RemoteDbSessionImpl session, Class<Db> dbClass) {

    super(session, dbClass);

    // figure out the private begin method
    try {
      beginMethod = dbClass.getDeclaredMethod("begin", String.class, Boolean.TYPE, TransactionIsolation.class, TransactionWritability.class);
      beginMethod.setAccessible(true);
    }
    catch (NoSuchMethodException ex) {
      throw new PersistenceException(dbClass.getName() + " provides no begin(String,boolean)", ex);
    }
    catch (RuntimeException rx) {
      throw new PersistenceException(dbClass.getName() + " cannot access begin(String,boolean)", rx);
    }
  }


  @Override
  public int getSessionId() {
    return getSession().getSessionId();
  }


  @Override
  public void groupWith(int sessionGroupId) {
    DbUtilities.getInstance().addToSessionGroup(getSession(), sessionGroupId, true);
  }

  @Override
  public boolean isAlive() {
    return getSession().isAlive();
  }

  @Override
  public void setAlive(boolean alive) {
    getSession().setAlive(alive);
  }

  @Override
  public boolean commit(long txVoucher) {
    return getSession().commit(txVoucher);
  }

  @Override
  public boolean rollback(long txVoucher, boolean withLog) {
    return getSession().rollback(txVoucher, withLog);
  }

  @Override
  public RemoteTransaction begin(String txName, TransactionIsolation transactionIsolation, TransactionWritability transactionWritability) {
    try {
      Db session = getSession();
      return new RemoteTransaction((long) beginMethod.invoke(session, txName, true, transactionIsolation, transactionWritability),
                                   session.getTxName(), session.getTxNumber(), session.getTxLevel());
    }
    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex)  {
      throw new PersistenceException("failed to invoke begin method", ex);
    }
  }

  @Override
  public SavepointHandle setSavepoint() {
    return getSession().setSavepoint();
  }

  @Override
  public SavepointHandle setSavepoint(String name) {
    return getSession().setSavepoint(name);
  }

  @Override
  public void rollback(SavepointHandle handle) {
    getSession().rollback(handle);
  }

  @Override
  public void releaseSavepoint(SavepointHandle handle) {
    getSession().releaseSavepoint(handle);
  }

  @Override
  public void setReadOnly(boolean flag) {
    getSession().setReadOnly(flag);
  }

  @Override
  public void setCountModificationAllowed(boolean flag) {
    getSession().setCountModificationAllowed(flag);
  }

  @Override
  public void setLogModificationAllowed(boolean flag) {
    getSession().setLogModificationAllowed(flag);
  }

  @Override
  public void setLogModificationTxEnabled(boolean flag) {
    getSession().setLogModificationTxEnabled(flag);
  }

  @Override
  public void setLogModificationTxId(long txId) {
    getSession().setLogModificationTxId(txId);
  }

  @Override
  public long getLogModificationTxId() {
    return getSession().getLogModificationTxId();
  }

  @Override
  public boolean setLogModificationDeferred(boolean flag) {
    getSession().setLogModificationDeferred(flag);
    return getSession().isLogModificationDeferred();
  }

  @Override
  public boolean setConstraintsDeferred(boolean flag) {
    getSession().setConstraintsDeferred(flag);
    return getSession().isConstraintsDeferred();
  }

  @Override
  public List<ModificationLog> popModificationLogsOfTransaction() {
    return getSession().popModificationLogsOfTransaction();
  }

  @Override
  public DbTransactionHandle registerPersistenceVisitor(PersistenceVisitor visitor) {
    return getSession().registerPersistenceVisitor(visitor);
  }

  @Override
  public PersistenceVisitor unregisterPersistenceVisitor(DbTransactionHandle handle) {
    return getSession().unregisterPersistenceVisitor(handle);
  }

  @Override
  public Collection<PersistenceVisitor> getPersistenceVisitors() {
    return getSession().getPersistenceVisitors();
  }

  @Override
  public <T extends Serializable> T setProperty(String key, T value) {
    return getSession().setProperty(key, value);
  }

  @Override
  public <T extends Serializable> T getProperty(String key) {
    return getSession().getProperty(key);
  }

  @Override
  public void clearProperties() {
    getSession().clearProperties();
  }

}
