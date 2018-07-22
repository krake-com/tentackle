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

import javafx.scene.control.Button;

import org.tentackle.fx.ConfiguratorService;

/**
 * Configures a Button.
 *
 * @author harald
 * @param <T> the button type
 */
@ConfiguratorService(Button.class)
public class ButtonConfigurator<T extends Button> extends ButtonBaseConfigurator<T> {

  @Override
  public void configure(T button) {
    // no super.configure: don't remap the keys

    // by default, buttons are not traversable in a desktop application
    button.setFocusTraversable(false);
  }

}
