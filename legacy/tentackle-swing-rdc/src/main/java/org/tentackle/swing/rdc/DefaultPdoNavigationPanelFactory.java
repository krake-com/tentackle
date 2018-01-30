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

import java.util.List;
import org.tentackle.common.Service;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Default factory for {@link PdoNavigationPanel}s.
 *
 * @author harald
 */
@Service(PdoNavigationPanelFactory.class)
public class DefaultPdoNavigationPanelFactory implements PdoNavigationPanelFactory {

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list,
          SelectionFilter selectionFilter,
          int buttonMode, boolean showTable, String tableName) {
    return new PdoNavigationPanel<>(list, selectionFilter, buttonMode, showTable, tableName);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list,
          SelectionFilter selectionFilter, int buttonMode, boolean showTable) {
    return new PdoNavigationPanel<>(list, selectionFilter, buttonMode, showTable);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list,
          SelectionFilter selectionFilter) {
    return new PdoNavigationPanel<>(list, selectionFilter);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(List<T> list) {
    return new PdoNavigationPanel<>(list);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj,
          SelectionFilter selectionFilter, int buttonMode, boolean showTable, String tableName) {
    return new PdoNavigationPanel<>(obj, selectionFilter, buttonMode, showTable, tableName);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj,
          SelectionFilter selectionFilter, int buttonMode, boolean showTable) {
    return new PdoNavigationPanel<>(obj, selectionFilter, buttonMode, showTable);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj,
          SelectionFilter selectionFilter) {
    return new PdoNavigationPanel<>(obj, selectionFilter);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoNavigationPanel<T> createPdoNavigationPanel(T obj) {
    return new PdoNavigationPanel<>(obj);
  }

}
