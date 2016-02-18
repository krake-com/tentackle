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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import org.tentackle.misc.ShortLongText;

/**
 * Special PopupRenderer that renders two columns according
 * to {@code ShortLongText} objects.
 *
 * @author harald
 * @see org.tentackle.swing.FormComboBox#setShortLongPopupEnabled(boolean)
 */
@SuppressWarnings("serial")
public class TPopupRenderer extends JPanel implements ListCellRenderer {

  private final JLabel leftLabel;       // label for short text
  private final JLabel rightLabel;      // label for long text
  private int minLeftWidth;             // minumum width for short text
  private int minRightWidth;            // minimum width for long text
  private String text;                  // last rendered text (for GUIDancer)



  /**
   * Creates a popup renderer.
   */
  public TPopupRenderer() {
    super();
    setLayout(new FlowLayout(FlowLayout.LEFT, 1, 0));

    leftLabel = new JLabel() {
      @Override
      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        if (d.width < minLeftWidth) {
          d.width = minLeftWidth;
        }
        return d;
      }
    };

    rightLabel = new JLabel() {
      @Override
      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        if (d.width < minRightWidth) {
          d.width = minRightWidth;
        }
        return d;
      }
    };

    add(leftLabel);
    add(rightLabel);
  }


  /**
   * Sets the minimum width for the short text.
   *
   * @param width the minimum width
   */
  public void setMinLeftWidth(int width) {
    minLeftWidth = width;
  }

  /**
   * Gets the minimum width for the short text.
   *
   * @return the minimum width
   */
  public int getLeftWidth() {
    return leftLabel.getPreferredSize().width;
  }


  /**
   * Sets the minimum width for the long text.
   *
   * @param width the minimum width
   */
  public void setMinRightWidth(int width) {
    minRightWidth = width;
  }

  /**
   * Gets the minimum width for the long text.
   *
   * @return the minimum width
   */
  public int getRightWidth() {
    return rightLabel.getPreferredSize().width;
  }


  /**
   * Gets the last rendered text.<br>
   * For ShortLongText this is always the short text.
   * The method is provided for GUIdancer &ge; 4.3
   *
   * @return the last rendered text
   */
  public String getText() {
    return text;
  }



  @Override
  public Component getListCellRendererComponent(JList list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus) {

    if (isSelected) {
      setBackground(list.getSelectionBackground());
      setForeground(list.getSelectionForeground());
    }
    else {
      setBackground(list.getBackground());
      setForeground(list.getForeground());
    }

    leftLabel.setFont(list.getFont());
    leftLabel.setForeground(getForeground());
    rightLabel.setFont(list.getFont());
    rightLabel.setForeground(getForeground());

    if (value instanceof ShortLongText) {
      String shortText = ((ShortLongText) value).getShortText();
      String longText  = ((ShortLongText) value).getLongText();
      leftLabel.setText(shortText);
      rightLabel.setText(longText);
      text = (shortText == null ? "" : shortText) + "|" +
             (longText == null ? "" : longText);
    }
    else  {
      text = value == null ? " " : value.toString();
      leftLabel.setText(text);
      rightLabel.setText(" ");
    }

    return this;
  }

}
