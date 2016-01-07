/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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


package org.tentackle.swing;

import java.util.EventObject;

/**
 * A cell traversal event.<br>
 * Whenever a cell traversal occurs on a {@link FormTable} (see setCellTraversal())
 * an event will be sent to all {@link FormTableTraversalListener}s.
 * The event will also be triggered if a cell traversal does not take place
 * due to wrapping restrictions.<br>
 *
 * Furthermore, the event is vetoable, i.e. if one of the listeners
 * does not ack the event, the cell traversal will be cancelled.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormTableTraversalEvent extends EventObject {

  // all row/cols with respect to the view (not the data-model!)
  private final int fromRow;              // current row
  private final int fromColumn;           // current column
  private final int toRow;                // new row
  private final int toColumn;             // new column

  private final boolean editing;          // true if traversal will start editing of cell
  private final boolean next;             // true if traversal was "to next", false if "to previous" cell
  private final boolean inhibited;        // true if traversal inhibited due to wrapping restrictions


  /**
   * Creates a new traversal event.
   *
   * @param table the FormTable
   * @param inhibited true if traversal inhibited due to wrapping restrictions
   * @param fromRow the row in which the event was fired
   * @param fromColumn the column in which the event was fired
   * @param toRow the row the event is related to
   * @param toColumn the column the event is related to
   * @param editing true if traversal will start editing of the cell in toRow/toColumn
   * @param next true if traversal was "to next", false if "to previous" cell
   */
  public FormTableTraversalEvent(FormTable table,
                                 boolean inhibited,
                                 int fromRow, int fromColumn,
                                 int toRow, int toColumn,
                                 boolean editing, boolean next) {
    super(table);
    this.inhibited  = inhibited;
    this.fromRow    = fromRow;
    this.fromColumn = fromColumn;
    this.toRow      = toRow;
    this.toColumn   = toColumn;
    this.editing    = editing;
    this.next       = next;
  }


  /**
   * Gets the table.
   *
   * @return the FormTable
   */
  public FormTable getTable() {
    return (FormTable)getSource();
  }

  /**
   * Gets the row in which the event was fired.
   *
   * @return the row
   */
  public int getFromRow() {
    return fromRow;
  }

  /**
   * Gets the column in which the event was fired.
   *
   * @return the column
   */
  public int getFromColumn()  {
    return fromColumn;
  }

  /**
   * Gets the row the event is related to.
   *
   * @return the row
   */
  public int getToRow() {
    return toRow;
  }

  /**
   * Gets the column the event is related to.
   *
   * @return the column
   */
  public int getToColumn()  {
    return toColumn;
  }

  /**
   * Returns whether traversal will start editing of the cell in toRow/toColumn.
   *
   * @return true if start editing
   */
  public boolean isEditing() {
    return editing;
  }

  /**
   * Returns whether traversal was "to next", false if "to previous" cell.
   *
   * @return true if next
   */
  public boolean isNext() {
    return next;
  }

  /**
   * Returns whether traversal was inhibited due to wrapping restrictions.
   *
   * @return true if inhibited
   */
  public boolean isInhibited()  {
    return inhibited;
  }


  @Override
  public String toString()  {
    return getClass().getName() +
           " [from=" + fromRow + "/" + fromColumn + ",to=" + toRow + "/" + toColumn +
           ",edit=" + editing + ",next=" + next + ",inhibited=" + inhibited +
           ",source=" + source + "]";
  }

}
