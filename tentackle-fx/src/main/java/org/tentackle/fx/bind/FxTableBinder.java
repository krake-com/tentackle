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


package org.tentackle.fx.bind;

import org.tentackle.bind.Binder;
import org.tentackle.fx.table.TableConfiguration;

import java.util.Collection;



/**
 * The Binder that associates a table with object binding paths.
 *
 * @param <S> type of the objects contained within the table's items list
 * @author harald
 */
public interface FxTableBinder<S> extends Binder {

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to cast.
   */
  @Override
  FxTableBinding<S,?> getBinding(String bindingPath);

  /**
   * Gets the table configuration.
   *
   * @return the config
   */
  TableConfiguration<S> getTableConfiguration();

  /**
   * Gets the list of all bound column names.
   *
   * @return the list of bound columns
   */
  Collection<String> getBoundColumns();


  /**
   * Gets the list of all unbound column names.
   *
   * @return the list of unbound columns
   */
  Collection<String> getUnboundColumns();

}
