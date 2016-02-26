/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.swing.plaf;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.TreeMap;
import javax.swing.ImageIcon;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.swing.GUIRuntimeException;


interface PlafUtilities$Singleton {
  PlafUtilities INSTANCE = ServiceFactory.createService(PlafUtilities.class, PlafUtilities.class);
}


/**
 * Pluggable Look And Feel common helper methods.
 *
 * @author harald
 */
@Service(PlafUtilities.class)     // defaults to self
public class PlafUtilities {

  /**
   * The namespace of the icons for tentackle (can be used by apps)
   */
  public static final String TENTACKLE_ICONREALM = "tentackle";


  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static PlafUtilities getInstance() {
    return PlafUtilities$Singleton.INSTANCE;
  }


  /** The logger for this class. */
  private static final Logger LOGGER = LoggerFactory.getLogger(PlafUtilities.class);


  // predefined Swing colors according to current look and feel
  private Color alarmColor;
  private Color alarmBackgroundColor;
  private Color listSelectedForegroundColor;
  private Color listSelectedBackgroundColor;
  private Color listUnselectedForegroundColor;
  private Color listSelectedDisabledForegroundColor;
  private Color listUnselectedDisabledForegroundColor;
  private Color tableForegroundColor;
  private Color tableBackgroundColor;
  private Color tableFocusCellForegroundColor;
  private Color tableFocusCellBackgroundColor;
  private Color tableSelectionForegroundColor;
  private Color tableSelectionBackgroundColor;
  private Color dropFieldActiveColor;
  private Color dropFieldInactiveColor;
  private Color tableEditCellBorderColor;
  private Color textFieldBackgroundColor;
  private Color textFieldInactiveBackgroundColor;

  /**
   * IconProviders implement an abstraction layer for loading icons.
   * Each plaf may provide its own icon set which the application
   * can refer to in a portable way. Furthermore, applications can
   * register their own icon-provider. This allows applications to
   * adapt their icon sets if the user selects another plaf.
   *
   */
  private final Map<String,IconProvider> iconProviderMap = new TreeMap<>();

  /**
   * Icons are cached for the currently selected look and feel.
   * The key is constructed from the realm and icon name as {@code "realm/name"}.
   */
  private Map<String,ImageIcon> iconMap;





  /**
   * Creates the utility instance.
   */
  public PlafUtilities() {
    triggerLookAndFeelUpdated();
    addIconProvider(new DefaultIconProvider());
  }


  /**
   * Installs a LAF by its classname.
   * <p>
   * If the LAF is not loadable, no exception will be thrown.
   * This method is used to load known lafs. If a LAF is in the
   * classpath and provides a method <tt>get&lt;name&gt;Name()</tt>, then
   * {@link UIManager#installLookAndFeel(java.lang.String, java.lang.String)}
   * is invoked.<br>
   * Otherwise it is silently ignored.
   * <p>
   * The LAF may be any kind of LAF. However, due to the checked presence of the
   * method, in fact only {@link TentackleLookAndFeel}s will be loaded.
   *
   * @param clazzName the classname of the LAF
   */
  @SuppressWarnings("unchecked")
  public void installLookAndFeel(String clazzName) {
    try {
      // this will also check all dependencies, whether the plaf is in the classpath, etc...
      Class<?> clazz = Class.forName(clazzName);
      // figure out the name by invoking the class'es name func
      String name = clazzName.substring(clazzName.lastIndexOf('.') + 1, clazzName.length() - 11);
      // translate the name to what the static(!) method get<PLAF>Name() returns
      name = (String) clazz.getMethod("get" + name + "Name").invoke(null);
      UIManager.installLookAndFeel(name, clazzName);
    }
    catch (NoClassDefFoundError e) {
      // some reference missing
      LOGGER.fine("plaf {0} not in classpath -- not loaded", clazzName);
    }
    catch (Exception e) {
      // some other config error
      LOGGER.severe("cannot install plaf " + clazzName, e);
    }
  }

  /**
   * Installs all Tentackle Look and Feels
   */
  public void installTentackleLookAndFeels() {
    for (String plafName:
         ServiceFactory.getServiceFinder().findServiceConfigurations(TentackleLookAndFeel.class.getName()).keySet()) {
      installLookAndFeel(plafName);
    }
  }


