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


package org.tentackle.swing.plaf;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * A listener to implement focus flashing.
 * 
 * @author harald
 */
public class TFlasherListener implements FocusListener, ActionListener {
    
  private static Timer flasher; // one timer for all components!
  
 
  /**
   * Checks whether flasher is visible.
   * 
   * @return true if visible
   */
  public static boolean isFlasherVisible() {
    return flasherVisible;
  }
  
  
  /**
   * Removes a flasher listener from the given comp, if any.
   * @param comp the component
   */
  public static void removeFlasherListener(JComponent comp)  {
    FocusListener[] listeners = comp.getFocusListeners();
    if (listeners != null) {
      for (int counter = 0; counter < listeners.length; counter++) {
        if (listeners[counter] instanceof TFlasherListener) {
          comp.removeFocusListener(listeners[counter]);
        }
      }
    }
  }
  
  
  
  
  /** true if flasher visible. */
  protected static boolean flasherVisible;

  /** the component having the focus. */
  protected final JComponent comp;

  
  /**
   * Creates a flasher listener.
   * 
   * @param comp the component to "flash"
   */
  public TFlasherListener(JComponent comp) {
    this.comp = comp;
    if (flasher == null)  {
      // setup timer
      flasher = new Timer(500, null);
      flasher.start();
    }
  }
  
  public void actionPerformed(ActionEvent e)  {
    flasherVisible = !flasherVisible;
    comp.repaint();
  }

  public void focusGained(FocusEvent e) {
    flasher.addActionListener(this);
  }

  public void focusLost(FocusEvent e) {
    flasher.removeActionListener(this);
    comp.repaint();
  }
 
}
