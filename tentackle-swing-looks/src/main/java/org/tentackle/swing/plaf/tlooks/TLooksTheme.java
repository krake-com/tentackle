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


package org.tentackle.swing.plaf.tlooks;

import com.jgoodies.looks.plastic.PlasticTheme;
import java.awt.Color;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;


/**
 * Default theme.
 * 
 * @author harald
 */
public class TLooksTheme extends PlasticTheme {


  private static final Color mandatoryColor = new ColorUIResource(255, 255, 170);

  protected Color getMandatoryColor() {
    return mandatoryColor;
  }


  @Override
  public void addCustomEntriesToTable (UIDefaults table) {
    super.addCustomEntriesToTable(table);

    Object[] defaults = new Object[] {
            "TextField.mandatoryBackground", getMandatoryColor(),
            "TextArea.mandatoryBackground", getMandatoryColor(),
            "TextArea.inactiveBackground", table.getColor("control"),
            "ComboBox.mandatoryBackground", getMandatoryColor(),
            "CheckBox.mandatoryBackground", getMandatoryColor(),
            "RadioButton.mandatoryBackground", getMandatoryColor(),
    };

    table.putDefaults(defaults);
  }

  
}