  /**
   * Gets all installed {@link TentackleLookAndFeel}s.
   * <p>
   * The method invokes {@link UIManager#getInstalledLookAndFeels()} and
   * returns a list of all LAFs that implement {@link TentackleLookAndFeel}.
   *
   * @return the installed tentackle plafs, never null
   */
  public LookAndFeelInfo[] getInstalledTentackleLookAndFeels() {
    Collection<LookAndFeelInfo> tentackleLAFs = new ArrayList<>();
    for (LookAndFeelInfo lafInfo: UIManager.getInstalledLookAndFeels()) {
      try {
        if (TentackleLookAndFeel.class.isAssignableFrom(Class.forName(lafInfo.getClassName()))) {
          tentackleLAFs.add(lafInfo);
        }
      }
      catch (ClassNotFoundException ex) {
        // ignore
      }
    }
    return tentackleLAFs.toArray(new LookAndFeelInfo[tentackleLAFs.size()]);
  }


  /**
   * Configures the animated keyboard focus for non-text components that can grab
   * the keyboard focus, such as comboboxes, radio buttons or check boxes.
   * By default, the animation is enabled. For non-tentackle plafs this setting
   * has no effect.
   *
   * @param flag the boolean value which is true to enable the animation, false to turn it off
   */
  public void setFocusAnimated(boolean flag) {
    LookAndFeel plaf = UIManager.getLookAndFeel();
    if (plaf instanceof TentackleLookAndFeel) {
      ((TentackleLookAndFeel)plaf).setFocusAnimated(flag);
    }
  }


  /**
   * Retrievs the current setting for the focus animation.
   *
   * @return true if focus is animated
   */
  public boolean isFocusAnimated() {
    LookAndFeel plaf = UIManager.getLookAndFeel();
    return plaf instanceof TentackleLookAndFeel && ((TentackleLookAndFeel) plaf).isFocusAnimated();
  }



  /**
   * @return the alarmColor
   */
  public Color getAlarmColor() {
    return alarmColor;
  }

  /**
   * @param aAlarmColor the alarmColor to set
   */
  public void setAlarmColor(Color aAlarmColor) {
    alarmColor = aAlarmColor;
  }

  /**
   * @return the alarmBackgroundColor
   */
  public Color getAlarmBackgroundColor() {
    return alarmBackgroundColor;
  }

  /**
   * @param aAlarmBackgroundColor the alarmBackgroundColor to set
   */
  public void setAlarmBackgroundColor(Color aAlarmBackgroundColor) {
    alarmBackgroundColor = aAlarmBackgroundColor;
  }

  /**
   * @return the listSelectedForegroundColor
   */
  public Color getListSelectedForegroundColor() {
    return listSelectedForegroundColor;
  }

  /**
   * @param aListSelectedForegroundColor the listSelectedBackgroundColor to set
   */
  public void setListSelectedForegroundColor(Color aListSelectedForegroundColor) {
    listSelectedForegroundColor = aListSelectedForegroundColor;
  }

  /**
   * @return the listSelectedBackgroundColor
   */
  public Color getListSelectedBackgroundColor() {
    return listSelectedBackgroundColor;
  }

  /**
   * @param aListSelectedBackgroundColor the listSelectedBackgroundColor to set
   */
  public void setListSelectedBackgroundColor(Color aListSelectedBackgroundColor) {
    listSelectedBackgroundColor = aListSelectedBackgroundColor;
  }

  /**
   * @return the listUnselectedForegroundColor
   */
  public Color getListUnselectedForegroundColor() {
    return listUnselectedForegroundColor;
  }

  /**
   * @param aListUnselectedForegroundColor the listUnselectedForegroundColor to set
   */
  public void setListUnselectedForegroundColor(Color aListUnselectedForegroundColor) {
    listUnselectedForegroundColor = aListUnselectedForegroundColor;
  }

  /**
   * @return the listSelectedDisabledForegroundColor
   */
  public Color getListSelectedDisabledForegroundColor() {
    return listSelectedDisabledForegroundColor;
  }

  /**
   * @param aListSelectedDisabledForegroundColor the listSelectedDisabledForegroundColor to set
   */
  public void setListSelectedDisabledForegroundColor(Color aListSelectedDisabledForegroundColor) {
    listSelectedDisabledForegroundColor = aListSelectedDisabledForegroundColor;
  }

