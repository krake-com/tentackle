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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import javax.swing.CellRendererPane;
import javax.swing.DefaultButtonModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import net.sf.tinylaf.Theme;
import net.sf.tinylaf.TinyLookAndFeel;
import net.sf.tinylaf.borders.TinyButtonBorder;
import net.sf.tinylaf.util.ColorRoutines;
import net.sf.tinylaf.util.DrawRoutines;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.swing.FormComboBox;



/**
 * UI for combobox button.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class TTinyComboBoxButton extends JButton {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(TTinyComboBoxButton.class);


  static final int COMBO_BUTTON_WIDTH = 18;

	// cache for already drawn buttons
  private static final HashMap<ButtonKey, Image> CACHE = new HashMap<>();

  protected JComboBox<?> comboBox;

  protected JList<?> listBox;

  protected CellRendererPane rendererPane;

  protected Icon comboIcon;

  protected boolean iconOnly = false;

  private static BufferedImage focusImg;

  public static void clearCache() {
    CACHE.clear();
  }

  public final JComboBox<?> getComboBox() {
    return comboBox;
  }

  public final void setComboBox(JComboBox<?> cb) {
    comboBox = cb;
  }

  public final Icon getComboIcon() {
    return comboIcon;
  }

  public final void setComboIcon(Icon i) {
    comboIcon = i;
  }

  public final boolean isIconOnly() {
    return iconOnly;
  }

  public final void setIconOnly(boolean isIconOnly) {
    iconOnly = isIconOnly;
  }

  public TTinyComboBoxButton() {
    super("");

    setModel(new DefaultButtonModel() {
      @Override
      public void setArmed(boolean armed) {
        super.setArmed(isPressed() ? true : armed);
      }
    });

    // Set the background and foreground to the combobox colors.
    setBackground(UIManager.getColor("ComboBox.background"));
    setForeground(UIManager.getColor("ComboBox.foreground"));

    if (focusImg == null) {
      ImageIcon icon = TinyLookAndFeel.loadIcon("ComboBoxFocus.png");

      if (icon != null) {
        focusImg = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        Graphics g = focusImg.getGraphics();
        icon.paintIcon(this, g, 0, 0);
      }
    }
  }

  public TTinyComboBoxButton(JComboBox<?> cb, Icon i,
          boolean onlyIcon, CellRendererPane pane, JList<?> list) {
    this();
    comboBox = cb;
    comboIcon = i;
    rendererPane = pane;
    listBox = list;
    setEnabled(comboBox.isEnabled());
  }

  /**
   * Mostly taken from the swing sources.
   * 
   * @see javax.swing.JComponent#paintComponent(Graphics)
   */
  @SuppressWarnings("unchecked")
  @Override
  public void paintComponent(Graphics g) {
    Color panelBackground = getParent().getParent().getBackground();

		// With non-editable combo box we paint the whole combo,
    // with editable combo box we paint the arrow button only
    int h = getHeight();
    int w = getWidth();
    ButtonKey key = null;
    Image img = null;
    Graphics graphics = g;
    boolean cached = false;

    if (!TinyLookAndFeel.controlPanelInstantiated) {
      key = new ButtonKey(
              panelBackground,
              getSize(),
              comboBox.isEnabled(),
              comboBox.isEditable(),
              model.isPressed(),
              model.isRollover());
      Object value = CACHE.get(key);

      if (value != null) {
        // image already cached - paint image
        g.drawImage((Image) value, 0, 0, this);
				// Note: We can't return because the selected
        // value is not yet painted (non-editable combo
        // box only)
        if (comboBox.isEditable()) {
          return;
        }

        cached = true;
      }
      else {
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        graphics = img.getGraphics();
      }
    }

    boolean leftToRight = getComponentOrientation().isLeftToRight();

    if (!cached) {
      if (comboBox.isEnabled()) {
        if (comboBox.isEditable()) {
          graphics.setColor(Theme.textBgColor.getColor());
        }
        else {
          graphics.setColor(comboBox.getBackground());
        }
      }
      else {
        graphics.setColor(Theme.textDisabledBgColor.getColor());
      }

      graphics.fillRect(1, 1, w - 2, h - 2);

      // paint border background - next parent is combo box
      graphics.setColor(panelBackground);
      graphics.drawRect(0, 0, w - 1, h - 1);

      Color color;

      if (!isEnabled()) {
        color = Theme.comboButtDisabledColor.getColor();
      }
      else if (model.isPressed()) {
        color = Theme.comboButtPressedColor.getColor();
      }
      else if (model.isRollover()) {
        color = Theme.comboButtRolloverColor.getColor();
      }
      else {
        color = Theme.comboButtColor.getColor();
      }

      graphics.setColor(color);

      Rectangle buttonRect = new Rectangle(
              w - COMBO_BUTTON_WIDTH,
              1, COMBO_BUTTON_WIDTH, h - 2);

      drawXpButton(graphics, buttonRect, color);

      // draw border
      Border border = getBorder();

      if (border != null && (border instanceof TinyButtonBorder.CompoundBorderUIResource)) {
        if (!isEnabled()) {
          DrawRoutines.drawRoundedBorder(
                  graphics, Theme.comboBorderDisabledColor.getColor(), 0, 0, w, h);
        }
        else {
          DrawRoutines.drawRoundedBorder(
                  graphics, Theme.comboBorderColor.getColor(), 0, 0, w, h);

          if (!getModel().isPressed() && getModel().isRollover() &&
                  Theme.comboRollover.getValue()) {
            DrawRoutines.drawRolloverBorder(
                    graphics, Theme.buttonRolloverColor.getColor(), 0, 0, w, h);
          }
        }
      }

      if (key != null) {
        // dispose of image graphics
        graphics.dispose();

        // draw the image
        g.drawImage(img, 0, 0, this);

        // add the image to the cache
        CACHE.put(key, img);

        if (TinyLookAndFeel.PRINT_CACHE_SIZES) {
          LOGGER.info("TinyComboBoxButton.cache.size=" + CACHE.size());
        }
      }
    }

    // paint the selected value
    Insets insets = new Insets(
            Theme.comboInsets.top,
            Theme.comboInsets.left,
            Theme.comboInsets.bottom,
            0);

    int width = w - (insets.left + insets.right);
    int height = h - (insets.top + insets.bottom);

    if (height <= 0 || width <= 0) {
      return;
    }

    int left = insets.left;
    int top = insets.top;
    int right = left + (width - 1);
    int bottom = top + (height - 1);

    int iconWidth = COMBO_BUTTON_WIDTH;

    // Let the renderer paint
    Component c = null;
    boolean mustResetOpaque = false;
    boolean savedOpaque = false;
    boolean paintFocus = false;

    if (!iconOnly && comboBox != null) {
      ListCellRenderer renderer = comboBox.getRenderer();
      boolean rendererSelected = getModel().isPressed();
      c = renderer.getListCellRendererComponent(listBox,
              comboBox.getSelectedItem(), -1, rendererSelected, false);
      c.setFont(rendererPane.getFont());

      if (model.isArmed() && model.isPressed()) {
        if (isOpaque()) {
          // defaults to ColorUIResource[r=167,g=165,b=163]
          c.setBackground(UIManager.getColor("Button.select"));
        }
        c.setForeground(comboBox.getForeground());
      }
      else if (!comboBox.isEnabled()) {
        if (isOpaque()) {
          c.setBackground(Theme.textDisabledBgColor.getColor());
        }
        else {
          comboBox.setBackground(Theme.textDisabledBgColor.getColor());
        }
        c.setForeground(UIManager.getColor("ComboBox.disabledForeground"));
      }
      else if (comboBox.hasFocus() && !comboBox.isPopupVisible()) {
        if (comboBox.isEditable()) {
          c.setForeground(Theme.mainColor.getColor());
        }
        else {
          c.setForeground(UIManager.getColor("ComboBox.selectionForeground"));
        }

        c.setBackground(UIManager.getColor("ComboBox.focusBackground"));

        if (c instanceof JComponent) {
          mustResetOpaque = true;
          JComponent jc = (JComponent) c;
          savedOpaque = jc.isOpaque();
          jc.setOpaque(true);
          paintFocus = true;
        }
      }
      else {
        c.setForeground(comboBox.getForeground());
        if (!comboBox.isEditable() && comboBox instanceof FormComboBox && ((FormComboBox) comboBox).isMandatory()) {
          c.setBackground(UIManager.getColor("ComboBox.mandatoryBackground"));
        }
        else {
          c.setBackground(comboBox.getBackground());
        }
      }

      int cWidth = width - (insets.right + iconWidth);

      // Fix for 4238829: should lay out the JPanel.
      boolean shouldValidate = (c instanceof JPanel);

      if (leftToRight) {
        rendererPane.paintComponent(g, c, this, left, top, cWidth, height, shouldValidate);
      }
      else {
        rendererPane.paintComponent(g, c, this, left + iconWidth, top, cWidth, height, shouldValidate);
      }

      if (paintFocus && Theme.comboFocus.getValue()) {
        g.setColor(Color.black);
        Graphics2D g2d = (Graphics2D) g;
        Rectangle r = new Rectangle(left, top, 2, 2);
        TexturePaint tp = new TexturePaint(focusImg, r);

        g2d.setPaint(tp);
        g2d.draw(new Rectangle(left, top, cWidth, height));
      }
    }

    if (mustResetOpaque) {
      JComponent jc = (JComponent) c;
      jc.setOpaque(savedOpaque);
    }
  }

  private void drawXpButton(Graphics g, Rectangle buttonRect, Color c) {
    int x2 = buttonRect.x + buttonRect.width;
    int y2 = buttonRect.y + buttonRect.height;

    int spread1 = Theme.comboSpreadLight.getValue();
    int spread2 = Theme.comboSpreadDark.getValue();
    if (!isEnabled()) {
      spread1 = Theme.comboSpreadLightDisabled.getValue();
      spread2 = Theme.comboSpreadDarkDisabled.getValue();
    }

    int h = buttonRect.height - 2;
    float spreadStep1 = 10.0f * spread1 / (h - 3);
    float spreadStep2 = 10.0f * spread2 / (h - 3);
    int halfY = h / 2;
    int yd;

    for (int y = 1; y < h - 1; y++) {
      if (y < halfY) {
        yd = halfY - y;
        g.setColor(ColorRoutines.lighten(c, (int) (yd * spreadStep1)));
      }
      else if (y == halfY) {
        g.setColor(c);
      }
      else {
        yd = y - halfY;
        g.setColor(ColorRoutines.darken(c, (int) (yd * spreadStep2)));
      }

      g.drawLine(buttonRect.x + 1, buttonRect.y + y + 1, buttonRect.x + buttonRect.width - 3, buttonRect.y + y + 1);
    }

    // draw the button border
    Color col;
    if (!isEnabled()) {
      col = Theme.comboButtBorderDisabledColor.getColor();
    }
    else {
      col = Theme.comboButtBorderColor.getColor();
    }
    g.setColor(col);
    g.drawLine(buttonRect.x + 2, buttonRect.y + 1, x2 - 4, buttonRect.y + 1);
    g.drawLine(buttonRect.x + 1, buttonRect.y + 2, buttonRect.x + 1, y2 - 3);
    g.drawLine(x2 - 3, buttonRect.y + 2, x2 - 3, y2 - 3);
    g.drawLine(buttonRect.x + 2, y2 - 2, x2 - 4, y2 - 2);

    // corners
    col = new Color(col.getRed(), col.getGreen(), col.getBlue(), 128);
    g.setColor(col);
    g.drawLine(buttonRect.x + 1, buttonRect.y + 1, buttonRect.x + 1, buttonRect.y + 1);
    g.drawLine(x2 - 3, buttonRect.y + 1, x2 - 3, buttonRect.y + 1);
    g.drawLine(buttonRect.x + 1, y2 - 2, buttonRect.x + 1, y2 - 2);
    g.drawLine(x2 - 3, y2 - 2, x2 - 3, y2 - 2);

    // draw arrow
    if (isEnabled()) {
      g.setColor(Theme.comboArrowColor.getColor());
    }
    else {
      g.setColor(Theme.comboArrowDisabledColor.getColor());
    }

    drawXpArrow(g, buttonRect);
  }

  private void drawXpArrow(Graphics g, Rectangle r) {
    int x = r.x + (r.width - 8) / 2 - 1;
    int y = r.y + (r.height - 6) / 2 + 1;

    g.drawLine(x + 1, y, x + 1, y);
    g.drawLine(x + 7, y, x + 7, y);
    g.drawLine(x, y + 1, x + 2, y + 1);
    g.drawLine(x + 6, y + 1, x + 8, y + 1);
    g.drawLine(x + 1, y + 2, x + 3, y + 2);
    g.drawLine(x + 5, y + 2, x + 7, y + 2);
    g.drawLine(x + 2, y + 3, x + 6, y + 3);
    g.drawLine(x + 3, y + 4, x + 5, y + 4);
    g.drawLine(x + 4, y + 5, x + 4, y + 5);
  }

  private static class ButtonKey {

    private final Color panelBackground;

    private final Dimension size;

    private final boolean enabled;

    private final boolean editable;

    private final boolean pressed;

    private final boolean rollover;

    ButtonKey(Color background, Dimension size,
            boolean enabled, boolean editable, boolean pressed, boolean rollover) {
      this.panelBackground = background;
      this.size = size;
      this.enabled = enabled;
      this.editable = editable;
      this.pressed = pressed;
      this.rollover = rollover;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null) {
        return false;
      }
      if (!(o instanceof ButtonKey)) {
        return false;
      }

      ButtonKey other = (ButtonKey) o;

      return enabled == other.enabled &&
              editable == other.editable &&
              pressed == other.pressed &&
              rollover == other.rollover &&
              panelBackground.equals(other.panelBackground) &&
              size.equals(other.size);
    }

    @Override
    public int hashCode() {
      return panelBackground.hashCode() *
              size.hashCode() *
              (enabled ? 2 : 1) *
              (editable ? 8 : 4) *
              (pressed ? 32 : 16) *
              (rollover ? 128 : 64);
    }
  }
}
