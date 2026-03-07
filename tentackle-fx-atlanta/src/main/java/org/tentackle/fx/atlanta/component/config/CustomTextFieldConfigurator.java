/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.fx.atlanta.component.config;

import atlantafx.base.controls.CustomTextField;

import org.tentackle.fx.ConfiguratorService;
import org.tentackle.fx.component.config.TextInputControlConfigurator;

/**
 * Configures a {@link CustomTextField}.
 *
 * @author harald
 * @param <T> the textfield type
 */
@ConfiguratorService(CustomTextField.class)
public class CustomTextFieldConfigurator<T extends CustomTextField> extends TextInputControlConfigurator<T> {

  /**
   * Creates the configurator for a {@link CustomTextField}.
   */
  public CustomTextFieldConfigurator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }
}