  /**
   * @return the listUnselectedDisabledForegroundColor
   */
  public Color getListUnselectedDisabledForegroundColor() {
    return listUnselectedDisabledForegroundColor;
  }

  /**
   * @param aListUnselectedDisabledForegroundColor the listUnselectedDisabledForegroundColor to set
   */
  public void setListUnselectedDisabledForegroundColor(Color aListUnselectedDisabledForegroundColor) {
    listUnselectedDisabledForegroundColor = aListUnselectedDisabledForegroundColor;
  }

  /**
   * @return the tableForegroundColor
   */
  public Color getTableForegroundColor() {
    return tableForegroundColor;
  }

  /**
   * @param aTableForegroundColor the tableForegroundColor to set
   */
  public void setTableForegroundColor(Color aTableForegroundColor) {
    tableForegroundColor = aTableForegroundColor;
  }

  /**
   * @return the tableBackgroundColor
   */
  public Color getTableBackgroundColor() {
    return tableBackgroundColor;
  }

  /**
   * @param aTableBackgroundColor the tableBackgroundColor to set
   */
  public void setTableBackgroundColor(Color aTableBackgroundColor) {
    tableBackgroundColor = aTableBackgroundColor;
  }

  /**
   * @return the tableFocusCellForegroundColor
   */
  public Color getTableFocusCellForegroundColor() {
    return tableFocusCellForegroundColor;
  }

  /**
   * @param aTableFocusCellForegroundColor the tableFocusCellForegroundColor to set
   */
  public void setTableFocusCellForegroundColor(Color aTableFocusCellForegroundColor) {
    tableFocusCellForegroundColor = aTableFocusCellForegroundColor;
  }

  /**
   * @return the tableFocusCellBackgroundColor
   */
  public Color getTableFocusCellBackgroundColor() {
    return tableFocusCellBackgroundColor;
  }

  /**
   * @param aTableFocusCellBackgroundColor the tableFocusCellBackgroundColor to set
   */
  public void setTableFocusCellBackgroundColor(Color aTableFocusCellBackgroundColor) {
    tableFocusCellBackgroundColor = aTableFocusCellBackgroundColor;
  }

  /**
   * @return the tableSelectionForegroundColor
   */
  public Color getTableSelectionForegroundColor() {
    return tableSelectionForegroundColor;
  }

  /**
   * @param aTableSelectionForegroundColor the tableSelectionForegroundColor to set
   */
  public void setTableSelectionForegroundColor(Color aTableSelectionForegroundColor) {
    tableSelectionForegroundColor = aTableSelectionForegroundColor;
  }

  /**
   * @return the tableSelectionBackgroundColor
   */
  public Color getTableSelectionBackgroundColor() {
    return tableSelectionBackgroundColor;
  }

  /**
   * @param aTableSelectionBackgroundColor the tableSelectionBackgroundColor to set
   */
  public void setTableSelectionBackgroundColor(Color aTableSelectionBackgroundColor) {
    tableSelectionBackgroundColor = aTableSelectionBackgroundColor;
  }

  /**
   * @return the dropFieldActiveColor
   */
  public Color getDropFieldActiveColor() {
    return dropFieldActiveColor;
  }

  /**
   * @param aDropFieldActiveColor the dropFieldActiveColor to set
   */
  public void setDropFieldActiveColor(Color aDropFieldActiveColor) {
    dropFieldActiveColor = aDropFieldActiveColor;
  }

  /**
   * @return the dropFieldInactiveColor
   */
  public Color getDropFieldInactiveColor() {
    return dropFieldInactiveColor;
  }

  /**
   * @param aDropFieldInactiveColor the dropFieldInactiveColor to set
   */
  public void setDropFieldInactiveColor(Color aDropFieldInactiveColor) {
    dropFieldInactiveColor = aDropFieldInactiveColor;
  }

  /**
   * @return the tableEditCellBorderColor
   */
  public Color getTableEditCellBorderColor() {
    return tableEditCellBorderColor;
  }

  /**
   * @param aTableEditCellBorderColor the tableEditCellBorderColor to set
   */
  public void setTableEditCellBorderColor(Color aTableEditCellBorderColor) {
    tableEditCellBorderColor = aTableEditCellBorderColor;
  }

  /**
   * @return the textFieldBackgroundColor
   */
  public Color getTextFieldBackgroundColor() {
    return textFieldBackgroundColor;
  }

