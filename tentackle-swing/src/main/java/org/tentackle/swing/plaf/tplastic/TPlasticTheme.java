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

import java.awt.Color;
import javax.swing.plaf.ColorUIResource;
import org.tentackle.swing.plaf.tmetal.TOceanTheme;

/**
 *
 * @author harald
 */
public class TPlasticTheme extends TOceanTheme {

  // Contants identifying the various Fonts that a theme can support
  static final int CONTROL_TEXT_FONT = 0;
  static final int SYSTEM_TEXT_FONT = 1;
  static final int USER_TEXT_FONT = 2;
  static final int MENU_TEXT_FONT = 3;
  static final int WINDOW_TITLE_FONT = 4;
  static final int SUB_TEXT_FONT = 5;
  
  private static final ColorUIResource primary1 = new ColorUIResource(89, 122, 193);
  private static final ColorUIResource primary2 = new ColorUIResource(110, 151, 239);
  private static final ColorUIResource primary3 = new ColorUIResource(170, 197, 255);

  private static final ColorUIResource secondary1 = new ColorUIResource(102, 102, 132);
  private static final ColorUIResource secondary2 = new ColorUIResource(150, 150, 192);
  private static final ColorUIResource secondary3 = new ColorUIResource(222, 222, 236);

  
  private static final Color mandatoryColor = new ColorUIResource(255, 255, 170);

  @Override
  protected Color getMandatoryColor() {
    return mandatoryColor;
  }

  public TPlasticTheme() {
    super();
  }



  @Override
  protected ColorUIResource getPrimary1() {
    return primary1;
  }

  @Override
  protected ColorUIResource getPrimary2() {
    return primary2;
  }

  @Override
  protected ColorUIResource getPrimary3() {
    return primary3;
  }

  @Override
  protected ColorUIResource getSecondary1() {
    return secondary1;
  }

  @Override
  protected ColorUIResource getSecondary2() {
    return secondary2;
  }

  @Override
  protected ColorUIResource getSecondary3() {
    return secondary3;
  }


}
