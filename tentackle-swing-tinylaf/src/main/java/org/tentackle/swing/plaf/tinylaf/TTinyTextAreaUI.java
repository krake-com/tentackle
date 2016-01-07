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

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import net.sf.tinylaf.TinyTextAreaUI;
import org.tentackle.swing.FormComponent;
import org.tentackle.swing.FormTextArea;



/**
 * UI for JTextArea/FormTextArea.
 * Adds mandatory property.
 *
 * @author  harald
 */
public class TTinyTextAreaUI extends TinyTextAreaUI {

  // ********************************
  //        Create PlAF
  // ********************************
  public static ComponentUI createUI(JComponent c) {
    return new TTinyTextAreaUI();
  }


  public TTinyTextAreaUI() {
    super();
  }



  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    if (c instanceof FormTextArea &&
        (((FormTextArea)c).isMandatory() || !((FormTextArea)c).isEditable())) {
      updateBackground((JTextArea) c);
    }
  }


  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals("mandatory") ||
        evt.getPropertyName().equals("editable") ||
        evt.getPropertyName().equals("enabled")) {
      updateBackground((JTextArea) evt.getSource());
    }
    else {
      super.propertyChange(evt);
    }
  }




  // copied from BasicTextUI and extended by mandatory option


  /**
   * Updates the background of the text component based on whether the
   * text component is editable and/or enabled.
   *
   * @param c the JTextArea that needs its background color updated
   */
  private void updateBackground(JTextArea c) {
    Color background = c.getBackground();
    if (background instanceof UIResource) {
      String prefix = getPropertyPrefix();

      Color mandatoryBG =
              UIManager.getColor(prefix + ".mandatoryBackground");
      Color disabledBG =
              UIManager.getColor(prefix + ".disabledBackground");
      Color inactiveBG =
              UIManager.getColor(prefix + ".inactiveBackground");
      Color bg =
              UIManager.getColor(prefix + ".background");

      /* In an ideal situation, the following check would not be necessary
       * and we would replace the color any time the previous color was a
       * UIResouce. However, it turns out that there is existing code that
       * uses the following inadvisable pattern to turn a text area into
       * what appears to be a multi-line label:
       *
       * JLabel label = new JLabel();
       * JTextArea area = new JTextArea();
       * area.setBackground(label.getBackground());
       * area.setEditable(false);
       *
       * JLabel's default background is a UIResource. As such, just
       * checking for UIResource would have us always changing the
       * background away from what the developer wanted.
       *
       * Therefore, for JTextArea/JEditorPane, we'll additionally check
       * that the color we're about to replace matches one that was
       * installed by us from the UIDefaults.
       */
      if (background != disabledBG &&
          background != inactiveBG &&
          background != mandatoryBG &&
          background != bg) {

        return;
      }

      Color newColor = null;
      if (!c.isEnabled()) {
        newColor = disabledBG;
      }

      if (newColor == null && !c.isEditable()) {
        newColor = inactiveBG;
      }

      if (newColor == null && c instanceof FormComponent && ((FormComponent)c).isMandatory()) {
        newColor = mandatoryBG;
      }

      if (newColor == null) {
        newColor = bg;
      }

      if (newColor != null && newColor != background) {
        c.setBackground(newColor);
      }
    }
  }

}
