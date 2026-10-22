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

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.rdc.translate.PdoComponentAddon;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.collections.ObservableList;
import javafx.util.Callback;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Common implementation for the choice box and combo box delegates.<br>
 * Loads the items of a component bound to a PDO type and reloads them when the entity's
 * modification count changes. A PDO modified via F2 replaces its outdated entry in the item list.
 */
public class RdcBoxDelegateImpl {

  private final FxComponent component;                          // the combo box or choice box
  @SuppressWarnings("rawtypes")
  private final Supplier<ObservableList> itemSupplier;          // component::getItems
  @SuppressWarnings("rawtypes")
  private final Supplier<PdoComponentAddon> pdoAddonSupplier;   // delegate::getPdoAddon
  private long lastModificationCount;                           // 0 if no PDO, -1 if load initial count, else last count at time of loading
  private long lastPdoId;                                       // the ID of the last current PDO
  private long lastPdoSerial;                                   // the last current PDO serial
  @SuppressWarnings("rawtypes")
  private PersistentDomainObject proxy;                         // proxy PDO of the component's PDO type


  /**
   * Creates the delegate implementation.
   *
   * @param component the component
   * @param itemSupplier gets the list of items shown by the component
   * @param pdoAddonSupplier gets the PDO addon
   */
  @SuppressWarnings("rawtypes")
  public RdcBoxDelegateImpl(FxComponent component, Supplier<ObservableList> itemSupplier,
                            Supplier<PdoComponentAddon> pdoAddonSupplier) {
    this.component = component;
    this.itemSupplier = itemSupplier;
    this.pdoAddonSupplier = pdoAddonSupplier;
  }

  /**
   * Prepares the component's setType method.
   *
   * @param type the type
   */
  public void prepareSetType(Class<?> type) {
    lastModificationCount = PersistentDomainObject.class.isAssignableFrom(type) ? -1 : 0;
    lastPdoSerial = 0;
    lastPdoId = 0;
    proxy = null;
  }

  /**
   * Gets the domain context from the binding properties.
   *
   * @return the context, null if not bound or no such property
   */
  public DomainContext getDomainContext() {
    DomainContext context = null;
    FxComponentBinding binding = component.getBinding();
    if (binding != null) {
      context = binding.getBinder().getBindingProperty(DomainContext.class);
    }
    return context;
  }

  /**
   * Prepares the component's setViewValue method.<br>
   * Loads the items initially or if the modification count has changed.
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void prepareSetViewValue() {
    if (lastModificationCount != 0) {
      boolean loadItems = false;
      if (proxy == null) {
        DomainContext context = getDomainContext();
        if (context != null) {
          proxy = Pdo.create((Class<PersistentDomainObject>) component.getType(), context);
          loadItems = true;
        }
      }
      else {
        long modificationCount = proxy.getModificationCount();
        if (modificationCount != lastModificationCount) {
          lastModificationCount = modificationCount;
          loadItems = true;
        }
        else {
          PersistentDomainObject pdo = pdoAddonSupplier.get().getPdo();
          if (pdo != null) {
            if (pdo.getId() == lastPdoId && pdo.getSerial() != lastPdoSerial) {
              // current PDO was modified (F2 edit, see PdoComponentAddon) -> replace in items list
              int ndx = 0;
              ObservableList items = itemSupplier.get();
              for (Object item : items) {
                if (pdo.equals(item)) {
                  items.set(ndx, pdo);
                  break;
                }
                ndx++;
              }
            }
            lastPdoId = pdo.getId();
            lastPdoSerial = pdo.getSerial();
          }
          else {
            lastPdoId = 0;
            lastPdoSerial = 0;
          }
        }
      }

      if (loadItems) {
        if (lastModificationCount == -1) {
          lastModificationCount = proxy.getModificationCount();
        }
        ObservableList items = itemSupplier.get();
        items.setAll(pdoAddonSupplier.get().getAllPdos(proxy));
      }
    }
  }

  /**
   * Gets the callback to load all PDOs.<br>
   * Used by {@link PdoComponentAddon#getAllPdos(PersistentDomainObject)}.
   *
   * @param <T> the PDO type
   * @return the callback
   */
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> Callback<T, Collection<T>> getLoadAllPdosCallback() {
    return pdoAddonSupplier.get().getLoadAllPdosCallback();
  }

  /**
   * Sets the callback to load all PDOs.<br>
   * Used by {@link PdoComponentAddon#getAllPdos(PersistentDomainObject)}.
   *
   * @param <T> the PDO type
   * @param loadAllPdosCallback the callback
   */
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> void setLoadAllPdosCallback(Callback<T, Collection<T>> loadAllPdosCallback) {
    pdoAddonSupplier.get().setLoadAllPdosCallback(loadAllPdosCallback);
  }

}
