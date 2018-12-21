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

import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.fx.component.delegate.FxChoiceBoxDelegate;
import org.tentackle.fx.rdc.translate.PdoComponentAddon;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Delegate for RdcChoiceBox.
 *
 * @author harald
 */
public class RdcChoiceBoxDelegate extends FxChoiceBoxDelegate {

  private final RdcBoxDelegateImpl boxImpl;
  private PdoComponentAddon addon;

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public RdcChoiceBoxDelegate(FxChoiceBox<?> component) {
    super(component);
    boxImpl = new RdcBoxDelegateImpl(component, component::getItems);
  }

  @Override
  public void setType(Class<?> type) {
    boxImpl.prepareSetType(type);
    if (PersistentDomainObject.class.isAssignableFrom(type)) {
      addon = createPdoAddon();
    }
    super.setType(type);
  }

  @Override
  public void setViewValue(Object value) {
    boxImpl.prepareSetViewValue();
    super.setViewValue(value);
  }

  /**
   * Creates the PDO style implementation.
   *
   * @return the implementation
   */
  @SuppressWarnings("unchecked")
  protected PdoComponentAddon createPdoAddon() {
    return new PdoComponentAddon(getComponent(), this::getViewValue);
  }

}
