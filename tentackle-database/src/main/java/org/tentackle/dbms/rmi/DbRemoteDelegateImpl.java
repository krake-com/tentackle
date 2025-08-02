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

package org.tentackle.dbms.rmi;

import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbTransactionHandle;
import org.tentackle.dbms.DbUtilities;
import org.tentackle.dbms.ModificationLog;
import org.tentackle.dbms.PersistenceVisitor;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SavepointHandle;
import org.tentackle.session.TransactionIsolation;
import org.tentackle.session.TransactionWritability;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

/**
 * Remote delegate implementation for {@link Db}.
 *
 * @author  harald
 */
public class DbRemoteDelegateImpl extends RemoteDelegateImpl<Db>
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
  }


  @Override
  public int getSessionId() throws RemoteException {
    try {
      return getSession().getSessionId();
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }


  @Override
  public void groupWith(int sessionGroupId) throws RemoteException {
    try {
      DbUtilities.getInstance().addToSessionGroup(getSession(), sessionGroupId, true);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public boolean isAlive() throws RemoteException {
    try {
      return getSession().isAlive();
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setAlive(boolean alive) throws RemoteException {
    try {
      getSession().setAlive(alive);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public boolean commit(long txVoucher) throws RemoteException {
    try {
      return getSession().commit(txVoucher);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public boolean rollback(long txVoucher, boolean withLog) throws RemoteException {
    try {
      return getSession().rollback(txVoucher, withLog);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public long begin(String txName, TransactionIsolation transactionIsolation, TransactionWritability transactionWritability) throws RemoteException {
    try {
      return (long) beginMethod.invoke(getSession(), txName, true, transactionIsolation, transactionWritability);
    }
    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public SavepointHandle setSavepoint() throws RemoteException {
    try {
      return getSession().setSavepoint();
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public SavepointHandle setSavepoint(String name) throws RemoteException {
    try {
      return getSession().setSavepoint(name);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void rollback(SavepointHandle handle) throws RemoteException {
    try {
      getSession().rollback(handle);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void releaseSavepoint(SavepointHandle handle) throws RemoteException {
    try {
      getSession().releaseSavepoint(handle);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setReadOnly(boolean flag) throws RemoteException  {
    try {
      getSession().setReadOnly(flag);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setCountModificationAllowed(boolean flag) throws RemoteException  {
    try {
      getSession().setCountModificationAllowed(flag);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setLogModificationAllowed(boolean flag) throws RemoteException  {
    try {
      getSession().setLogModificationAllowed(flag);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setLogModificationTxEnabled(boolean flag) throws RemoteException  {
    try {
      getSession().setLogModificationTxEnabled(flag);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setLogModificationTxId(long txId) throws RemoteException {
    try {
      getSession().setLogModificationTxId(txId);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public long getLogModificationTxId() throws RemoteException {
    try {
      return getSession().getLogModificationTxId();
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public boolean setLogModificationDeferred(boolean flag) throws RemoteException  {
    try {
      getSession().setLogModificationDeferred(flag);
      return getSession().isLogModificationDeferred();
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public List<ModificationLog> popModificationLogsOfTransaction() throws RemoteException {
    try {
      return getSession().popModificationLogsOfTransaction();
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public DbTransactionHandle registerPersistenceVisitor(PersistenceVisitor visitor) throws RemoteException {
    try {
      return getSession().registerPersistenceVisitor(visitor);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public PersistenceVisitor unregisterPersistenceVisitor(DbTransactionHandle handle) throws RemoteException {
    try {
      return getSession().unregisterPersistenceVisitor(handle);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public Collection<PersistenceVisitor> getPersistenceVisitors() throws RemoteException {
    try {
      return getSession().getPersistenceVisitors();
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public <T extends Serializable> T setProperty(String key, T value) throws RemoteException {
    try {
      return getSession().setProperty(key, value);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

  @Override
  public <T extends Serializable> T getProperty(String key) throws RemoteException {
    try {
      return getSession().getProperty(key);
    }
    catch (RuntimeException ex)  {
      throw createException(ex);
    }
  }

}