  /**
   * @param aTextFieldBackgroundColor the textFieldBackgroundColor to set
   */
  public void setTextFieldBackgroundColor(Color aTextFieldBackgroundColor) {
    textFieldBackgroundColor = aTextFieldBackgroundColor;
  }

  /**
   * @return the textFieldInactiveBackgroundColor
   */
  public Color getTextFieldInactiveBackgroundColor() {
    return textFieldInactiveBackgroundColor;
  }

  /**
   * @param aTextFieldInactiveBackgroundColor the textFieldInactiveBackgroundColor to set
   */
  public void setTextFieldInactiveBackgroundColor(Color aTextFieldInactiveBackgroundColor) {
    textFieldInactiveBackgroundColor = aTextFieldInactiveBackgroundColor;
  }


  /**
   * Registers an IconProvider.
   * Applications can register their own providers for the their namespaces.
   *
   * @param iconProvider the provider to register
   * @return null if there was no provider for the provider's realm, the old provider otherwise
   */
  public IconProvider addIconProvider(IconProvider iconProvider) {
    return iconProviderMap.put(iconProvider.getRealm(), iconProvider);
  }


  /**
   * Unregisters an IconProvider for a given realm.
   *
   * @param realm the realm to remove the provider for
   * @return null if there was no provider for the provider's realm, the old provider otherwise
   */
  public IconProvider removeIconProvider(String realm) {
    return iconProviderMap.remove(realm);
  }



  /**
   * Gets an icon for the current look-and-feel, a given realm and icon name.
   *
   * @param realm the icon's namespace
   * @param name the name if the icon
   * @return the icon
   * @throws MissingResourceException if no such icon
   */
  public ImageIcon getIcon(String realm, String name) throws MissingResourceException {

    ImageIcon icon = null;
    String key = realm + "/" + name;

    if (iconMap == null) {
      iconMap = new TreeMap<>();
    }
    else  {
      // check if icon is already loaded
      icon = iconMap.get(key);
    }

    if (icon == null) {
      // not in cache: load it
      IconProvider iconProvider = iconProviderMap.get(realm);
      if (iconProvider == null) {
        throw new MissingResourceException("no icon provider for realm '" + realm + "'", PlafUtilities.class.getName(), realm);
      }
      icon = iconProvider.loadImageIcon(UIManager.getLookAndFeel(), name);
      iconMap.put(key, icon);
    }

    return icon;
  }


  /**
   * Gets the icon for the tentackle default realm.
   *
   * @param name the name if the icon
   * @return the icon
   * @throws MissingResourceException if no such icon
   */
  public ImageIcon getIcon(String name) throws MissingResourceException {
    return getIcon(TENTACKLE_ICONREALM, name);
  }


  /**
   * Gets a UIManager default.
   *
   * @param keys the list of keys to try
   * @return the resource
   */
  public Object getUIManagerDefault(String... keys) {
    for (String key: keys) {
      Object resource = UIManager.getDefaults().get(key);
      if (resource != null) {
        return resource;
      }
    }
    throw new GUIRuntimeException("missing resource " + keys[0]);
  }


