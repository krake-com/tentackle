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



package org.tentackle.dbms;

import org.tentackle.dbms.rmi.AbstractDbOperationRemoteDelegate;


/**
 * Holds static class variables for classes derived from AbstractDbOperation.
 * This is a "singleton per class".
 *
 * @param <P> the persistent operation class
 * @author harald
 */
public class DbOperationClassVariables<P extends AbstractDbOperation<P>> {

  /**
   * the class.
   */
  public final Class<P> clazz;

  /**
   * the full classname.
   */
  public final String className;

  /**
   * ID for the remote delegate for this class.
   */
  public int remoteDelegateId;


  /**
   * Gets the delegateId of the class, i.e. subclass of AbstractDbOperation.
   * If the remoteDelegateId is 0, it will be prepared
   * The delegateId is unique for each class. It is valid only in remote connections and
   * is the same for all remote Db's.
   * The RMI-server creates a delegate for each subclass of AbstractDbOperation (DbOperationRemoteDelegateImpl resp.)
   *
   * @return the delegate id
   */
  public int getRemoteDelegateId() {
    if (remoteDelegateId == 0)  {
      remoteDelegateId = Db.prepareRemoteDelegate(clazz);
    }
    return remoteDelegateId;
  }

  /**
   * Gets the RemoteDelegate for the class and db.
   * @param db the db connection
   * @return the delegate
   */
  @SuppressWarnings("unchecked")
  public AbstractDbOperationRemoteDelegate<P> getRemoteDelegate(Db db) {
    return (AbstractDbOperationRemoteDelegate<P>) db.getRemoteDelegate(getRemoteDelegateId());
  }


  /**
   * constructs a classvariable.
   * Throws IllegalStateException if already constructed.
   *
   * @param clazz is the class of the derived AbstractDbOperation
   */
  public DbOperationClassVariables(Class<P> clazz)  {
    this.clazz      = clazz;
    className       = clazz.getName();
  }


  @Override
  public String toString() {
    return className;
  }

}
