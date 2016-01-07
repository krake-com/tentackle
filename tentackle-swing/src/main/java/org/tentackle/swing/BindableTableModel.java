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

import javax.swing.table.TableModel;
import org.tentackle.swing.bind.FormTableBinder;
import org.tentackle.swing.bind.FormTableBinding;

/**
 *
 * @param <T> the type
 * @author harald
 */
public interface BindableTableModel<T> extends TableModel {

  /**
   * The binding type.<br>
   * Determines the kind of binding, or none at all.
   */
  public static enum BindType {
    /** don't bind */
    NONE,
    /** bind declared only */
    BIND,
    /** bind inherited */
    BINDALL
  }



  /**
   * Gets the binder of this container.
   *
   * @return the binder, never null.
   */
  public FormTableBinder getBinder();


  /**
   * Gets the binding for a given column.
   *
   * @param columnIndex the model column
   * @return the binding, null if column not bound
   */
  public FormTableBinding getBinding(int columnIndex);


  /**
   * Determines whether the model using this entry is bindable and if so, which kind of binding to use.
   *
   * @return the binding type
   */
  public BindType getBindType();


  /**
   * Gets the binding path for given model column.<br>
   * The returned path is relative to the object described by this table entry.
   *
   * @param mColumn the datamodel-column
   * @return the binding path, null if not bound
   */
  public String getBindingPath(int mColumn);


  /**
   * Gets the root class where to start scan for {@link Bindable}s.
   * <p>
   * For {@link FormTableModel}s this is the class of the {@link FormTableEntry}.
   * Other models may refer to other classes.
   *
   * @return the class to start the scan
   */
  public Class<?> getBindingRootClass();


  /**
   * Gets the table.
   *
   * @return the form table or null if this model is not the direct data model for the table
   */
  public FormTable<T> getTable();


  /**
   * Sets the table.
   *
   * @param table the form table
   */
  public void setTable(FormTable<T> table);

}
