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

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;


/**
 * Generic mapping of the rows within a FormTable.<br>
 * Implementing a sorter is easily done by subclassing FormTableMap.
 *
 * @param <T> the model's type
 * @author harald
 */
@SuppressWarnings("serial")
public class FormTableMap<T> extends AbstractFormTableModel<T>
                          implements TableModelListener {

  /** chained table model **/
  protected AbstractFormTableModel<T> model;


  /**
   * Creates a form table map on top of a given table model.
   *
   * @param model the chained table model
   */
  public FormTableMap(AbstractFormTableModel<T> model) {
    setModel(model);
  }

  /**
   * Gets the table model this table mapper maps.
   *
   * @return the table model
   */
  public AbstractFormTableModel<T> getModel() {
    return model;
  }

  /**
   * Sets the table model.
   *
   * @param model the table model
   */
  public void setModel(AbstractFormTableModel<T> model) {
    AbstractFormTableModel<T> oldModel = this.model;
    this.model = model;
    if (model != null)  {
      model.addTableModelListener(this);
      model.setMap(this);
    }
    if (oldModel != null)  {
      oldModel.removeTableModelListener(this);
      oldModel.setMap(null);
    }
  }



  @Override
  public FormTableEntry<T> getTemplate() {
    return model.getTemplate();
  }


  /**
   * {@inheritDoc}
   * <p>
   * This method must be overridden for another mapping than 1:1.
   */
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return model.getValueAt(rowIndex, columnIndex);
  }

  /**
   * {@inheritDoc}
   * <p>
   * This method must be overridden for another mapping than 1:1.
   */
  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    model.setValueAt(aValue, rowIndex, columnIndex);
  }


  @Override
  public FormTableEntry<T> getEntryAt (int rowIndex) {
    return model.getEntryAt(rowIndex);
  }


  @Override
  public int getRowCount() {
    return (model == null) ? 0 : model.getRowCount();
  }

  @Override
  public int getColumnCount() {
    return (model == null) ? 0 : model.getColumnCount();
  }

  @Override
  public String getColumnName(int column) {
    return model.getColumnName(column);
  }

  @Override
  public String getDisplayedColumnName(int column) {
    return model.getDisplayedColumnName(column);
  }

  @Override
  public Class<?> getColumnClass(int col) {
    return model.getColumnClass(col);
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return model.isCellEditable(rowIndex, columnIndex);
  }


  @Override
  public int getMappedIndex(int mappedRow) {
    return mappedRow;
  }


  @Override
  public int[] getMappedIndex(int[] mappedRows)  {
    return mappedRows;
  }


  @Override
  public int getModelIndex(int row) {
    return row;
  }


  @Override
  public int[] getModelIndex(int[] rows)  {
    return rows;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Event handler for TableModelListener: table data has changed.<br>
   * This method must be overridden for another mapping than 1:1.
   * The default implementation just invokes fireTableChanged.
   *
   * @param e the table model event
   */
  @Override
  public void tableChanged(TableModelEvent e) {
    fireTableChanged(e);
  }

  @Override
  public boolean isDataChanged() {
    return model.isDataChanged();
  }

  @Override
  public void setDataChanged(boolean dataChanged) {
    model.setDataChanged(dataChanged);
  }

}
