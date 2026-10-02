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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;
import java.net.URL;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

interface ThemeUtilitiesHolder {
  ThemeUtilities INSTANCE = ServiceFactory.createService(ThemeUtilities.class, ThemeUtilities.class);
}

/**
 * Utility methods for theme management.<br>
 * Maintains the dark mode and the theme name, applies the stylesheets to the scenes,
 * and saves/loads the user's theme preferences.
 * The default implementation supports the JavaFX default theme Modena only.
 * Theme modules such as {@code tentackle-fx-atlanta} replace the singleton to provide more themes.
 *
 * @author harald
 */
@Service(ThemeUtilities.class)    // defaults to self
public class ThemeUtilities {

  /**
   * Property for the dark mode.
   */
  public static final String DARK_MODE = "darkMode";

  /**
   * Theme preferences.
   *
   * @param darkMode the dark mode flag, null for default or to clear the preference
   * @param themeName the theme name, null for default or to clear the preference
   */
  public record ThemePreferences(Boolean darkMode, String themeName) {

    /**
     * Derives a new preferences object with the given dark mode flag.
     *
     * @param darkMode the dark mode flag, null for default or to clear the preference
     * @return the new preferences
     */
    public ThemePreferences withDarkMode(Boolean darkMode) {
      return new ThemePreferences(darkMode, themeName);
    }

    /**
     * Derives a new preferences object with the given theme name.
     *
     * @param themeName the theme name, null for default or to clear the preference
     * @return the new preferences
     */
    public ThemePreferences withThemeName(String themeName) {
      return new ThemePreferences(darkMode, themeName);
    }

  }

  /**
   * Property for the theme name.
   */
  public static final String THEME_NAME = "themeName";

  private static final String DEFAULT_THEME_NAME = "Modena";
  private static final List<String> SUPPORTED_THEMES = List.of(DEFAULT_THEME_NAME);

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static ThemeUtilities getInstance() {
    return ThemeUtilitiesHolder.INSTANCE;
  }


  // holds the dark theme boolean, false is light mode (default)
  private final BooleanProperty darkMode = new SimpleBooleanProperty();

  // holds the theme name, "Modena" for the default theme
  private final StringProperty themeName = new SimpleStringProperty();

  /**
   * The default font.
   */
  private final Font defaultFont;

  private boolean styleUpdateEnabled = true;
  private boolean stylesApplied;




  /**
   * Creates the theme utilities.
   *
   * @param defaultThemeName the default theme name
   * @param fontSize the default font size
   */
  public ThemeUtilities(String defaultThemeName, int fontSize) {
    themeName.set(defaultThemeName);
    darkModeProperty().subscribe(this::updateStyles);
    themeNameProperty().subscribe(this::updateStyles);
    defaultFont = Font.font(Font.getDefault().getFamily(), FontWeight.NORMAL, fontSize);
  }

  /**
   * Creates the theme utilities with the default theme name.
   */
  public ThemeUtilities() {
    this(DEFAULT_THEME_NAME, 13);
  }


  /**
   * Returns whether dark mode is enabled.
   *
   * @return true if dark mode, false if light mode (default)
   */
  public boolean isDarkMode() {
    return darkModeProperty().get();
  }

  /**
   * Enables dark mode.
   *
   * @param darkMode true to enable dark theme, false for light theme (default)
   */
  public void setDarkMode(boolean darkMode) {
    darkModeProperty().set(darkMode);
    if (!stylesApplied) {
      // make sure that stylesheets are applied at least once
      updateStyles();
    }
  }

  /**
   * Gets the dark mode property.<br>
   * Changing the property updates the stylesheets of all scenes.
   *
   * @return the property
   */
  public BooleanProperty darkModeProperty() {
    return darkMode;
  }


  /**
   * Gets the list of supported themes.
   *
   * @return the names of the supported themes, never null
   */
  public List<String> getSupportedThemes() {
    return SUPPORTED_THEMES;
  }

  /**
   * Gets the current theme name.
   *
   * @return the theme name, never null
   */
  public String getThemeName() {
    return themeNameProperty().get();
  }

  /**
   * Sets the theme name.
   * <p>
   * Throws {@link FxRuntimeException} if the theme is not supported.
   *
   * @param themeName the theme name
   */
  public void setThemeName(String themeName) {
    if (getSupportedThemes().contains(themeName)) {
      themeNameProperty().set(themeName);
      if (!stylesApplied) {
        // make sure that stylesheets are applied at least once
        updateStyles();
      }
    }
    else {
      throw new FxRuntimeException("Unsupported theme: " + themeName);
    }
  }

  /**
   * Gets the theme name property.<br>
   * Changing the property updates the stylesheets of all scenes.
   *
   * @return the property
   */
  public StringProperty themeNameProperty() {
    return themeName;
  }

