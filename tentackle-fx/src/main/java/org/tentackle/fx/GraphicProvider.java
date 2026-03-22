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

/**
 * Provider for icons, images, and nodes with graphical content in general.
 * <p>
 * Graphics are categorized in realms. The framework uses the default realm.
 * Applications should use their own realm.
 *
 * @see GraphicProviderService
 */
public interface GraphicProvider {

  /**
   * Creates a graphic node.<br>
   * Throws {@link FxRuntimeException}, if loading the image failed or no such image found.
   *
   * @param name the graphic's name
   * @return the graphic node, never null
   */
  Node createGraphic(String name);

}
