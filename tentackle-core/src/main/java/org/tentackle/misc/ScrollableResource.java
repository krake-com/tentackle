/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.misc;

import java.util.List;

/**
 * A scrollable resource.<br>
 * A {@code ScrollableResource} is a wrapper around a resource. It provides
 * the concept of a current row pointing to an object derived from the resource.
 * Row numbers start at 1.
 * It is open by default and must be closed to release the resource.
 *
 * @author harald
 * @param <T> the type
 */
public interface ScrollableResource<T> extends AutoCloseable {

  /**
   * Gets the current row.<br>
   * Row numbers start at 1.
   *
   * @return the current row, 0 if before first row
   */
  int getRow();

  /**
   * Sets the scrollable resource to a given row.<br>
   * Row numbers start at 1.
   *
   * @param row the row number (must be &ge; 0)
   * @return true if done, false if no such row and the resource is now positioned before the first row or after the last row
   */
  boolean setRow (int row);

  /**
   * Rewinds the scrollable resource to the first row.
   *
   * @return true if rewound, false if scrollable resource is empty
   */
  boolean first();

  /**
   * Positions the scrollable resource on the last row.
   *
   * @return true if positioned, false if scrollable resource is empty
   */
  boolean last();

  /**
   * Moves the scrollable resource to the next row.<br>
   * If there are no more rows the current row remains unchanged.
   *
   * @return true if moved, false if no more rows
   */
  boolean next();

  /**
   * Moves the scrollable resource a given number of rows.
   *
   * @param rows the number of rows to move, negative to move backwards
   * @return true if moved, false if no such row and the resource is now positioned before the first row or after the last row
   */
  boolean scroll(int rows);

  /**
   * Moves the scrollable resource to the previous row.<br>
   * If we are already at the beginning, the scrollable resource remains unchanged.
   *
   * @return true if moved, false if already at the beginning
   */
  boolean previous();

  /**
   * Positions the scrollable resource before the first row.<br>
   * Works even for empty scrollable resources.
   */
  void beforeFirst();

  /**
   * Positions the scrollable resource after the last row.<br>
   * Works even for empty scrollable resources.
   */
  void afterLast();

  /**
   * Checks whether the scrollable resource is before the first row.
   *
   * @return true if before first
   */
  boolean isBeforeFirst();

  /**
   * Checks whether the scrollable resource is after the last row.
   *
   * @return true if after last
   */
  boolean isAfterLast();

  /**
   * Gets the object of the current row.
   *
   * @return the object, null if invalid row or no such object
   */
  T get();

  /**
   * Returns the objects of this scrollable resource as a list.
   *
   * @return the list (never null)
   */
  List<T> toList();

  /**
   * Returns the objects of this scrollable resource as a list and closes this scrollable resource.
   *
   * @return the list (never null)
   */
  List<T> toListAndClose();

  /**
   * Closes the scrollable resource.<br>
   * The scrollable resource is opened in its constructor.
   * Closing an already closed scrollable resource is allowed.
   */
  @Override
  void close();

  /**
   * Returns whether the scrollable resource is closed.
   *
   * @return true if closed
   */
  boolean isClosed();

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
   * Fetches the next objects up to the fetch size.<br>
   * This method is provided to minimize the number of
   * roundtrips, especially for remote cursors.
   * The cursor is closed when its end is reached.
   *
   * @return the list of objects, null if no more objects found
   */
  List<T> fetch();

}
