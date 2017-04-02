/*
 * Tentackle - a framework for java desktop applications
 * http://www.tentackle.org
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

package org.tentackle.fx.rdc.component.delegate;

import javafx.collections.ObservableList;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.component.FxComboBox;
import org.tentackle.fx.component.delegate.FxComboBoxDelegate;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Delegate for RdcComboBox.
 *
 * @author harald
 */
public class RdcComboBoxDelegate extends FxComboBoxDelegate {

  private boolean loadPdosPending;

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public RdcComboBoxDelegate(FxComboBox<?> component) {
    super(component);
  }

  @Override
  public void setType(Class<?> type) {
    super.setType(type);
    if (PersistentDomainObject.class.isAssignableFrom(type)) {
      loadPdosPending = true;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setViewValue(Object value) {
    if (loadPdosPending) {
      loadPdosPending = false;
      FxComponentBinding binding = getComponent().getBinding();
      DomainContext context = binding.getBinder().getBindingProperty(DomainContext.class);
      if (context != null) {
        PersistentDomainObject pdo = Pdo.create((Class<PersistentDomainObject>) getType(), context);
        ObservableList items = getComponent().getItems();
        items.clear();
        items.addAll(pdo.selectAll());
      }
    }

    super.setViewValue(value); //To change body of generated methods, choose Tools | Templates.
  }

}
