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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.StringTokenizer;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.basic.BasicToolTipUI;



/**
 * Multi-line tooltip UI.<br>
 *
 * See the static init block in {@link FormHelper}.
 * <p>
 * Notice: the code is derived from {@code xdocletgui.swing.MultiLineToolTipUI}.
 */
public class MultiLineToolTipUI extends BasicToolTipUI {

  static MultiLineToolTipUI sharedInstance = new MultiLineToolTipUI();
  static final int MARGIN = 2;


  /**
   * MultiLineToolTipUI, constructor.
   * <p>
   * Protected so we can be subclassed,
   * but not created by client classes.
   **/
  protected MultiLineToolTipUI() {
    super();
  }


  /**
   * Create the UI component for the given component.
   * The same UI can be shared for all components, so
   * return our shared instance.
   * <p>
   * @param c The component to create the UI for.
   * @return Our shared UI component instance.
   **/
  public static ComponentUI createUI(JComponent c) {
    return sharedInstance;
  }


  /**
   * Paints the ToolTip. Use the current font and colors
   * set for the given component.
   */
  @Override
  public void paint(Graphics g, JComponent c) {
    if (c.getClientProperty(BasicHTML.propertyKey) != null) {
      super.paint(g, c);
    }
    else  {
      // Determine the size for each row.
      Font font = c.getFont();
      FontMetrics fontMetrics = c.getFontMetrics(font);
      int fontHeight = fontMetrics.getHeight();
      int fontAscent = fontMetrics.getAscent();

      // Paint the background in the tip color.
      g.setColor(c.getBackground());
      Dimension size = c.getSize();
      g.fillRect(0, 0, size.width, size.height);

      // Paint each line in the tip using the
      // foreground color. Use a StringTokenizer
      // to parse the ToolTip. Each line is left-justified,
      // and the y coordinate is updated through the loop.
      String tipText = ((JToolTip)c).getTipText();
      if (tipText != null)  {
        g.setColor(c.getForeground());
        int y = 2 + fontAscent;
        StringTokenizer tokenizer = new StringTokenizer(tipText, "\n");
        int numberOfLines = tokenizer.countTokens();
        for(int i = 0; i < numberOfLines; i++) {
          g.drawString(tokenizer.nextToken(), MARGIN, y);
          y += fontHeight;
        }
      }
    }
  }


  /**
   * The preferred size for the MultiLineToolTip is the width of
   * the longest row in the tip, and the height of a
   * single row times the number of rows in the tip.
   * @return the preferred size
   */
  @Override
  public Dimension getPreferredSize(JComponent c) {
    if (c.getClientProperty(BasicHTML.propertyKey) != null) {
      return super.getPreferredSize(c);
    }
    else  {
      // Determine the size for each row.
      Font font = c.getFont();
      FontMetrics fontMetrics = c.getFontMetrics(font);
      int fontHeight = fontMetrics.getHeight();

      // Get the tip text string.
      String tipText = ((JToolTip)c).getTipText();

      // Empty tip, use a default size.
      if(tipText == null) {
        return new Dimension(2 * MARGIN, 2 * MARGIN);
      }

      // Create a StringTokenizer to parse the ToolTip.
      StringTokenizer tokenizer = new StringTokenizer(tipText, "\n");
      int numberOfLines = tokenizer.countTokens();

      // Height is number of lines times height of a single line.
      int height = numberOfLines * fontHeight;

      // Width is width of longest single line.
      int width = 0;
      for( int i = 0; i < numberOfLines; i++ ) {
        int thisWidth = fontMetrics.stringWidth(tokenizer.nextToken());
        width = Math.max(width, thisWidth);
      }

      // Add the margin to the size, and return.
      return new Dimension(width + 2 * MARGIN, height + 2 * MARGIN);
    }
  }

}