  /**
   * Sets the theme name and dark mode simultaneously.<br>
   * The stylesheets are updated only once.
   *
   * @param darkMode the dark mode flag
   * @param themeName the theme name
   */
  public void setTheme(boolean darkMode, String themeName) {
    styleUpdateEnabled = false;   // don't update styles twice
    setDarkMode(darkMode);
    styleUpdateEnabled = true;
    stylesApplied = false;
    setThemeName(themeName);
  }


  /**
   * Saves the theme name and dark mode to the user preferences on the local machine.
   *
   * @param clazz the application class
   * @param preferences the preferences to save
   */
  public void savePreferences(Class<?> clazz, ThemePreferences preferences) {
    Preferences userPrefs = Preferences.userNodeForPackage(clazz);
    if (preferences.darkMode != null) {
      userPrefs.putBoolean(DARK_MODE, preferences.darkMode);
    }
    else {
      userPrefs.remove(DARK_MODE);
    }
    if (preferences.themeName != null) {
      userPrefs.put(THEME_NAME, preferences.themeName);
    }
    else {
      userPrefs.remove(THEME_NAME);
    }
    try {
      userPrefs.flush();
    }
    catch (BackingStoreException bx) {
      throw new FxRuntimeException("can't save theme preferences", bx);
    }
  }

  /**
   * Loads the theme name and dark mode from the user preferences on the local machine.
   *
   * @param clazz the application class
   * @param defaultDarkMode the default dark mode value, null for none
   * @param defaultThemeName the default theme name, null for none
   * @return the preferences, never null
   */
  public ThemePreferences loadPreferences(Class<?> clazz, Boolean defaultDarkMode, String defaultThemeName) {
    Preferences userPrefs = Preferences.userNodeForPackage(clazz);
    String darkModePref = userPrefs.get(DARK_MODE, null);
    Boolean darkModeValue = darkModePref != null ? Boolean.valueOf(darkModePref) : defaultDarkMode;
    String themaNamePref = userPrefs.get(THEME_NAME, defaultThemeName);
    if (themaNamePref != null && !getSupportedThemes().contains(themaNamePref)) {
      themaNamePref = null;
    }
    return new ThemePreferences(darkModeValue, themaNamePref);
  }

  /**
   * Loads the theme name and dark mode from the user preferences on the local machine.
   *
   * @param clazz the application class
   * @return the preferences, never null
   */
  public ThemePreferences loadPreferences(Class<?> clazz) {
    return loadPreferences(clazz, null, null);
  }

  /**
   * Applies the theme preferences.<br>
   * Preferences that are null leave the current setting unchanged.
   *
   * @param preferences the preferences
   */
  public void applyThemePreferences(ThemePreferences preferences) {
    setTheme(preferences.darkMode == null ? isDarkMode() : preferences.darkMode,
             preferences.themeName == null ? getThemeName() : preferences.themeName);
  }


  /**
   * Applies the user agent stylesheet.<br>
   * The default implementation does nothing, i.e. keeps the JavaFX default Modena.
   * Overridden by theme implementations.
   */
  public void applyUserAgentStylesheet() {
    // the default does nothing, resulting in Modena
  }

  /**
   * Applies the default stylesheets to a scene.<br>
   * Invoked for all newly created scenes or via {@link #reapplyStyleSheets()}.
   *
   * @param scene the scene
   */
  public void applyStylesheets(Scene scene) {
    scene.getStylesheets().setAll(isDarkMode() ? "/org/tentackle/fx/tentackle-dark.css" : "/org/tentackle/fx/tentackle.css");
  }

  /**
   * Resets and applies the stylesheets to all scenes.<br>
   * Useful when the dark mode configuration changed, for example.
   */
  public void reapplyStyleSheets() {
    for (Window window : Window.getWindows()) {
      Scene scene = window.getScene();
      if (scene != null) {
        applyStylesheets(scene);
      }
    }
  }

  /**
   * Gets the resource URL.<br>
   * The method is provided to allow redirects to other resources, usually CSS files.
   *
   * @param clazz the reference class
   * @param name the resource name
   * @return the resource URL
   */
  public URL getResourceURL(Class<?> clazz, String name) {
    return clazz.getResource(name);
  }

  /**
   * Gets the default font.
   *
   * @return the default font
   */
  public Font getDefaultFont() {
    return defaultFont;
  }

  /**
   * Gets the padding for auto-adjusting the width of a table column or tree table column.
   *
   * @return the padding in pixels
   */
  public double getAutoAdjustPadding() {
    return 0;
  }


  /**
   * Updates the stylesheets according to the dark mode and theme setting.<br>
   * Does nothing if not invoked from the FX application thread or while {@link #setTheme(boolean, String)} is in progress.
   */
  protected void updateStyles() {
    if (styleUpdateEnabled && Platform.isFxApplicationThread()) {
      stylesApplied = true;
      applyUserAgentStylesheet();
      reapplyStyleSheets();
    }
  }

}
