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

package org.tentackle.fx.component.config;

import javafx.scene.control.ComboBox;

import org.tentackle.fx.ConfiguratorService;

/**
 * Configures a ComboBox.
 *
 * @author harald
 * @param <T> the combobox type
 */
@ConfiguratorService(ComboBox.class)
public class ComboBoxConfigurator<T extends ComboBox<?>> extends ComboBoxBaseConfigurator<T> {

  @Override
  public void configure(T control) {
    super.configure(control);

    control.getProperties().put(PrefixSelectionFeature.ENABLED, "");
    new PrefixSelectionFeature<>(control,
                                 () -> !control.isEditable(),
                                 control::getItems,
                                 control::getConverter,
                                 index -> control.getSelectionModel().select(index))
        .configure();
  }

}
