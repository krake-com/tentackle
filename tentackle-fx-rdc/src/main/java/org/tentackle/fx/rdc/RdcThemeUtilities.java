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

package org.tentackle.fx.rdc;

import org.tentackle.common.Service;
import org.tentackle.fx.ThemeUtilities;

import javafx.scene.Scene;

/**
 * RDC theme utilities.<br>
 * Adds the stylesheet for the PDO-bound components (style class {@code tt-pdo-style}) to every scene.
 */
@Service(ThemeUtilities.class)
public class RdcThemeUtilities extends ThemeUtilities {

  /**
   * Creates the RDC theme utilities.
   *
   * @param themeName the default theme name
   * @param fontSize the default font size
   */
  public RdcThemeUtilities(String themeName, int fontSize) {
    super(themeName, fontSize);
  }

  /**
   * Creates the RDC theme utilities.
   */
  public RdcThemeUtilities() {
    super();
  }

  @Override
  public void applyStylesheets(Scene scene) {
    super.applyStylesheets(scene);
    scene.getStylesheets().add("/org/tentackle/fx/rdc/translate/pdo-style.css");
  }

}
