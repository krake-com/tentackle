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
import org.tentackle.fx.component.delegate.FxChoiceBoxDelegate;
import org.tentackle.fx.rdc.component.RdcChoiceBox;
import org.tentackle.fx.rdc.translate.PdoComponentAddon;
import org.tentackle.fx.rdc.translate.PdoTranslator;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.util.Callback;
import java.util.Collection;

/**
 * Delegate for {@link RdcChoiceBox}.
 *
 * @author harald
 */
public class RdcChoiceBoxDelegate extends FxChoiceBoxDelegate {

  private final RdcBoxDelegateImpl boxImpl;

  @SuppressWarnings("rawtypes")
  // null if not bound to a PDO type or there is an explicit PdoTranslator provided by the application
  private PdoComponentAddon pdoAddon;

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public RdcChoiceBoxDelegate(RdcChoiceBox<?> component) {
    super(component);
    boxImpl = new RdcBoxDelegateImpl(component, component::getItems, this::getPdoAddon);
  }

  @Override
  public void setType(Class<?> type) {
    boxImpl.prepareSetType(type);
    super.setType(type);
    if (getPdoAddon() == null && PersistentDomainObject.class.isAssignableFrom(type)) {
      // FxChoiceBox is not a FxTextComponent, hence PdoStringTranslator does not apply.
      // If there is no explicit PdoTranslator for the given concrete PDO type, create a default
      // PDO addon. This allows us to use FxChoiceBox for PDOs even without such a translator.
      pdoAddon = createPdoAddon();
    }
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
    return pdoAddon;    // null, if not bound to a PDO type
  }

  /**
   * Creates the PDO component addon.
   *
   * @return the addon, null if not bound to a PDO type
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected PdoComponentAddon createPdoAddon() {
    return new PdoComponentAddon(getComponent(), this::getViewValue, this::setViewValue);
  }

}
