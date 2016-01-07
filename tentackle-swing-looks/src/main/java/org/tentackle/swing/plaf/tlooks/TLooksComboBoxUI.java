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

import com.jgoodies.looks.plastic.PlasticComboBoxUI;
import java.awt.Color;
import javax.swing.UIManager;
import org.tentackle.swing.plaf.TComboPopup;
import org.tentackle.swing.plaf.TFlasherListener;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.plaf.metal.MetalComboBoxUI;
import org.tentackle.swing.plaf.TentackleLookAndFeel;
import org.tentackle.swing.FormComboBox;


/**
 * UI for JComboBox/FormComboBox/FormFieldComboBox.
 * 
 * @author harald
 */
public class TLooksComboBoxUI extends PlasticComboBoxUI {

  private static Color mandatoryColor;    // hack because we need a real Color and not a ColorUIResource

  
  // ********************************
  //        Create PlAF
  // ********************************
  public static ComponentUI createUI(JComponent c) {
      PlasticComboBoxUI.createUI(c);
      return new TLooksComboBoxUI();    // one per component
  }
    
  @Override
  protected void installListeners() {
    super.installListeners();
    if (((TentackleLookAndFeel)UIManager.getLookAndFeel()).isFocusAnimated()) {
      comboBox.addFocusListener(new TComboBoxFlasherListener());
    }
  }

  @Override
  protected void uninstallListeners() {
    super.uninstallListeners();
    TFlasherListener.removeFlasherListener(comboBox);
  }

  private Color getMandatoryColor() {
    Color color = UIManager.getColor("ComboBox.mandatoryBackground");
    if (mandatoryColor == null) {
      mandatoryColor = new Color(color.getRGB());
    }
    return mandatoryColor;
  }

  private boolean isArrowButtonShownAsMandatory() {
    return !comboBox.isEditable() && comboBox instanceof FormComboBox && ((FormComboBox) comboBox).isMandatory();
  }

  @Override
  public PropertyChangeListener createPropertyChangeListener() {
    return new TMetalPropertyChangeListener();
  }


  public class TMetalPropertyChangeListener extends MetalComboBoxUI.PropertyChangeHandler {

    @Override
    public void propertyChange(PropertyChangeEvent e) {
      super.propertyChange(e);

      String propertyName = e.getPropertyName();

      if (propertyName.equals("background")) {
        Color color = (Color) e.getNewValue();
        if (isArrowButtonShownAsMandatory()) {
          arrowButton.setBackground(getMandatoryColor());
        }
        else {
          arrowButton.setBackground(color);
        }
        listBox.setBackground(color);
      }
      else if (propertyName.equals("mandatory") ||
               propertyName.equals("editable")  ||
               propertyName.equals("enabled")) {
        Color color = isArrowButtonShownAsMandatory() ?
                          getMandatoryColor() :
                          listBox.getBackground();
        arrowButton.setBackground(color);
      }
    }
  }


  @Override
  protected JButton createArrowButton() {
    JButton button = super.createArrowButton();
    if (isArrowButtonShownAsMandatory()) {
      button.setBackground(getMandatoryColor());
    }
    return button;
  }


  @Override
  public void configureArrowButton() {
    super.configureArrowButton();
    if (isArrowButtonShownAsMandatory()) {
      arrowButton.setBackground(getMandatoryColor());
    }
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
              g.drawRect(left-1, top+1, width, height-4);
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
