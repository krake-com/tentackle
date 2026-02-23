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

package org.tentackle.fx.atlanta;

import org.tentackle.common.Service;
import org.tentackle.fx.NotificationBuilder;
import org.tentackle.fx.ThemeUtilities;
import org.tentackle.fx.component.Note;
import org.tentackle.fx.rdc.RdcThemeUtilities;
import org.tentackle.fx.table.TotalsTableView;

import javafx.application.Application;
import javafx.scene.Scene;
import java.net.URL;
import java.util.List;

/**
 * {@link ThemeUtilities} for AtlantaFX.
 */
@Service(ThemeUtilities.class)
public class AtlantaThemeUtilities extends RdcThemeUtilities {

  private static final String DEFAULT_THEME_NAME = "Primer";
  private static final String CUPERTINO_THEME_NAME = "Cupertino";
  private static final String NORD_THEME_NAME = "Nord";

  private static final List<String> SUPPORTED_THEMES = List.of(DEFAULT_THEME_NAME,
                                                               CUPERTINO_THEME_NAME,
                                                               NORD_THEME_NAME);

  /**
   * Creates the Atlanta FX utilities.
   */
  public AtlantaThemeUtilities() {
    super(DEFAULT_THEME_NAME);
  }

  @Override
  public List<String> getSupportedThemes() {
    return SUPPORTED_THEMES;
  }

  @Override
  public void applyUserAgentStylesheet() {
    String modePart = isDarkMode() ? "-dark.css" : "-light.css";
    Application.setUserAgentStylesheet(switch (getThemeName()) {
      case NORD_THEME_NAME -> "/atlantafx/base/theme/nord" + modePart;
      case CUPERTINO_THEME_NAME -> "/atlantafx/base/theme/cupertino" + modePart;
      default -> "/atlantafx/base/theme/primer" + modePart;
    });
  }

  @Override
  public void applyStylesheets(Scene scene) {
    scene.getStylesheets().setAll(isDarkMode() ? "/org/tentackle/fx/atlanta/tentackle_dark.css" : "/org/tentackle/fx/atlanta/tentackle.css");
    scene.getStylesheets().add("/org/tentackle/fx/atlanta/pdo-style.css");
  }

  @Override
  public URL getResourceURL(Class<?> clazz, String name) {
    if (clazz == NotificationBuilder.class ||
        clazz == Note.class ||
        clazz == TotalsTableView.class) {
      clazz = getClass();
    }
    return super.getResourceURL(clazz, name);
  }
}