  /**
   * Invoke this method whenever the plaf has changed.
   */
  public void triggerLookAndFeelUpdated() {

    try {
      // colors are created as non-UIResource colors because otherwise most
      // plaf implementation will ignore them.
      // Notice: alternate keys are for Nimbus
      setTextFieldBackgroundColor(new Color(((Color) getUIManagerDefault("TextField.background")).getRGB()));
      setTextFieldInactiveBackgroundColor(
              new Color(((Color) getUIManagerDefault("TextField.inactiveBackground", "TextField.background")).getRGB()));
      setAlarmColor(Color.red);   // not depending on plaf
      setAlarmBackgroundColor(new Color(255, 192, 192));
      // we use new Color because UIManager usually returns UIResource colors,
      // which are not honoured by most lafs.
      setListSelectedForegroundColor(new Color(((Color) getUIManagerDefault("List.selectionForeground",
              "List[Selected].textForeground")).getRGB()));
      setListSelectedBackgroundColor(new Color(((Color) getUIManagerDefault("List.selectionBackground",
              "List[Selected].textBackground")).getRGB()));
      setListUnselectedForegroundColor(new Color(((Color) getUIManagerDefault("List.foreground")).getRGB()));
      setListUnselectedDisabledForegroundColor(new Color(((Color) getUIManagerDefault("TextField.inactiveForeground",
              "List[Disabled].textForeground")).
              getRGB()));
      Color color1 = getListSelectedForegroundColor();
      Color color2 = getListSelectedBackgroundColor();
      setListSelectedDisabledForegroundColor(new Color((color1.getRed() + color2.getRed()) >> 1, (color1.getGreen() +
              color2.getGreen()) >> 1, (color1.getBlue() + color2.getBlue()) >> 1));

      setTableForegroundColor(new Color(((Color) getUIManagerDefault("Table.foreground")).getRGB()));
      setTableBackgroundColor(new Color(((Color) getUIManagerDefault("Table.background")).getRGB()));
      setTableFocusCellForegroundColor(new Color(((Color) getUIManagerDefault("Table.focusCellForeground",
              "Table[Enabled+Selected].textForeground")).getRGB()));
      setTableFocusCellBackgroundColor(new Color(((Color) getUIManagerDefault("Table.focusCellBackground",
              "Table[Enabled+Selected].textBackground")).getRGB()));
      setTableSelectionForegroundColor(new Color(((Color) getUIManagerDefault("Table.selectionForeground",
              "Table.foreground")).getRGB()));
      setTableSelectionBackgroundColor(new Color(((Color) getUIManagerDefault("Table.selectionBackground",
              "Table[Disabled+Selected].textBackground")).getRGB()));

      color1 = new Color(((Color) getUIManagerDefault("TextField.selectionBackground",
              "TextField[Selected].textForeground")).getRGB());
      color2 = new Color(((Color) getUIManagerDefault("TextField.inactiveBackground", "TextField.background")).getRGB());

      setDropFieldActiveColor(new Color((color1.getRed() + color2.getRed()) >> 1,
              (color1.getGreen() + color2.getGreen()) >> 1, (color1.getBlue() + color2.getBlue()) >> 1));
      float[] hsb = new float[3];
      Color.RGBtoHSB(getDropFieldActiveColor().getRed(), getDropFieldActiveColor().getGreen(),
              getDropFieldActiveColor().
              getBlue(), hsb);
      hsb[1] = 0.2f;    // fixed saturation 20%
      hsb[2] = 0.9f;    // fixed brightness 90%
      setDropFieldActiveColor(new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2])));

      setDropFieldInactiveColor(color2);

      Color.RGBtoHSB(getListSelectedDisabledForegroundColor().getRed(),
              getListSelectedDisabledForegroundColor().getGreen(),
              getListSelectedDisabledForegroundColor().getBlue(), hsb);
      hsb[1] *= 0.2f;
      setTableEditCellBorderColor(new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2])));

      // clear the icon cache
      iconMap = null;
    }
    catch (RuntimeException rex) {
      LOGGER.severe("updating UI defaults failed", rex);
    }
  }




  /**
   * Make a color brighter.
   *
   * @param color the original color
   * @param factor the factor ({@link Color} uses 0.7)
   * @return the brighter color
   * @see Color#brighter()
   */
  public Color brighter(Color color, double factor) {

    int r = color.getRed();
    int g = color.getGreen();
    int b = color.getBlue();

    /* From 2D group:
     * 1. black.brighter() should return grey
     * 2. applying brighter to blue will always return blue, brighter
     * 3. non pure color (non zero rgb) will eventually return white
     */
    int i = (int) (1.0 / (1.0 - factor));
    if (r == 0 && g == 0 && b == 0) {
      return new Color(i, i, i);
    }
    if (r > 0 && r < i) {
      r = i;
    }
    if (g > 0 && g < i) {
      g = i;
    }
    if (b > 0 && b < i) {
      b = i;
    }

    return new Color(Math.min((int) (r / factor), 255),
            Math.min((int) (g / factor), 255),
            Math.min((int) (b / factor), 255));
  }

  /**
   * Make a color darker.
   *
   * @param color the original color
   * @param factor the factor ({@link Color} uses 0.7)
   * @return the darker color
   * @see Color#darker()
   */
  public Color darker(Color color, double factor) {
    return new Color(Math.max((int) (color.getRed() * factor), 0),
            Math.max((int) (color.getGreen() * factor), 0),
            Math.max((int) (color.getBlue() * factor), 0));
  }

}
