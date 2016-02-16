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



package org.tentackle.swing;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.event.TableModelEvent;



/**
 * A table model and mapper to sort rows.
 *
 * @param <T> the sorted type
 * @author harald
 */
@SuppressWarnings("serial")
public class FormTableSorter<T> extends FormTableMap<T> implements MouseListener {

  private boolean   mouseListenerActive;  // registered table for mouseEvents, false = none.
  private int[]     map;                  // index map for sorted objects
  private int[]     sortby;               // column-indexes for sorting
  private boolean   sortdone;             // true if sorted, i.e. next mouse clears sortby
  private boolean   sorting;              // true if sorting in progress
  private FormTableEntry<T> sumEntry;     // != null if last line contains a fixed sum line


  /**
   * Create a form table sorter on top of a given table model.
   *
   * @param model the chained table model
   */
  public FormTableSorter (AbstractFormTableModel<T> model) {
    super(model);
    clearSorting();
    clearMapping();
  }



  /**
   * {@inheritDoc}
   * <p>
   * If the table is set the selections will be kept across sortings.
   * Otherwise the selections will be cleared.
   * A mouse listener will also be registered for the table.
   * @see #addMouseListenerForHeaderOfTable()
   */
  @Override
  public void setTable(FormTable<T> table) {
    removeMouseListenerForHeaderOfTable();    // remove if some is registered (paranoic....)
    super.setTable(table);
    addMouseListenerForHeaderOfTable();       // add listener (as this makes always sense)
  }



  /**
   * Sets a table entry to sum up the rows.
   * Will always be appended to the end of the table.
   *
   * @param sumEntry the summing table entry
   */
  public void setSumEntry(FormTableEntry<T> sumEntry)  {
    int rows = getRowCount();
    if (sumEntry != null) {
      if (this.sumEntry == null)  {
        this.sumEntry = sumEntry;
        fireTableRowsInserted(rows, rows);
      }
      else  {
        this.sumEntry = sumEntry;
        rows--;
        fireTableRowsUpdated(rows, rows);
      }
    }
    else  {
      // remove it
      if (this.sumEntry != null)  {
        this.sumEntry = null;
        rows--;
        fireTableRowsDeleted(rows, rows);
      }
    }
  }


  /**
   * @return the sumEntry, null if none
   */
  public FormTableEntry<T> getSumEntry() {
    return sumEntry;
  }


