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

package org.tentackle.fx.rdc.component.delegate;

import javafx.collections.ObservableList;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.function.Supplier;

/**
 * Common implementation for choice- and combobox delegates.
 */
public class RdcBoxDelegateImpl {

  private final FxComponent component;
  private final Supplier<ObservableList> itemSupplier;
  private boolean loadPdosPending;

  /**
   * Creates the delegate impl.
   *
   * @param component the component
   * @param itemSupplier gets the list of items shown by the component
   */
  public RdcBoxDelegateImpl(FxComponent component, Supplier<ObservableList> itemSupplier) {
    this.component = component;
    this.itemSupplier = itemSupplier;
  }

  /**
   * Prepare the component's setType method.
   *
   * @param type the type
   */
  public void prepareSetType(Class<?> type) {
    if (PersistentDomainObject.class.isAssignableFrom(type)) {
      loadPdosPending = true;
    }
  }

  /**
   * Prepare the component's setViewValue method.
   */
  @SuppressWarnings("unchecked")
  public void prepareSetViewValue() {
    if (loadPdosPending) {
      loadPdosPending = false;
      FxComponentBinding binding = component.getBinding();
      DomainContext context = binding.getBinder().getBindingProperty(DomainContext.class);
      if (context != null) {
        PersistentDomainObject pdo = Pdo.create((Class<PersistentDomainObject>) component.getType(), context);
        ObservableList items = itemSupplier.get();
        items.clear();
        items.addAll(pdo.selectAllCached());
      }
    }
  }
}
