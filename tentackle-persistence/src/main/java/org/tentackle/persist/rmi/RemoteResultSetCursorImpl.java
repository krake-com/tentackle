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

package org.tentackle.persist.rmi;

import org.tentackle.dbms.rmi.RemoteDbSessionImpl;
import org.tentackle.dbms.rmi.RemoteDelegateImpl;
import org.tentackle.log.Logger;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.ResultSetCursor;
import org.tentackle.persist.ResultSetCursor.FetchList;

import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote delegate implementation for {@link ResultSetCursor}.
 *
 * @author harald
 * @param <T> the object type
 */
public class RemoteResultSetCursorImpl<T extends PersistentDomainObject<T>>
             extends RemoteDelegateImpl<T>
             implements RemoteResultSetCursor<T> {


  private static final Logger LOGGER = Logger.get(RemoteResultSetCursorImpl.class);


  private ResultSetCursor<T> cursor;      // local cursor


  /**
   * Creates a remote cursor delegate.
   *
   * @param session the RMI session
   * @param clazz the PDO interface class
   */
  public RemoteResultSetCursorImpl(RemoteDbSessionImpl session, Class<T> clazz) {
    super(session, clazz);
  }

  @Override
  public String toString() {
    return "RemoteResultSetCursorImpl for " + getServicedClass() + ", session " + getServerSession();
  }

  /**
   * Configures the delegate.<p>
   * Invoked from {@link RemoteDbSessionImpl#createRemoteDelegateInstance} by reflection.
   *
   * @param args the configuration parameters
   */
  @SuppressWarnings("unchecked")
  public void configureDelegate(Object... args) {
    setCursor((ResultSetCursor<T>) args[0]);
  }


  /**
   * Sets the local cursor.
   *
   * @param cursor the cursor
   */
  public void setCursor(ResultSetCursor<T> cursor) {
    this.cursor = cursor;
  }

  /**
   * Gets the local cursor.
   *
   * @return the local cursor
   */
  public ResultSetCursor<T> getCursor() {
    return cursor;
  }


  @Override
  public int afterLast() throws RemoteException {
    try {
      cursor.afterLast();
      return cursor.getRow();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void beforeFirst() throws RemoteException {
    try {
     cursor.beforeFirst();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void close() throws RemoteException {
    try {
      cursor.close();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public boolean first() throws RemoteException {
    try {
      return cursor.first();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public T get() throws RemoteException {
    try {
      return cursor.get();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public boolean isAfterLast() throws RemoteException {
    try {
      return cursor.isAfterLast();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public int last() throws RemoteException {
    try {
      return cursor.last() ? cursor.getRow() : -1;
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public boolean next() throws RemoteException {
    try {
      return cursor.next();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public boolean previous() throws RemoteException {
    try {
      return cursor.previous();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public int setRow(int row) throws RemoteException {
    try {
      cursor.setRow(row);
      return cursor.getRow();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public int scroll(int rows) throws RemoteException {
    try {
      cursor.scroll(rows);
      return cursor.getRow();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public List<T> toList() throws RemoteException {
    try {
      return cursor.toList();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void setFetchSize(int rows) throws RemoteException {
    try {
      cursor.setFetchSize(rows);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public int getFetchSize() throws RemoteException {
    try {
      return cursor.getFetchSize();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void setFetchDirection(int direction) throws RemoteException {
    try {
      cursor.setFetchDirection(direction);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public int getFetchDirection() throws RemoteException {
    try {
      return cursor.getFetchDirection();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public FetchList<T> fetch() throws RemoteException {
    try {
      return cursor.fetch();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }


  @Override
  public RemoteException createException(Throwable t) {
    try {
      cursor.close();
    }
    catch (RuntimeException r) {
      LOGGER.severe("closing cursor failed", r);
    }
    return super.createException(t);
  }

}
