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


package org.tentackle.swing.plaf.tnimbus;

import org.tentackle.common.Service;
import org.tentackle.swing.plaf.PlafUtilities;
import org.tentackle.swing.plaf.TentackleLookAndFeel;

import javax.swing.ImageIcon;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.Color;
import java.net.URL;
import java.util.MissingResourceException;



/**
 * Extended Tentackle Nimbus Look And Feel.
 *
 * TODO: all tentackle features still missing, just rudimentary
 * @author harald
 */
@Service(TentackleLookAndFeel.class)
public class TNimbusLookAndFeel extends NimbusLookAndFeel implements TentackleLookAndFeel {

  private static final long serialVersionUID = 1L;


  private Color mandatoryColor;               // mandatory background color for text fields and selected table cells
  private Color unselectedMandatoryColor;     // mandatory background color for unselected table cells
  private boolean focusAnimated = true;       // focus animation for non-textfield components


  /**
   * Creates a new instance of TMetalLookAndFeel
   */
  public TNimbusLookAndFeel() {
    super();
  }

  public static String getTNimbusName() {
      return "TNimbus";
  }

  public static String getTNimbusDescription() {
      return "Enhanced Nimbus LnF for Tentackle";
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


  /**
   * Implements TentackleLookAndFeel.
   */
  @Override
  public ImageIcon loadImageIcon(String name) throws MissingResourceException {
    URL url = TNimbusLookAndFeel.class.getResource("icons/" + name + ".png");
    if (url == null) {
      // try GIF
      url = TNimbusLookAndFeel.class.getResource("icons/" + name + ".gif");
    }
    if (url == null) {
      throw new MissingResourceException("no such icon '" + name + "'", TNimbusLookAndFeel.class.getName(), name);
    }
    return new ImageIcon(url);
  }


  /**
   * Configures the animated keyboard focus for non-text components that can grab
   * the keyboard focus, such as comboboxes, radio buttons or check boxes.
   * By default, the animation is enabled.
   *
   * @param flag the boolean value which is true to enable the animation, false to turn it off
   */
  @Override
  public void setFocusAnimated(boolean flag) {
    focusAnimated = flag;
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
