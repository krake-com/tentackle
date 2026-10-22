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

package org.tentackle.fx.atlanta.container.build;

import atlantafx.base.layout.InputGroup;

import org.tentackle.fx.AbstractBuilder;
import org.tentackle.fx.BuilderService;
import org.tentackle.fx.atlanta.container.FxInputGroup;

import javafx.beans.DefaultProperty;

/**
 * Builder to create an {@link FxInputGroup} whenever the FXML references an {@link InputGroup}.
 *
 * @author harald
 */
@BuilderService(InputGroup.class)
@DefaultProperty("children")
public class InputGroupBuilder extends AbstractBuilder<InputGroup> {

  /**
   * Creates the builder.
   */
  public InputGroupBuilder() {
    super(new FxInputGroup());
  }

}
