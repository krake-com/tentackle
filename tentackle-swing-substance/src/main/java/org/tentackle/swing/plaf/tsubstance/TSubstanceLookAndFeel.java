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




package org.tentackle.swing.plaf.tsubstance;

import java.awt.Color;
import java.net.URL;
import java.util.MissingResourceException;
import javax.swing.ImageIcon;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;
import org.jvnet.lafwidget.animation.FadeConfigurationManager;
import org.jvnet.lafwidget.animation.FadeKind;
import org.jvnet.substance.skin.SubstanceModerateLookAndFeel;
import org.tentackle.common.Service;
import org.tentackle.swing.plaf.PlafUtilities;
import org.tentackle.swing.plaf.TentackleLookAndFeel;



/**
 *
 * @author harald
 */
@Service(TentackleLookAndFeel.class)
public class TSubstanceLookAndFeel extends SubstanceModerateLookAndFeel implements TentackleLookAndFeel {

  private static final long serialVersionUID = -1426152671864702049L;

  protected static MetalTheme currentTheme = null;


  private Color mandatoryColor;               // mandatory background color for text fields and selected table cells
  private Color unselectedMandatoryColor;     // mandatory background color for unselected table cells
  private boolean focusAnimated = true;       // focus animation for non-textfield components


  public TSubstanceLookAndFeel() {
    configureTSubstanceLookAndFeel();
    currentTheme = null;    // this does the trick that switching between plafs resets the theme!
  }


  public void configureTSubstanceLookAndFeel() {
    FadeConfigurationManager.getInstance().allowFades(FadeKind.ICON_GLOW);
    FadeConfigurationManager.getInstance().allowFades(FadeKind.GHOSTING_ICON_ROLLOVER);
    FadeConfigurationManager.getInstance().allowFades(FadeKind.GHOSTING_BUTTON_PRESS);
    setFocusAnimated(focusAnimated);
    loadTentackleExtraColors();
  }

  @Override
  public void loadTentackleExtraColors() {
    mandatoryColor = new ColorUIResource(255,255,170);
    unselectedMandatoryColor = new ColorUIResource(PlafUtilities.getInstance().brighter(mandatoryColor, 0.75));
  }

  @Override
  public Color getMandatoryColor() {
    return mandatoryColor;
  }

  @Override
  public void setMandatoryColor(Color mandatoryColor) {
    this.mandatoryColor = mandatoryColor;
  }


  @Override
  public Color getUnselectedMandatoryColor() {
    return unselectedMandatoryColor;
  }

  @Override
  public void setUnselectedMandatoryColor(Color unselectedMandatoryColor) {
    this.unselectedMandatoryColor = unselectedMandatoryColor;
  }

  public static String getTSubstanceName() {
      return "TSubstance";
  }


  public static String getTSubstanceDescription() {
      return "Enhanced Substance LnF for Tentackle";
  }

  @Override
  public String getName() {
      return getTSubstanceName();
  }


  @Override
  public String getDescription() {
      return getTSubstanceDescription();
  }

  @Override
  public String getID() {
    return getName();
  }


  @Override
  public boolean isNativeLookAndFeel() {
    return false;
  }


  @Override
  public boolean isSupportedLookAndFeel() {
    return true;
  }


  /**
   * Sets the current color theme.
   * @param theme the theme
   */
  public static void setCurrentTheme(MetalTheme theme) {
    MetalLookAndFeel.setCurrentTheme(theme);
    currentTheme = theme;
  }



  /**
   * UIManager.setLookAndFeel calls this method just before we're
   * replaced by a new default look and feel.   Subclasses may
   * choose to free up some resources here.
   */
  @Override
  public void uninitialize() {
    setCurrentTheme(new DefaultMetalTheme());
  }


  /**
   * overridden to install special TSubstance UI
   */
  @Override
  protected void initClassDefaults(UIDefaults table) {
    super.initClassDefaults(table);

    final String tSubstancePackageName = "org.tentackle.swing.plaf.tsubstance.";

    Object[] uiDefaults = {
      "RadioButtonUI", tSubstancePackageName + "TSubstanceRadioButtonUI",
      "CheckBoxUI", tSubstancePackageName + "TSubstanceCheckBoxUI",
      "ComboBoxUI", tSubstancePackageName + "TSubstanceComboBoxUI",
      "TextFieldUI", tSubstancePackageName + "TSubstanceTextFieldUI",
      "TextAreaUI", tSubstancePackageName + "TSubstanceTextAreaUI",
      "TableUI", tSubstancePackageName + "TSubstanceTableUI",};

    table.putDefaults(uiDefaults);
  }



  @Override
  protected void initComponentDefaults(UIDefaults table) {
    super.initComponentDefaults(table);
    Object[] defaults = new Object[]{
      "TextField.mandatoryBackground", getMandatoryColor(),
      "TextArea.mandatoryBackground", getMandatoryColor(),
      "TextArea.inactiveBackground", table.getColor("control"),
      "ComboBox.mandatoryBackground", getMandatoryColor(),
      "CheckBox.mandatoryBackground", getMandatoryColor(),
      "RadioButton.mandatoryBackground", getMandatoryColor(),
      "Table.selectedMandatoryBackground", getMandatoryColor(),
      "Table.unselectedMandatoryBackground", getUnselectedMandatoryColor()
    };
    table.putDefaults(defaults);
  }




  /**
   * Implements TentackleLookAndFeel.
   */
  @Override
  public ImageIcon loadImageIcon(String name) throws MissingResourceException {
    URL url = TSubstanceLookAndFeel.class.getResource("icons/" + name + ".png");
    if (url == null) {
      // try GIF
      url = TSubstanceLookAndFeel.class.getResource("icons/" + name + ".gif");
    }
    if (url == null) {
      throw new MissingResourceException("no such icon '" + name + "'", TSubstanceLookAndFeel.class.getName(), name);
    }
    return new ImageIcon(url);
  }



  /**
   * Configures the animated keyboard focus for non-text components that can grab
   * the keyboard focus, such as comboboxes, radio buttons or check boxes.
   * By default, the animation is enabled.
   * For the substance plaf we simply enable/disable the focus loop.
   *
   * @param flag the boolean value which is true to enable the animation, false to turn it off
   */
  @Override
  public void setFocusAnimated(boolean flag) {
    focusAnimated = flag;
    if (flag) {
      FadeConfigurationManager.getInstance().allowFades(FadeKind.FOCUS_LOOP_ANIMATION);
    }
    else  {
      FadeConfigurationManager.getInstance().disallowFades(FadeKind.FOCUS_LOOP_ANIMATION);
    }
  }


  /**
   * Retrievs the current setting for the focus animation.
   *
   * @return true if focus is animated
   */
  @Override
  public boolean isFocusAnimated() {
    return focusAnimated;
  }
}
