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

package org.tentackle.fx;

import javafx.scene.Node;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Base implementation of a graphic provider based on ikonli icon fonts.
 *
 * @author harald
 */
public abstract class IkonliGraphicProvider implements GraphicProvider {

  /**
   * Parent constructor.
   */
  public IkonliGraphicProvider() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public Node createGraphic(String name) {
    try {
      // size and color should be set via CSS
      return new FontIcon(translateName(name));
    }
    catch (RuntimeException rx) {
      throw new FxRuntimeException("loading font icon " + name + " failed", rx);
    }
  }

  /**
   * Translates the symbolic Tentackle icon name to the corresponding name of the icon pack.<br>
   * If the name is not one of the names used by the framework, it is returned unchanged.
   * This allows the application to use known icons of the corresponding icon pack.
   *
   * @param name the symbolic name
   * @return the possibly translated name
   */
  protected abstract String translateName(String name);

}
