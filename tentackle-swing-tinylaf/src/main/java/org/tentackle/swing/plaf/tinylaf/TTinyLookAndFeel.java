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


package org.tentackle.swing.plaf.tinylaf;

import net.sf.tinylaf.Theme;
import net.sf.tinylaf.TinyLookAndFeel;

import org.tentackle.common.Service;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.swing.plaf.PlafUtilities;
import org.tentackle.swing.plaf.TentackleLookAndFeel;

import javax.swing.ImageIcon;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;
import java.net.URL;
import java.util.MissingResourceException;



/**
 * Extended Tentackle TinyLAF.
 * <p>
 * Needs at least tinylaf 1.4.0
 *
 * @author harald
 */
@Service(TentackleLookAndFeel.class)
public class TTinyLookAndFeel extends TinyLookAndFeel implements TentackleLookAndFeel {

  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = LoggerFactory.getLogger(TTinyLookAndFeel.class);


  private Color mandatoryColor;               // mandatory background color for text fields and selected table cells
  private Color unselectedMandatoryColor;     // mandatory background color for unselected table cells
  private boolean focusAnimated = true;       // focus animation for non-textfield components



  public TTinyLookAndFeel() {
    super();
    loadInitialTheme();
    loadTentackleExtraColors();
  }


  /**
   * Sets up the initial theme.<p>
   * The default implementation loads "org/tentackle/swing/plaf/tinylaf/tentackle.theme".
   */
  public void loadInitialTheme() {
    URL url = TTinyLookAndFeel.class.getResource("tentackle.theme");
    if (url != null) {
      try {
        Theme.loadTheme(url);
      }
      catch (Exception e) {
        // something incompatible
        LOGGER.severe("incompatible tinylaf theme");
      }
    }
  }


  @Override
  public void loadTentackleExtraColors() {
    // automatic approach:
    //    Color baseColor = Theme.sub2Color.getColor();
    //    mandatoryColor = new ColorUIResource(PlafUtilities.getInstance().brighter(baseColor, 0.2));
    //    unselectedMandatoryColor = new ColorUIResource(PlafUtilities.getInstance().brighter(mandatoryColor, 0.8));
    // too weak.
    //
    // better use fixed colors (light yellow)
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




  public static String getTTinyName() {
    return "TTiny";
  }


  public static String getTTinyDescription() {
    return "Enhanced TinyLAF for Tentackle";
  }

  @Override
  public String getName() {
    return getTTinyName();
  }

  @Override
  public String getDescription() {
    return getTTinyDescription();
  }

  @Override
  public String getID() {
    return getName();
  }



  @Override
  protected void initClassDefaults(UIDefaults table)
  {
    super.initClassDefaults(table);

    final String tTinyPackageName = "org.tentackle.swing.plaf.tinylaf.";

    Object[] uiDefaults = {
               "ButtonUI",      tTinyPackageName + "TTinyButtonUI",       // replaced!
               "ComboBoxUI",    tTinyPackageName + "TTinyComboBoxUI",
               "RadioButtonUI", tTinyPackageName + "TTinyRadioButtonUI",
               "CheckBoxUI",    tTinyPackageName + "TTinyCheckBoxUI",
               "TextFieldUI",   tTinyPackageName + "TTinyTextFieldUI",
               "TextAreaUI",    tTinyPackageName + "TTinyTextAreaUI",
               "TableUI",       tTinyPackageName + "TTinyTableUI",
    };

    table.putDefaults(uiDefaults);
  }



  @Override
	protected void initComponentDefaults(UIDefaults table) {
		super.initComponentDefaults(table);
    Object[] defaults = new Object[] {
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



  @Override
  public ImageIcon loadImageIcon(String name) throws MissingResourceException {
    URL url = TTinyLookAndFeel.class.getResource("icons/" + name + ".png");
    if (url == null) {
      // try GIF
      url = TTinyLookAndFeel.class.getResource("icons/" + name + ".gif");
    }
    if (url == null) {
      throw new MissingResourceException("no such icon '" + name + "'", TTinyLookAndFeel.class.getName(), name);
    }
    return new ImageIcon(url);
  }

  @Override
  public void setFocusAnimated(boolean flag) {
    focusAnimated = flag;
  }

  @Override
  public boolean isFocusAnimated() {
    return focusAnimated;
  }


}
