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

import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.delegate.FxComboBoxDelegate;
import org.tentackle.fx.rdc.component.RdcComboBox;
import org.tentackle.fx.rdc.translate.PdoComponentAddon;
import org.tentackle.fx.rdc.translate.PdoTranslator;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.util.Callback;
import java.util.Collection;

/**
 * Delegate for {@link RdcComboBox}.
 *
 * @author harald
 */
public class RdcComboBoxDelegate extends FxComboBoxDelegate {

  private final RdcBoxDelegateImpl boxImpl;

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public RdcComboBoxDelegate(RdcComboBox<?> component) {
    super(component);
    boxImpl = new RdcBoxDelegateImpl(component, component::getItems, this::getPdoAddon);
  }

  @Override
  public void setType(Class<?> type) {
    boxImpl.prepareSetType(type);
    super.setType(type);
  }

  @Override
  public void setViewValue(Object value) {
    boxImpl.prepareSetViewValue();
    super.setViewValue(value);
  }

  /**
   * Gets the domain context from the binding properties.
   *
   * @return the context, null if not bound or no such property
   */
  public DomainContext getDomainContext() {
    return boxImpl.getDomainContext();
  }

  /**
   * Gets the callback to load all PDOs.<br>
   * Used by {@link PdoComponentAddon#getAllPdos(PersistentDomainObject)}.
   *
   * @param <T> the PDO type
   * @return the callback
   */
  public <T extends PersistentDomainObject<T>> Callback<T, Collection<T>> getLoadAllPdosCallback() {
    return boxImpl.getLoadAllPdosCallback();
  }

  /**
   * Sets the callback to load all PDOs.<br>
   * Used by {@link PdoComponentAddon#getAllPdos(PersistentDomainObject)}.
   *
   * @param <T> the PDO type
   * @param loadAllPdosCallback the callback
   */
  public <T extends PersistentDomainObject<T>> void setLoadAllPdosCallback(Callback<T, Collection<T>> loadAllPdosCallback) {
    boxImpl.setLoadAllPdosCallback(loadAllPdosCallback);
  }

  /**
   * Gets the PDO addon.
   *
   * @return the addon, null if not bound to a PDO type
   */
  @SuppressWarnings("rawtypes")
  public PdoComponentAddon getPdoAddon() {
    ValueTranslator translator = getValueTranslator();
    if (translator instanceof PdoTranslator pdoTranslator) {
      return pdoTranslator.getPdoAddon();
    }
    return null;    // not bound to a PDO type
  }

}
