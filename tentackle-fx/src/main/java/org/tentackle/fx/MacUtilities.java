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

package org.tentackle.fx;

import org.kordamp.ikonli.javafx.FontIcon;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;

import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;


interface MacUtilitiesHolder {
  MacUtilities INSTANCE = ServiceFactory.createService(MacUtilities.class, MacUtilities.class);
}


/**
 * Utilities for Mac OS X.
 */
@Service(MacUtilities.class)
public class MacUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static MacUtilities getInstance() {
    return MacUtilitiesHolder.INSTANCE;
  }


  private static final int ICON_SIZE = 16;    // system menu bar icon size on Mac OS X

  /**
   * Creates the MacUtilities.
   */
  public MacUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  /**
   * Installs a menubar as a system menu bar.<br>
   * Used on macOS to convert {@link FontIcon}s to {@link ImageView}s
   * since font icons cannot be displayed in the system menu.
   */
  public void toSystemMenuBar(MenuBar menuBar) {
    for (Menu menu : menuBar.getMenus()) {
      toSystemMenu(menu);
    }
    menuBar.setUseSystemMenuBar(true);
  }


  /**
   * Converts {@link FontIcon}s to {@link ImageView}s.
   *
   * @param menu the menu
   */
  protected void toSystemMenu(Menu menu) {
    Node graphic = menu.getGraphic();
    if (graphic instanceof FontIcon fontIcon) {
      menu.setGraphic(createImageView(fontIcon));
    }
    for (MenuItem item : menu.getItems()) {
      if (item instanceof Menu subMenu) {
        toSystemMenu(subMenu);
      }
      else {
        graphic = item.getGraphic();
        if (graphic instanceof FontIcon fontIcon) {
          item.setGraphic(createImageView(fontIcon));
        }
      }
    }
  }

  /**
   * Creates an {@link ImageView} from a {@link FontIcon}.
   *
   * @param fontIcon the font icon
   * @return the image view
   */
  protected ImageView createImageView(FontIcon fontIcon) {
    fontIcon.setIconSize(ICON_SIZE);
    fontIcon.setIconColor(ThemeUtilities.getInstance().isDarkMode() ? Color.WHITE : Color.BLACK);
    return new ImageView(FxFactory.getInstance().createImage(fontIcon, Color.TRANSPARENT));
  }

}
