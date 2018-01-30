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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import net.sf.tinylaf.TinyRadioButtonUI;
import org.tentackle.swing.FormRadioButton;
import org.tentackle.swing.plaf.TFlasherListener;
import org.tentackle.swing.plaf.TentackleLookAndFeel;

/**
 * UI for JRadioButton/FormRadioButton.
 *
 * @author harald
 */
public class TTinyRadioButtonUI extends TinyRadioButtonUI {

  private static final TTinyRadioButtonUI radioButtonUI = new TTinyRadioButtonUI();


  // ********************************
  //        Create PlAF
  // ********************************
  public static ComponentUI createUI(JComponent c) {
    return radioButtonUI;
  }


  // overridden to paint flashing focus and selected/unselected color
  @Override
  public synchronized void paint(Graphics g, JComponent c) {

    AbstractButton b = (AbstractButton) c;

    // harr
    Color selectedColor = null;
    Color unselectedColor = null;
    if (b instanceof FormRadioButton) {
      selectedColor = ((FormRadioButton) b).getSelectedColor();
      unselectedColor = ((FormRadioButton) b).getUnselectedColor();
    }

    ButtonModel model = b.getModel();

    Dimension size = c.getSize();

    Font f = c.getFont();
    g.setFont(f);
    FontMetrics fm = g.getFontMetrics();

    Rectangle viewRect = new Rectangle(size);
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();

    Insets i = c.getInsets();
    viewRect.x += i.left;
    viewRect.y += i.top;
    viewRect.width -= (i.right + viewRect.x);
    viewRect.height -= (i.bottom + viewRect.y);

    Icon altIcon = b.getIcon();
    String text = SwingUtilities.layoutCompoundLabel(
            c, fm, b.getText(), altIcon != null ? altIcon : getDefaultIcon(),
            b.getVerticalAlignment(), b.getHorizontalAlignment(),
            b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
            viewRect, iconRect, textRect, b.getIconTextGap());

    // fill background
    if (c.isOpaque()) {
      if (c instanceof FormRadioButton && ((FormRadioButton)c).isMandatory()) {
        g.setColor (UIManager.getColor("RadioButton.mandatoryBackground"));
      }
      else  {
        g.setColor(b.getBackground());
      }
      g.fillRect(0, 0, size.width, size.height);
    }


    // Paint the radio button
    if (altIcon != null) {

      if (!model.isEnabled()) {
        if (model.isSelected()) {
          altIcon = b.getDisabledSelectedIcon();
        }
        else {
          altIcon = b.getDisabledIcon();
        }
      }
      else if (model.isPressed() && model.isArmed()) {
        altIcon = b.getPressedIcon();
        if (altIcon == null) {
          // Use selected icon
          altIcon = b.getSelectedIcon();
        }
      }
      else if (model.isSelected()) {
        if (b.isRolloverEnabled() && model.isRollover()) {
          altIcon = b.getRolloverSelectedIcon();
          if (altIcon == null) {
            altIcon = b.getSelectedIcon();
          }
        }
        else {
          altIcon = b.getSelectedIcon();
        }
      }
      else if (b.isRolloverEnabled() && model.isRollover()) {
        altIcon = b.getRolloverIcon();
      }

      if (altIcon == null) {
        altIcon = b.getIcon();
      }

      altIcon.paintIcon(c, g, iconRect.x, iconRect.y);

    }
    else {
      getDefaultIcon().paintIcon(c, g, iconRect.x, iconRect.y);
    }


    // Draw the Text
    if (text != null) {
      View v = (View) c.getClientProperty(BasicHTML.propertyKey);
      if (v != null) {
        v.paint(g, textRect);
      }
      else {
        int mnemIndex = b.getDisplayedMnemonicIndex();
        // Harr
        if (selectedColor != null && b.isSelected()) {
          // *** paint the text normally
          g.setColor(selectedColor);
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, text,
                  mnemIndex, textRect.x, textRect.y + fm.getAscent());
        }
        else if (unselectedColor != null && !b.isSelected()) {
          // *** paint the text normally
          g.setColor(unselectedColor);
          BasicGraphicsUtils.drawStringUnderlineCharAt(g, text,
                  mnemIndex, textRect.x, textRect.y + fm.getAscent());
        }
        else {
          if (model.isEnabled()) {
            // *** paint the text normally
            g.setColor(b.getForeground());
            BasicGraphicsUtils.drawStringUnderlineCharAt(g, text,
                    mnemIndex, textRect.x, textRect.y + fm.getAscent());
          }
          else {
            // *** paint the text disabled
            g.setColor(getDisabledTextColor());
            BasicGraphicsUtils.drawStringUnderlineCharAt(g, text,
                    mnemIndex, textRect.x, textRect.y + fm.getAscent());
          }
        }
      }
    }

    if (c.hasFocus() && ((AbstractButton) c).isFocusPainted()) {
      paintFlashingFocus(g, c);
    }
  }


  // overridden to disable
  @Override
  protected void paintFocus(Graphics g, Rectangle t, Dimension d) {
    // don't draw standard focus border!
  }

  // replacing paintFocus()
  private void paintFlashingFocus(Graphics g, JComponent c) {
    g.setColor(!((TentackleLookAndFeel) UIManager.getLookAndFeel()).isFocusAnimated() ||
            TFlasherListener.isFlasherVisible() ? getFocusColor() : c.getBackground());
    g.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 2);
  }


  // overridden to install additional focus-Listener
  @Override
  protected void installListeners(AbstractButton b) {
    super.installListeners(b);
    if (((TentackleLookAndFeel) UIManager.getLookAndFeel()).isFocusAnimated()) {
      b.addFocusListener(new TFlasherListener(b));
    }
  }

  // overridden to remove additional listener again
  @Override
  protected void uninstallListeners(AbstractButton b) {
    super.uninstallListeners(b);
    TFlasherListener.removeFlasherListener(b);
  }

}