  @Override
  public int getRowCount() {
    if (sumEntry != null) {
      return super.getRowCount() + 1;
    }
    return super.getRowCount();
  }


  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    if (rowIndex >= 0 && rowIndex < map.length) {
      return model.getValueAt(map[rowIndex], columnIndex);
    }
    else if (sumEntry != null && rowIndex == map.length) {
      return sumEntry.getValueAt(columnIndex);
    }
    return null;
  }


  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (rowIndex >= 0 && rowIndex < map.length) {
      model.setValueAt(aValue, map[rowIndex], columnIndex);
    }
  }


  @Override
  public boolean isCellEditable (int rowIndex, int columnIndex) {
    return rowIndex >= 0 && rowIndex < map.length && model.isCellEditable(map[rowIndex], columnIndex);
  }


  @Override
  public FormTableEntry<T> getEntryAt (int rowIndex) {
    if (rowIndex >= 0 && rowIndex < map.length) {
      return model.getEntryAt(map[rowIndex]);
    }
    // the sumEntry is NOT returned, i.e. null means: sumEntry
    return null;
  }

  @Override
  public int getMappedIndex(int mappedRow) {
    for (int i=0; i < map.length; i++)  {
      if (map[i] == mappedRow) {
        return i;
      }
    }
    return -1;
  }


  @Override
  public int[] getMappedIndex(int[] mappedRows)  {
    if (mappedRows != null)  {
      int[] rows = new int[mappedRows.length];
      for (int i=0; i < mappedRows.length; i++)  {
        rows[i] = getMappedIndex(mappedRows[i]);
      }
      return rows;
    }
    return null;
  }


  @Override
  public int getModelIndex(int row) {
    if (row >= 0 && row < map.length) {
      return map[row];
    }
    return -1;
  }

  @Override
  public int[] getModelIndex(int[] rows)  {
    if (rows != null)  {
      int[] modelRows = new int[rows.length];
      for (int i=0; i < rows.length; i++)  {
        modelRows[i] = getModelIndex(rows[i]);
      }
      return modelRows;
    }
    return null;
  }


  /**
   * Adds a sort index.
   *
   * @param column the column index to add
   */
  public void addSortIndex (int column)  {
    int i;
    int[] newsortby = new int[sortby.length + 1];
    for (i=0; i < sortby.length; i++) {
      if (sortby[i] == column || sortby[i] == -column -1) {
        return; // already in list
      }
      newsortby[i] = sortby[i];
    }
    newsortby[i] = column;
    sortby = newsortby;
    fireSortingChanged();
  }


  /**
   * Do the sort
   */
  public void sort() {

    int[] selectedRows = null;

    FormTable<T> table = getTable();

    if (table != null)  {
      // remember selections
      selectedRows = getModelIndex(table.getSelectedRows());
    }

    sorting = true;
    fireSortingChanged();     // tell that we are sorting now

    clearMapping();           // revert to original mapping

    if (sortby.length > 0)  {
      // implements a simple quicksort
      qsort (0, map.length-1);
    }

    fireTableDataChanged();   // display new sorted data

    sorting = false;

    fireSortingChanged();     // tell that we finished sorting

    if (table != null)  {
      // restore selections
      table.setSelectedRows(getMappedIndex(selectedRows));
    }
  }


  /**
   * Clears the mapping.
   * Sets a 1:1 mapping for the table.
   */
  public void clearMapping() {
    // setup identity object mapping
    map = new int[model.getRowCount()];
    for (int i=0; i < map.length; i++) {
      map[i] = i;
    }
    sortdone = true; // next mouse-clicks will setup a new sorting order!
  }


  /**
   * Clears the sorting
   */
  public void clearSorting() {
    sortby   = new int[0];
    sortdone = false;
    fireSortingChanged();
  }


  /**
   * Gets the sort indexes.
   *
   * @return the array of column indexes, null if no sorting
   */
  public int[] getSorting() {
    return sortby;
  }


  /**
   * Sets the sort indexes
   *
   * @param sortby the array of column indexes, null to clear sorting
   */
  public void setSorting(int[] sortby)  {
    if (sortby == null) {
      clearSorting();
    }
    else  {
      this.sortby = sortby;
      sortdone = false;
      fireSortingChanged();
    }
  }


  /**
   * Determines whether the table is sorted.
   *
   * @return true if sorted
   */
  public boolean isSorted() {
    return sortby.length > 0;
  }


  /**
   * Gets a string containing the (displayed) field-names of the current sorting.
   *
   * @return fieldnames seperated by colons, null if no sorting.
   */
  public String getSortNames()  {

    if (sortby.length == 0 || getRowCount() == 0) {
      return null;
    }

    String str;
    if (sorting)  {
      str = "* ";
    }
    else if (sortdone)  {
      str = "";
    }
    else  {
      str = "? ";
    }

    FormTableEntry<T> entry = getTemplate();
    for (int i=0; i < sortby.length; i++) {
      if (i > 0) {
        str += ", ";
      }
      int ndx = sortby[i];
      if (ndx >= 0) {
        str += entry.getDisplayedColumnName(ndx);
      }
      else {
        str += "-" + entry.getDisplayedColumnName(-ndx -1);
      }
    }
    return str;
  }


  /**
   * Table data has changed from unterlying tablemodel:
   * sort data and redisplay.<p>
   * {@inheritDoc}
   */
  @Override
  public void tableChanged(TableModelEvent e) {
    // check if we need to sort
    boolean needsort = false;
    if (map == null || map.length != model.getRowCount()) {
      // more or fewer rows
      needsort = true;
    }
    else  {
      // some other change in data
      int col = e.getColumn();
      if (col == TableModelEvent.ALL_COLUMNS) {
        needsort = true;
      }
      else  {
        for (int i=0; i < sortby.length; i++) {
          if (sortby[i] == col) {
            // column is part of sorting order
            needsort = true;
            break;
          }
        }
      }
    }

    if (needsort) {
      sort();
      // sort will fireTableDateChanged() so we can omit super.tablesChanged() here.
    }
    else  {
      super.tableChanged(e);
    }
  }



  /**
   * Adds a property change listener (used if sorting has changed)
   * @param listener the listener to add
   */
  public synchronized void addPropertyChangeListener (PropertyChangeListener listener)  {
    listenerList.add (PropertyChangeListener.class, listener);
  }

  /**
   * Remove a property change listener.
   * @param listener the listener to remove
   */
  public synchronized void removePropertyChangeListener (PropertyChangeListener listener) {
     listenerList.remove (PropertyChangeListener.class, listener);
  }


  /**
   * Installs a Mouse-Listener to the table header.<br>
   * Mouse clicks on a column header will be treated as follows:
   * <ul>
   * <li>single click: add column to sort criteria</li>
   * <li>double click: add column and run the sort</li>
   * </ul>
   * Pressing the shift key switches to descending order for the column.
   */
  public void addMouseListenerForHeaderOfTable() {
    FormTable<T> table = getTable();
    if (table != null && !mouseListenerActive)  {
      table.getTableHeader().addMouseListener(this);
      mouseListenerActive = true;
    }
  }


  /**
   * Removes the mouse header listeners.
   */
  public void removeMouseListenerForHeaderOfTable()  {
    FormTable<T> table = getTable();
    if (table != null && mouseListenerActive)  {
      table.getTableHeader().removeMouseListener(this);
      mouseListenerActive = false;
    }
  }


  @Override
  public void mouseClicked(MouseEvent e) {
    final FormTable<T> table = getTable();
    if (table != null && e.getButton() == MouseEvent.BUTTON1)  {
      // get the column viewed by the user
      int vCol = table.getColumnModel().getColumnIndexAtX(e.getX());
      // translate to the internal column of the model
      int col  = table.convertColumnIndexToModel(vCol);
      if (col >= 0)  { // if a real column
        // check if starting a new sorting
        if (sortdone) {
          clearSorting(); // new sorting order begins
        }
        // check if shift key pressed
        if ((e.getModifiers() & InputEvent.SHIFT_MASK) != 0) {
          // means descending order
          col = -col - 1;
        }
        // add column to sorting order
        addSortIndex(col);

        if (e.getClickCount() == 2) { // double click
          /**
           * do the sort.
           *
           * We cannot run the sort in an extra thread, because there is a high
           * possibility that sorting requires db-accesses and this might interfere
           * with transactions the user is doing during lengthy sorts.
           */
          FormUtilities.getInstance().setWaitCursor(table);
          sort();
          FormUtilities.getInstance().setDefaultCursor(table);
        }
      }
    }
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {
  }

  @Override
  public void mousePressed(MouseEvent e) {
  }

  @Override
  public void mouseReleased(MouseEvent e) {
  }









  /**
   * simple quick sort
   *
   * @param lower first index where to start sorting
   * @param upper last index where to end sorting
   */
  @SuppressWarnings("unchecked")
  private void qsort (int lower, int upper) {
    if (lower < upper)  {
      // at least one element to sort!
      int left   = lower;
      int right  = upper;
      int middle = (lower + upper) >> 1;
      while (left < right)  {
        // compare left and middle
        if (getEntryAt(middle).compareTo(getEntryAt(left), sortby) < 0) {
          // middle is less than left entry
          swap (left, middle);
          middle = left;
        }
        // compare right and middle
        if (getEntryAt(middle).compareTo(getEntryAt(right), sortby) > 0) {
          // middle is greater than right entry
          swap (right, middle);
          middle = right;
        }
        // move pointers
        if (left  < middle) {
          left++;
        }
        if (right > middle) {
          right--;
        }
      }
      // sort the rest
      qsort (lower,    middle-1);
      qsort (middle+1, upper);
    }
  }

  /**
   * swaps mappings
   */
  private void swap (int idx1, int idx2) {
    int saved = map[idx1];
    map[idx1] = map[idx2];
    map[idx2] = saved;
  }


  /**
   * fire the property change event
   */
  private void fireSortingChanged () {
    PropertyChangeListener[] pl = listenerList.getListeners(PropertyChangeListener.class);
    PropertyChangeEvent pe = new PropertyChangeEvent(this, "sorting", null, null);
    for (int i=0; i < pl.length; i++) {
      pl[i].propertyChange(pe);
    }
  }


}
