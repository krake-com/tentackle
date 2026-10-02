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

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;

import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;


interface MacUtilitiesHolder {
  MacUtilities INSTANCE = ServiceFactory.createService(MacUtilities.class, MacUtilities.class);
}


/**
 * Utilities for macOS.
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


  private static final int ICON_SIZE = 16;    // system menu bar icon size on macOS

  /** Key to remember the converted icon in the properties of the {@link ImageView}. */
  private static final String ICON_CODE_KEY = MacUtilities.class.getName() + ".iconCode";

  /** The system menu bars to update whenever the theme changes. */
  private final List<WeakReference<MenuBar>> systemMenuBars = new ArrayList<>();

  /** True if subscribed to the theme properties. */
  private boolean themeObserved;


  /**
   * Creates the MacUtilities.
   */
  public MacUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  /**
   * Installs a menubar as a system menu bar.<br>
   * Used on macOS to convert {@link FontIcon}s to {@link ImageView}s
   * since font icons cannot be displayed in the system menu.<br>
   * The menu bar is kept up to date: since the images are rendered for the current theme,
   * they are re-created whenever the light/dark mode or the theme changes.
   *
   * @param menuBar the menu bar
   */
  public void toSystemMenuBar(MenuBar menuBar) {
    for (Menu menu : menuBar.getMenus()) {
      toSystemMenu(menu);
    }
    menuBar.setUseSystemMenuBar(true);
    observeTheme(menuBar);
  }


  /**
   * Converts {@link FontIcon}s to {@link ImageView}s.
   *
   * @param menu the menu
   */
  protected void toSystemMenu(Menu menu) {
    menu.setGraphic(toSystemGraphic(menu.getGraphic()));
    for (MenuItem item : menu.getItems()) {
      if (item instanceof Menu subMenu) {
        toSystemMenu(subMenu);
      }
      else {
        item.setGraphic(toSystemGraphic(item.getGraphic()));
      }
    }
  }

  /**
   * Converts a menu graphic for the system menu.<br>
   * {@link FontIcon}s are rendered to an {@link ImageView}. An image view created by a former
   * invocation is rendered again, since the color depends on the current theme.
   * All other graphics are left unchanged.
   *
   * @param graphic the menu graphic, null if none
   * @return the graphic to display in the system menu, null if none
   */
  protected Node toSystemGraphic(Node graphic) {
    if (graphic instanceof FontIcon fontIcon) {
      return createImageView(fontIcon);
    }
    if (graphic instanceof ImageView imageView &&
        imageView.getProperties().get(ICON_CODE_KEY) instanceof Ikon iconCode) {
      return createImageView(new FontIcon(iconCode));
    }
    return graphic;
  }

  /**
   * Updates the system menu bars whenever the theme changes.<br>
   * Registers the menu bar and subscribes to the theme properties on first invocation.
   *
   * @param menuBar the system menu bar
   */
  protected void observeTheme(MenuBar menuBar) {
    for (Iterator<WeakReference<MenuBar>> iter = systemMenuBars.iterator(); iter.hasNext(); ) {
      MenuBar observedMenuBar = iter.next().get();
      if (observedMenuBar == null) {
        iter.remove();    // window gone
      }
      else if (observedMenuBar == menuBar) {
        return;           // already observed
      }
    }
    systemMenuBars.add(new WeakReference<>(menuBar));

    if (!themeObserved) {
      themeObserved = true;
      // the theme name matters as well, because getIconColor() may be overridden
      ThemeUtilities.getInstance().darkModeProperty().subscribe(this::updateSystemMenuBars);
      ThemeUtilities.getInstance().themeNameProperty().subscribe(this::updateSystemMenuBars);
    }
  }

  /**
   * Re-creates the images of all system menu bars for the current theme.
   */
  protected void updateSystemMenuBars() {
    for (Iterator<WeakReference<MenuBar>> iter = systemMenuBars.iterator(); iter.hasNext(); ) {
      MenuBar menuBar = iter.next().get();
      if (menuBar == null) {
        iter.remove();    // window gone
      }
      else {
        boolean menuGraphicPresent = false;
        for (Menu menu : menuBar.getMenus()) {
          menuGraphicPresent |= isGraphicPresent(menu);
          toSystemMenu(menu);
        }
        if (menuGraphicPresent) {
          /*
           * JavaFX updates the system menu whenever the graphic of a menu *item* changes,
           * but not for the menus themselves (see GlassSystemMenu#setMenuBindings).
           * Toggling the property makes the MenuBarSkin rebuild the whole system menu.
           */
          menuBar.setUseSystemMenuBar(false);
          menuBar.setUseSystemMenuBar(true);
        }
      }
    }
  }

  /**
   * Returns whether a menu or one of its sub menus provides a graphic.
   *
   * @param menu the menu
   * @return true if there is a graphic
   */
  private boolean isGraphicPresent(Menu menu) {
    if (menu.getGraphic() != null) {
      return true;
    }
    for (MenuItem item : menu.getItems()) {
      if (item instanceof Menu subMenu && isGraphicPresent(subMenu)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Creates an {@link ImageView} from a {@link FontIcon}.<br>
   * The font icon is modified and must not be used anymore.
   *
   * @param fontIcon the font icon
   * @return the image view
   */
  protected ImageView createImageView(FontIcon fontIcon) {
    Color iconColor = getIconColor();
    fontIcon.setIconSize(ICON_SIZE);
    fontIcon.setIconColor(iconColor);
    /*
     * The size and the color must be applied as an inline style as well.
     * FontIcon#iconSizeProperty and #iconColorProperty always report StyleOrigin.USER_AGENT,
     * even if the value was set programmatically. As a consequence, a matching rule of the
     * user agent stylesheet overrides the values set above during the CSS pass performed
     * by Node#snapshot (the AtlantaFX themes, for example, define
     * ".ikonli-font-icon { -fx-icon-size: 18px; -fx-icon-color: -color-fg-default; }").
     * Inline styles take precedence over any stylesheet.
     */
    fontIcon.setStyle(fontIcon.getStyle() +
                      ";-fx-icon-size: " + ICON_SIZE + "px" +
                      ";-fx-icon-color: " + toWebColor(iconColor) + ";");

    /*
     * Snapshot a square of exactly ICON_SIZE pixels with the icon centered.
     * The bounds of the FontIcon are the text bounds of the glyph, i.e. they depend on the
     * metrics of the icon font and are neither square nor centered. macOS has to rescale
     * such an image to fit the menu, which renders the icon blurred or distorted.
     */
    StackPane iconPane = new StackPane(fontIcon);
    iconPane.setMinSize(ICON_SIZE, ICON_SIZE);
    iconPane.setPrefSize(ICON_SIZE, ICON_SIZE);
    iconPane.setMaxSize(ICON_SIZE, ICON_SIZE);

    ImageView imageView = new ImageView(FxFactory.getInstance().createImage(iconPane, Color.TRANSPARENT));
    Ikon iconCode = fontIcon.getIconCode();
    if (iconCode != null) {
      // remember the icon to render it again if the theme changes
      imageView.getProperties().put(ICON_CODE_KEY, iconCode);
    }
    return imageView;
  }

  /**
   * Gets the color to render the system menu icons with.
   *
   * @return the icon color
   */
  protected Color getIconColor() {
    return ThemeUtilities.getInstance().isDarkMode() ? Color.WHITE : Color.BLACK;
  }

  /**
   * Converts a color to its CSS representation.
   *
   * @param color the color
   * @return the CSS color function
   */
  protected String toWebColor(Color color) {
    return String.format(Locale.ROOT, "rgba(%d,%d,%d,%s)",
                         Math.round(color.getRed() * 255),
                         Math.round(color.getGreen() * 255),
                         Math.round(color.getBlue() * 255),
                         color.getOpacity());
  }

}
