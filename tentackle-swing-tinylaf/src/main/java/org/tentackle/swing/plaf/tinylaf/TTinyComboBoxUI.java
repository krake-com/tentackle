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

import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.plaf.metal.MetalComboBoxUI;
import net.sf.tinylaf.TinyComboBoxUI;
import org.tentackle.swing.plaf.TComboPopup;
import org.tentackle.swing.plaf.TFlasherListener;
import org.tentackle.swing.plaf.TentackleLookAndFeel;


/**
 * UI for JComboBox/FormComboBox/FormFieldComboBox.
 *
 * @author harald
 */
public class TTinyComboBoxUI extends TinyComboBoxUI {

  private static final Insets DEFAULT_INSETS = new Insets(0, 0, 0, 0);



  // ********************************
  //        Create PlAF
  // ********************************
  public static ComponentUI createUI(JComponent c) {
      return new TTinyComboBoxUI();    // one per component
  }

  @Override
  protected void installListeners() {
    super.installListeners();
    if (((TentackleLookAndFeel) UIManager.getLookAndFeel()).isFocusAnimated()) {
      comboBox.addFocusListener(new TComboBoxFlasherListener());
    }
  }

  @Override
  protected void uninstallListeners() {
    super.uninstallListeners();
    TFlasherListener.removeFlasherListener(comboBox);
  }



  @Override
  public PropertyChangeListener createPropertyChangeListener() {
    return new TMetalPropertyChangeListener();
  }


  public class TMetalPropertyChangeListener extends MetalComboBoxUI.PropertyChangeHandler {

    @Override
    public void propertyChange(PropertyChangeEvent e) {
      String propertyName = e.getPropertyName();

      if (propertyName.equals("mandatory")) {
        comboBox.repaint();
      }
      else  {
        super.propertyChange(e);
      }
    }
  }


  @Override
  protected JButton createArrowButton() {
 		JButton button = new TTinyComboBoxButton(comboBox, null, comboBox.isEditable(), currentValuePane, listBox);
    button.setMargin(DEFAULT_INSETS);
    button.putClientProperty("isComboBoxButton", Boolean.TRUE);
    return button;
  }



  /**
   * Overridden to install special popup that recognizes
   * FormComboBoxRenderer.
   *
   * @return an instance of <code>ComboPopup</code>
   * @see ComboPopup
   */
  @Override
  protected ComboPopup createPopup() {
    return new TComboPopup(comboBox);
  }




  private class TComboBoxFlasherListener extends TFlasherListener {

    public TComboBoxFlasherListener() {
      super(comboBox);
    }

    @Override
    public void actionPerformed(ActionEvent e)  {
      if (!comboBox.isEditable()) {
        flasherVisible = !flasherVisible;
        if (!flasherVisible)  {
          Graphics g = arrowButton.getGraphics();
          if (g != null)  {
            // remove the focus
            g.setColor(arrowButton.getBackground());
            Insets insets = arrowButton.getInsets();
            int width = arrowButton.getWidth() - (insets.left + insets.right);
            int height = arrowButton.getHeight() - (insets.top + insets.bottom);
            if (height > 0 && width > 0) {
              int left = insets.left;
              int top = insets.top;
              g.drawRect(left, top, width-1, height-1);
            }
          }
        }
        else  {
          arrowButton.repaint();
        }
      }
    }
  }
}
