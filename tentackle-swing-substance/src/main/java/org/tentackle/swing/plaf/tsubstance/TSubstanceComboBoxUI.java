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

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.ComboPopup;
import org.jvnet.substance.SubstanceComboBoxUI;
import org.tentackle.swing.plaf.TComboPopup;


/**
 * UI for JComboBox/FormComboBox/FormFieldComboBox.
 *
 * TODO: mandatory property still missing
 *
 * @author harald
 */
public class TSubstanceComboBoxUI extends SubstanceComboBoxUI {


  // ********************************
  //        Create PlAF
  // ********************************
  public static ComponentUI createUI(JComponent c) {
    TSubstanceComboBoxUI ui = new TSubstanceComboBoxUI();
    ui.comboBox = (JComboBox) c;
    return ui;
  }


  /**
   * Overridden to install special popup that recognizes
   * FormComboBoxRenderer.
   *
   * @return an instance of <code>ComboPopup</code>
   * @see ComboPopup
   */
  protected ComboPopup createPopup() {
    return new TComboPopup(comboBox);
  }

}
