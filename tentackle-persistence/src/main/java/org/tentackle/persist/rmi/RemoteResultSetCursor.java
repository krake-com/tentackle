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

import org.tentackle.dbms.rmi.RemoteDelegate;
import org.tentackle.persist.ResultSetCursor.FetchList;

import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote delegate interface for {@link org.tentackle.persist.ResultSetCursor}.
 *
 * @author  harald
 * @param <T> the type
 */
public interface RemoteResultSetCursor<T> extends RemoteDelegate {

  /**
   * Closes the scrollable resource.<br>
   * The scrollable resource is opened in its constructor.
   * Closing an already closed scrollable resource is allowed.
   *
   * @throws RemoteException if failed
   */
  void close() throws RemoteException;

  /**
   * Sets the cursor to a given row.<br>
   * Row numbers start at 1.
   *
   * @param row the row number (must be &ge; 0)
   * @return the new row number
   * @throws RemoteException if failed
   */
  int setRow(int row) throws RemoteException;

  /**
   * Rewinds the cursor to the first row.
   *
   * @return true if rewound, false if cursor is empty
   * @throws RemoteException if failed
   */
  boolean first() throws RemoteException;

  /**
   * Positions the cursor on the last row.
   *
   * @return the current row
   * @throws RemoteException if failed
   */
  int last() throws RemoteException;

  /**
   * Moves the cursor to the next row.
   * If there are no more rows the current row remains unchanged.
   *
   * @return true if moved, false if no more rows
   * @throws RemoteException if failed
   */
  boolean next() throws RemoteException;

  /**
   * Moves the cursor to the previous row.
   * If we are already at the beginning, the cursor remains unchanged.
   *
   * @return true if advanced, false if already at the beginning
   * @throws RemoteException if failed
   */
  boolean previous() throws RemoteException;

  /**
   * Moves the cursor a given number of rows.
   *
   * @param rows the number of rows to move, negative to move backwards
   * @return the current row
   * @throws RemoteException if failed
   */
  int scroll(int rows) throws RemoteException;

  /**
   * Positions the cursor before the first row.<br>
   * Works even for empty cursors.
   *
   * @throws RemoteException if failed
   */
  void beforeFirst() throws RemoteException;

  /**
   * Positions the cursor after the last row.<br>
   * Works even for empty cursors.
   *
   * @return the current row
   * @throws RemoteException if failed
   */
  int afterLast() throws RemoteException;

  /**
   * Checks whether the cursor is after the last row.
   *
   * @return true if after last
   * @throws RemoteException if failed
   */
  boolean isAfterLast() throws RemoteException;

  /**
   * Gets the data object of the current row.
   *
   * @return the object, null if invalid row or no such object
   * @throws RemoteException if failed
   */
  T get() throws RemoteException;

  /**
   * Returns the objects of this cursor as a list.
   *
   * @return the list
   * @throws RemoteException if failed
   */
  List<T> toList() throws RemoteException;

  /**
   * Sets the fetchsize.<br>
   * This is the number of rows the cursor will fetch
   * from the server in one batch.
   * A fetchsize of 0 means server default.
   *
   * @param rows the fetchsize
   * @throws RemoteException if failed
   */
  void setFetchSize(int rows) throws RemoteException;

  /**
   * Gets the fetchsize.
   *
   * @return the fetchsize
   * @throws RemoteException if failed
   */
  int getFetchSize() throws RemoteException;

  /**
   * Sets the fetch direction.
   *
   * @param direction the direction, see {@link java.sql.ResultSet#setFetchDirection}
   * @throws RemoteException if failed
   */
  void setFetchDirection(int direction) throws RemoteException;

  /**
   * Gets the fetch direction.
   *
   * @return the direction
   * @throws RemoteException if failed
   */
  int getFetchDirection() throws RemoteException;

  /**
   * Fetches the next objects up to the fetchsize.<br>
   * This method is provided to minimize the number of
   * roundtrips especially for remote cursors.
   * The cursor is closed at the end of the cursor.
   *
   * @return the list of objects, null if no more objects found
   * @throws RemoteException if failed
   */
  FetchList<T> fetch() throws RemoteException;

}
