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


package org.tentackle.swing.plaf.tplastic;

import javax.swing.UIDefaults;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;
import javax.swing.plaf.metal.OceanTheme;
import org.tentackle.common.Service;
import org.tentackle.swing.plaf.TentackleLookAndFeel;
import org.tentackle.swing.plaf.tmetal.TMetalLookAndFeel;
import org.tentackle.swing.plaf.tmetal.TOceanLookAndFeel;



/**
 * Extended Tentackle Kunststoff Look-And-Feel.
 * <p>
 * Kunststoff is a discontinued project, so we
 * switched to Ocean in order not to break existing applications.
 * New applications should not use TPlastic, but TLooks or TTiny instead.
 * 
 * @author harald
 */
@SuppressWarnings("serial")
@Service(TentackleLookAndFeel.class)
public class TPlasticLookAndFeel extends TOceanLookAndFeel {


  public static String getTPlasticName() {
    return "TPlastic";
  }

  public static String getTPlasticDescription() {
    return "Enhanced Ocean LnF for Tentackle";
  }


  public TPlasticLookAndFeel() {
    super();
  }

  @Override
  public String getName() {
    return getTPlasticName();
  }

  @Override
  public String getDescription() {
    return getTPlasticDescription();
  }

  @Override
  public void initialize() {
    super.initialize();
    TMetalLookAndFeel.setCurrentTheme(new TPlasticTheme());
  }



  @Override
  protected void initComponentDefaults(UIDefaults table) {

    super.initComponentDefaults(table);

    //
    // DEFAULTS TABLE
    //
    OceanTheme currentTheme = (OceanTheme) MetalLookAndFeel.getCurrentTheme();
    if (currentTheme != null) {

      Object userTextValue = new FontActiveValue(currentTheme, TPlasticTheme.USER_TEXT_FONT);
      Object controlTextValue = new FontActiveValue(currentTheme, TPlasticTheme.CONTROL_TEXT_FONT);

      // changes the values so that all user-input fields are marked bold and
      // background data is plain.
      Object[] defaults = {
        "Label.font", userTextValue,
        "TextField.font", controlTextValue,
        "TextArea.font", controlTextValue,
        "PasswordField.font", controlTextValue,
        "Table.font", controlTextValue,
        "TitledBorder.font", userTextValue,};

      table.putDefaults(defaults);
    }
  }

  /**
   * FontActiveValue redirects to the appropriate metal theme method.
   * Copied from MetalTheme (package scope, argh...)
   */
  private static class FontActiveValue implements UIDefaults.ActiveValue {

    private int type;
    private MetalTheme theme;

    FontActiveValue(MetalTheme theme, int type) {
      this.theme = theme;
      this.type = type;
    }

    public Object createValue(UIDefaults table) {
      Object value = null;
      switch (type) {
        case TPlasticTheme.CONTROL_TEXT_FONT:
          value = theme.getControlTextFont();
          break;
        case TPlasticTheme.SYSTEM_TEXT_FONT:
          value = theme.getSystemTextFont();
          break;
        case TPlasticTheme.USER_TEXT_FONT:
          value = theme.getUserTextFont();
          break;
        case TPlasticTheme.MENU_TEXT_FONT:
          value = theme.getMenuTextFont();
          break;
        case TPlasticTheme.WINDOW_TITLE_FONT:
          value = theme.getWindowTitleFont();
          break;
        case TPlasticTheme.SUB_TEXT_FONT:
          value = theme.getSubTextFont();
          break;
      }
      return value;
    }
  }

}
