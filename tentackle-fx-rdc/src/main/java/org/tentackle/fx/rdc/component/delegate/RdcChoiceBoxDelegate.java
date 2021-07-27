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

import org.tentackle.fx.component.delegate.FxChoiceBoxDelegate;
import org.tentackle.fx.rdc.component.RdcChoiceBox;
import org.tentackle.fx.rdc.translate.PdoComponentAddon;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Delegate for RdcChoiceBox.
 *
 * @author harald
 */
public class RdcChoiceBoxDelegate extends FxChoiceBoxDelegate {

  private final RdcBoxDelegateImpl boxImpl;
  @SuppressWarnings("rawtypes")
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
    if (PersistentDomainObject.class.isAssignableFrom(type)) {
      setPdoAddon(createPdoAddon());
    }
    super.setType(type);
  }

  @Override
  public void setViewValue(Object value) {
    boxImpl.prepareSetViewValue();
    super.setViewValue(value);
  }

  /**
   * Gets the PDO addon.
   *
   * @return the addon
   */
  @SuppressWarnings("rawtypes")
  public PdoComponentAddon getPdoAddon() {
    return pdoAddon;
  }

  /**
   * Sets the PDO addon.
   *
   * @param pdoAddon the addon
   */
  @SuppressWarnings("rawtypes")
  public void setPdoAddon(PdoComponentAddon pdoAddon) {
    this.pdoAddon = pdoAddon;
  }

  /**
   * Creates the PDO style implementation.
   *
   * @return the implementation
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected PdoComponentAddon createPdoAddon() {
    return new PdoComponentAddon(getComponent(), this::getViewValue, this::setViewValue);
  }

}
