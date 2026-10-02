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

package org.tentackle.persist.trip;

import org.tentackle.dbms.trip.RemoteDbDelegate;
import org.tentackle.persist.ResultSetCursor.FetchList;

import java.util.List;

/**
 * Remote delegate interface for {@link org.tentackle.persist.ResultSetCursor}.<br>
 * The server-side implementation wraps a local cursor, which is created along with the delegate.
 *
 * @author  harald
 * @param <T> the type
 */
public interface RemoteResultSetCursor<T> extends RemoteDbDelegate {

  /**
   * Closes the cursor.<br>
   * Closing an already closed cursor is allowed.
   */
  void close();

  /**
   * Sets the cursor to a given row.<br>
   * Row numbers start at 1.
   *
   * @param row the row number (must be &ge; 0)
   * @return the current row number after positioning
   */
  int setRow(int row);

  /**
   * Rewinds the cursor to the first row.
   *
   * @return true if rewound, false if cursor is empty
   */
  boolean first();

  /**
   * Positions the cursor on the last row.
   *
   * @return the current row, 0 if cursor is empty
   */
  int last();

  /**
   * Moves the cursor to the next row.<br>
   * If there are no more rows the current row remains unchanged.
   *
   * @return true if moved, false if no more rows
   */
  boolean next();

  /**
   * Moves the cursor to the previous row.<br>
   * If there is no previous row, the cursor is positioned before the first row.
   *
   * @return true if moved, false if already at or before the first row
   */
  boolean previous();

  /**
   * Moves the cursor a given number of rows.
   *
   * @param rows the number of rows to move, negative to move backwards
   * @return the current row number after positioning
   */
  int scroll(int rows);

  /**
   * Positions the cursor before the first row.<br>
   * Works even for empty cursors.
   */
  void beforeFirst();

  /**
   * Positions the cursor after the last row.<br>
   * Works even for empty cursors.
   *
   * @return the current row
   */
  int afterLast();

  /**
   * Checks whether the cursor is after the last row.
   *
   * @return true if after last
   */
  boolean isAfterLast();

  /**
   * Gets the PDO of the current row.
   *
   * @return the PDO, null if invalid row or no such PDO
   */
  T get();

  /**
   * Returns the PDOs of this cursor as a list.<br>
   * The cursor is rewound to the first row before.
   *
   * @return the list
   */
  List<T> toList();

  /**
   * Sets the fetch size.<br>
   * This is the number of rows the cursor will fetch
   * from the server in one batch.
   * A fetch size of 0 means server default.
   *
   * @param rows the fetch size
   */
  void setFetchSize(int rows);

  /**
   * Gets the fetch size.
   *
   * @return the fetch size
   */
  int getFetchSize();

  /**
   * Sets the fetch direction.
   *
   * @param direction the direction, see {@link java.sql.ResultSet#setFetchDirection}
   */
  void setFetchDirection(int direction);

  /**
   * Gets the fetch direction.
   *
   * @return the direction
   */
  int getFetchDirection();

  /**
   * Fetches the next PDOs up to the fetch size.<br>
   * This method is provided to minimize the number of
   * round trips.
   * The cursor is closed when its end is reached, and the returned list is marked as closed.
   *
   * @return the list of PDOs, null if the cursor is already closed or no more PDOs found
   */
  FetchList<T> fetch();

}
