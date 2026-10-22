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

package org.tentackle.fx.atlanta.component.build;

import atlantafx.base.controls.ToggleSwitch;

import org.tentackle.fx.AbstractBuilder;
import org.tentackle.fx.BuilderService;
import org.tentackle.fx.atlanta.component.FxToggleSwitch;

/**
 * Builder to create an {@link FxToggleSwitch} whenever the FXML references a {@link ToggleSwitch}.
 *
 * @author harald
 */
@BuilderService(ToggleSwitch.class)
public class ToggleSwitchBuilder extends AbstractBuilder<ToggleSwitch> {

  /**
   * Creates the builder.
   */
  public ToggleSwitchBuilder() {
    super(new FxToggleSwitch());
  }

}
