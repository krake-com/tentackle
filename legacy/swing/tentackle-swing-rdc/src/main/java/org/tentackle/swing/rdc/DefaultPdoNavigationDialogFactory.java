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

package org.tentackle.swing.rdc;

import java.awt.Window;
import java.util.List;
import org.tentackle.common.Service;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Default factory for {@link PdoNavigationDialog}s.
 *
 * @author harald
 */
@Service(PdoNavigationDialogFactory.class)
public class DefaultPdoNavigationDialogFactory implements PdoNavigationDialogFactory {

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner,
          List<T> list, SelectionFilter selectionFilter, int buttonMode, boolean showTable) {
    return new PdoNavigationDialog<>(owner, list, selectionFilter, buttonMode, showTable);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner,
          List<T> list, SelectionFilter selectionFilter) {
    return new PdoNavigationDialog<>(owner, list, selectionFilter);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(List<T> list, SelectionFilter selectionFilter, boolean showTable)  {
    return new PdoNavigationDialog<>(list, selectionFilter, showTable);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(List<T> list, SelectionFilter selectionFilter)  {
    return new PdoNavigationDialog<>(list, selectionFilter);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner, T obj,
          SelectionFilter selectionFilter, int buttonMode, boolean showTable) {
    return new PdoNavigationDialog<>(owner, obj, selectionFilter, buttonMode, showTable);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(Window owner, T obj, SelectionFilter selectionFilter)  {
    return new PdoNavigationDialog<>(owner, obj, selectionFilter);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(T obj, SelectionFilter selectionFilter, boolean showTable)  {
    return new PdoNavigationDialog<>(obj, selectionFilter, showTable);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationDialog<T> createPdoNavigationDialog(T obj, SelectionFilter selectionFilter)  {
    return new PdoNavigationDialog<>(obj, selectionFilter);
  }

}
