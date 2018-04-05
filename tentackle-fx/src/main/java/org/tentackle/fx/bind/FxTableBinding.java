/*
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


package org.tentackle.fx.bind;

import org.tentackle.bind.Binding;
import org.tentackle.fx.table.TableColumnConfiguration;


/**
 * Binding between a class member and a table model column.
 *
 * @param <S> type of the objects contained within the table's items list
 * @param <T> type of the content in all cells in this column
 * @author harald
 */
public interface FxTableBinding<S,T> extends Binding {

  @Override
  FxTableBinder<S> getBinder();

  @Override
  T getModelValue();

  /**
   * Gets the column configuration.
   *
   * @return the config
   */
  TableColumnConfiguration<S,T> getConfiguration();

  /**
   * Gets the row object.
   *
   * @return the row object for this binding
   */
  S getBoundRootObject();

  /**
   * Sets the row object.
   *
   * @param rowObject the row object for this binding
   */
  void setBoundRootObject(S rowObject);

}
