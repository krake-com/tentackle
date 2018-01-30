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


package org.tentackle.swing.bind;

import org.tentackle.bind.Binder;
import org.tentackle.swing.BindableTableModel;



/**
 * The Binder that associates a formtable with object binding paths.
 *
 * @param <S> type of the objects contained within the table's items list
 * @author harald
 */
public interface FormTableBinder<S> extends Binder {

  /**
   * Gets the table model.
   *
   * @return the bound table model
   */
  BindableTableModel<S> getModel();


  /**
   * Gets a binding by component.
   *
   * @param mColumn the model column
   * @return the binding, null if column not bound
   */
  FormTableBinding getBinding(int mColumn);

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to cast.
   */
  @Override
  FormTableBinding getBinding(String bindingPath);


  /**
   * Gets the list of all bound components of the table..
   *
   * @return the list of bound components, never null
   */
  Integer[] getBoundColumns();


  /**
   * Gets the list of all unbound components of the table.
   * @return the list of unbound components, never null
   */
  Integer[] getUnboundColumns();


  /**
   * Programmatically removes a binding from the table.
   *
   * @param mColumn the model column
   *
   * @return the removed binding, null if no such binding found
   */
  FormTableBinding removeBinding(int mColumn);

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to cast.
   */
  @Override
  FormTableBinding removeBinding(String bindingPath);

}
