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

// Created on November 18, 2003, 7:54 PM

package org.tentackle.persist.rmi;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;
import org.tentackle.persist.CommitTxRunnable;
import org.tentackle.persist.Db;
import org.tentackle.persist.DbTransaction;
import org.tentackle.persist.DbTransactionHandle;
import org.tentackle.persist.ModificationLog;
import org.tentackle.persist.PersistenceVisitor;
import org.tentackle.persist.RollbackTxRunnable;

/**
 * Remote delegate implementation for {@link Db}.
 *
 * @author  harald
 */
public class DbRemoteDelegateImpl extends RemoteDelegateImpl<Db>
                                  implements DbRemoteDelegate {


  private Method beginMethod;             // begin method for db


  public DbRemoteDelegateImpl(RemoteDbSessionImpl session, Class<Db> dbClass) throws RemoteException {

    super(session, dbClass);

    // figure out private begin method
    try {
      beginMethod = dbClass.getDeclaredMethod("begin", String.class, Boolean.TYPE);
      beginMethod.setAccessible(true);
    }
    catch (NoSuchMethodException ex) {
      throw new RemoteException(dbClass.getName() + " provides no begin(String,boolean)", ex);
    }
  }


  @Override
  public int getSessionId() throws RemoteException {
    try {
      return getSession().getSessionId();
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setSessionGroupId(int number) throws RemoteException {
    try {
      getSession().setSessionGroupId(number);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public int getSessionGroupId() throws RemoteException {
    try {
      return getSession().getSessionGroupId();
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public int groupWith(int number) throws RemoteException {
    try {
      return getSession().groupWith(number);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public boolean isAlive() throws RemoteException {
    try {
      return getSession().isAlive();
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setAlive(boolean alive) throws RemoteException {
    try {
      getSession().setAlive(alive);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public boolean commit(long txVoucher) throws RemoteException {
    try {
      return getSession().commit(txVoucher);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public boolean rollback(long txVoucher, boolean withLog) throws RemoteException {
    try {
      return getSession().rollback(txVoucher, withLog);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public DbTransaction begin(String txName) throws RemoteException {
    try {
      return (DbTransaction) beginMethod.invoke(getSession(), txName, true);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setReadOnly(boolean flag) throws RemoteException  {
    try {
      getSession().setReadOnly(flag);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setCountModificationAllowed(boolean flag) throws RemoteException  {
    try {
      getSession().setCountModificationAllowed(flag);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setLogModificationAllowed(boolean flag) throws RemoteException  {
    try {
      getSession().setLogModificationAllowed(flag);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setLogModificationTxEnabled(boolean flag) throws RemoteException  {
    try {
      getSession().setLogModificationTxEnabled(flag);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public void setLogModificationTxId(long txId) throws RemoteException {
    try {
      getSession().setLogModificationTxId(txId);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public long getLogModificationTxId() throws RemoteException {
    try {
      return getSession().getLogModificationTxId();
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public boolean setLogModificationDeferred(boolean flag) throws RemoteException  {
    try {
      getSession().setLogModificationDeferred(flag);
      return getSession().isLogModificationDeferred();
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public List<ModificationLog> popModificationLogsOfTransaction() throws RemoteException {
    try {
      return getSession().popModificationLogsOfTransaction();
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public DbTransactionHandle registerPersistenceVisitor(PersistenceVisitor visitor) throws RemoteException {
    try {
      return getSession().registerPersistenceVisitor(visitor);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public PersistenceVisitor unregisterPersistenceVisitor(DbTransactionHandle handle) throws RemoteException {
    try {
      return getSession().unregisterPersistenceVisitor(handle);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public Collection<PersistenceVisitor> getPersistenceVisitors() throws RemoteException {
    try {
      return getSession().getPersistenceVisitors();
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }


  @Override
  public DbTransactionHandle registerCommitTxRunnable(CommitTxRunnable runnable) throws RemoteException {
    try {
      return getSession().registerCommitTxRunnable(runnable);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public CommitTxRunnable unregisterCommitTxRunnable(DbTransactionHandle handle) throws RemoteException {
    try {
      return getSession().unregisterCommitTxRunnable(handle);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public Collection<CommitTxRunnable> getCommitTxRunnables() throws RemoteException {
    try {
      return getSession().getCommitTxRunnables();
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }


  @Override
  public DbTransactionHandle registerRollbackTxRunnable(RollbackTxRunnable runnable) throws RemoteException {
    try {
      return getSession().registerRollbackTxRunnable(runnable);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public RollbackTxRunnable unregisterRollbackTxRunnable(DbTransactionHandle handle) throws RemoteException {
    try {
      return getSession().unregisterRollbackTxRunnable(handle);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public Collection<RollbackTxRunnable> getRollbackTxRunnables() throws RemoteException {
    try {
      return getSession().getRollbackTxRunnables();
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public <T extends Serializable> T setProperty(String key, T value) throws RemoteException {
    try {
      return getSession().setProperty(key, value);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public <T extends Serializable> T getProperty(String key) throws RemoteException {
    try {
      return getSession().getProperty(key);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

}
