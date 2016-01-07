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


package org.tentackle.swing.print;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JTextArea;



/**
 * TextArea for printing in PrintPanels
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class PrintTextArea extends JTextArea implements PrintComponent {

  private Object value;         // the printed object
  private boolean autoHeight;   // automatically change the height after setPrintValue
  private boolean autoWidth;    // automatically change the width after setPrintValue
  
  
  /**
   * Creates a text area.
   * 
   * @param text the text
   * @param rows the number of rows, 0 = derive from text
   * @param columns the number of columns, 0 = derive from text
   */
  public PrintTextArea(String text, int rows, int columns)  {
    super(rows, columns);
    // same as default font, but not bold
    Font font = getFont();
    setFont(new Font(font.getName(), Font.PLAIN, font.getSize()));
    // color is black
    setForeground(Color.black);
    setBackground(Color.white);   // usually not used cause of opaque = false
    // set the value
    setPrintValue(text);
    // make it transparent by default
    setOpaque(false);
    // clear Border (default border shifts the printing result southeast)
    setBorder(null);
  }
  
  /**
   * Creates a text area.
   * 
   * @param text the text
   */
  public PrintTextArea(String text)  {
    this(text, 0, 0);
  }

  /**
   * Creates a text area.
   */
  public PrintTextArea() {
    this(null);
  }
  
  
  /**
   * Determines whether the textarea's height should be adjusted
   * after {@link #setPrintValue}.
   * @param autoHeight true if adjust, false if fixed
   * @see PrintPanel#getDynamicSize()
   */
  public void setAutoHeight(boolean autoHeight) {
    this.autoHeight = autoHeight;
  }
  
  /**
   * Gets the autoHeight flag.
   * @return true if autoHeight enabled
   */
  public boolean isAutoHeight() {
    return autoHeight;
  }
  
  
  /**
   * Determines whether the textarea's width should be adjusted
   * after {@link #setPrintValue}.
   * @param autoWidth true if adjust, false if fixed
   * @see PrintPanel#getDynamicSize()
   */
  public void setAutoWidth(boolean autoWidth) {
    this.autoWidth = autoWidth;
  }
  
  /**
   * Gets the autoWidth flag.
   * @return true if autoWidth enabled
   */
  public boolean isAutoWidth() {
    return autoWidth;
  }
  

  
  @Override
  public Object getPrintValue() {
    return value;
  }
  
  /**
   * {@inheritDoc}
   * <p>
   * If the autoHeight or autoWidth feature is enabled the
   * size of the textarea will be adjusted according to
   * the value. The new size of the panel can be retrieved
   * by {@link PrintPanel#getDynamicSize()} and set in 
   * the report, for example {@link Report#setLineSize(java.awt.Dimension)}.
   * 
   * @param value
   * @see PrintPanel#getPreferredSize()
   */
  @Override
  public void setPrintValue(Object value) {
    this.value = value;
    setText(value == null ? null : value.toString());
    if (autoHeight && autoWidth) {
      setSize(getPreferredSize());
    }
    else if (autoHeight) {
      setSize(getWidth(), getPreferredSize().height);
    }
    else if (autoWidth) {
      setSize(getPreferredSize().width, getHeight());
    }
  }
  
}