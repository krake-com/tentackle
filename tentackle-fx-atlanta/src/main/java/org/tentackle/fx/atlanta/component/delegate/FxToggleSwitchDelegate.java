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

package org.tentackle.fx.atlanta.component.delegate;

import org.tentackle.fx.FxComponentDelegate;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.atlanta.component.FxToggleSwitch;

import javafx.scene.Parent;

/**
 * Delegate for {@link FxToggleSwitch}.
 *
 * @author harald
 */
public class FxToggleSwitchDelegate extends FxComponentDelegate {

  private final FxToggleSwitch component;   // the component

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxToggleSwitchDelegate(FxToggleSwitch component) {
    this.component = component;
  }

  @Override
  public FxToggleSwitch getComponent() {
    return component;
  }

  @Override
  public FxContainer getParentContainer() {
    Parent parent = component.getParent();
    return parent instanceof FxContainer ? (FxContainer) parent : null;
  }


  @Override
  public void setType(Class<?> type) {
    // important to set the type before creating the translator,
    // since the translator may need to know the type
    super.setType(type);
    if (getValueTranslator() == null) {    // if not already set by application
      setValueTranslator(FxFactory.getInstance().createValueTranslator(type, Boolean.class, getComponent()));
    }
  }

  @Override
  public Boolean getViewObject() {
    return component.isSelected();
  }

  @Override
  public void setViewObject(Object viewObject) {
    component.setSelected(viewObject != null && (Boolean) viewObject);
  }

}
