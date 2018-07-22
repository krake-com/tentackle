/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.fx.component.config;

import javafx.scene.control.Control;

import org.tentackle.fx.Configurator;
import org.tentackle.fx.FxUtilities;

/**
 * Configures a component.
 * <p>
 * The implementation allows overriding the base methods for a single configurator
 * or all configurators via {@link FxUtilities}.
 *
 * @author harald
 * @param <T> the control type
 */
public class ComponentConfigurator<T extends Control> implements Configurator<T> {

  /**
   * Configures the control.
   *
   * @param control the control
   */
  @Override
  public void configure(T control) {
    remapKeys(control);
    filterKeys(control);
    setupFocusHandling(control);
  }


  /**
   * Adds focus handling to sync with model.
   *
   * @param control the control
   */
  protected void setupFocusHandling(T control) {
    FxUtilities.getInstance().setupFocusHandling(control);
  }


  /**
   * Remaps certain keys.
   *
   * @param control the control
   */
  protected void remapKeys(T control) {
    FxUtilities.getInstance().remapKeys(control);
  }


  /**
   * Filters certain keys for special features.
   *
   * @param control the control
   */
  protected void filterKeys(T control) {
    FxUtilities.getInstance().filterKeys(control);
  }

}
