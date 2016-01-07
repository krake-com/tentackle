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


package org.tentackle.swing.plaf.tmetal;

import javax.swing.UIManager;
import org.tentackle.swing.plaf.TFlasherListener;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalButtonUI;
import org.tentackle.swing.plaf.TentackleLookAndFeel;

/**
 * UI for JButton/FormButton.
 * 
 * @author harald
 */
public class TMetalButtonUI extends MetalButtonUI {
  
  private final static TMetalButtonUI tMetalButtonUI = new TMetalButtonUI();
  

  // ********************************
  //          Create PLAF
  // ********************************
  public static ComponentUI createUI(JComponent c) {
      return tMetalButtonUI;    // one for all buttons
  }  
    
  
  // overridden to install additional focus-Listener
  protected void installListeners(AbstractButton b) {
    super.installListeners(b);
    if (((TentackleLookAndFeel)UIManager.getLookAndFeel()).isFocusAnimated()) {
      b.addFocusListener(new TFlasherListener(b));
    }
  }

  // overridden to remove additional listener again
  protected void uninstallListeners(AbstractButton b) {
    super.uninstallListeners(b);
    TFlasherListener.removeFlasherListener(b);
  }
  
  
  /**
   * overridden to flash the focus-rectangle
   */
  protected void paintFocus(Graphics g, AbstractButton b,
                            Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {

    Rectangle focusRect = new Rectangle();
    String text         = b.getText();
    boolean isIcon      = b.getIcon() != null;

    // if there is text
    if (text != null && !text.equals( "" )) {
      if (!isIcon) {
        focusRect.setBounds(textRect);
      }
      else {
        focusRect.setBounds(iconRect.union(textRect));
      }
    }
    // if there is an icon and no text
    else if (isIcon) {
      focusRect.setBounds(iconRect);
    }

    g.setColor(!((TentackleLookAndFeel)UIManager.getLookAndFeel()).isFocusAnimated() ||
               TFlasherListener.isFlasherVisible() ? getFocusColor() : b.getBackground());
    g.drawRect((focusRect.x-1), (focusRect.y-1), focusRect.width+1, focusRect.height+1);
  }
     
}


