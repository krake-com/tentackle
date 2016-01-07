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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import javax.swing.JLabel;


/**
 * Text printing bean.<br>
 * This is the base class for all text-field based printing beans.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class PrintLabel extends JLabel implements PrintComponent {

  /** last printed value **/
  protected Object value;

  private int preferredHeight;  // 0 = automatic
  private int preferredWidth;   // 0 = automatic
  private int columnWidth;      // != 0 if width computed
  private int columns;          // fixed columns-width, 0 = dynamic



  /**
   * Creates a print label.
   *
   * @param text the initial text, null = none
   * @param columns the width in columns, 0 = minimum width
   * @param horizontalAlignment the horizontal alignment
   * @see JLabel
   */
  public PrintLabel(String text, int columns, int horizontalAlignment)  {
    super(text, null, horizontalAlignment);
    setColumns(columns);
    // same as default font, but not bold
    Font font = getFont();
    setFont(new Font(font.getName(), Font.PLAIN, font.getSize()));
    // color is black
    setForeground(Color.black);
    // Labels are always opaque = false by default
    setBackground(Color.white);   // in case opaque is turned on
  }

  /**
   * Creates a print label.
   *
   * @param columns the width in columns, 0 = minimum width
   * @param horizontalAlignment the horizontal alignment
   * @see JLabel
   */
  public PrintLabel(int columns, int horizontalAlignment)  {
    this("", columns, horizontalAlignment);
    // notice: the empty string is important! Otherwise GUI editors
    // like netbeans won't setText("printLabelXX") which we need
    // because printLabels have no border to identify them in design mode.
  }

  /**
   * Creates a print label with a LEADING horizontal alignment.
   *
   * @param columns the width in columns, 0 = minimum width
   * @see JLabel
   */
  public PrintLabel(int columns)  {
    this(0, LEADING);
  }


  /**
   * Creates a print label with minimum width and a LEADING horizontal alignment.
   *
   * @see JLabel
   */
  public PrintLabel()  {
    this(0);
  }


  /**
   * Sets the width according to the number of columns.
   *
   * @param columns the columns, 0 = minimum width (default)
   * @throws IllegalArgumentException if width is negative
   */
  public void setColumns(int columns) {
    if (columns != this.columns)  {
      if (columns < 0)  {
        throw new IllegalArgumentException("columns less than zero.");
      }
      this.columns = columns;
      invalidate();
    }
  }

  /**
   * Gets the column width.
   *
   * @return the width according to the number of columns
   */
  public int getColumns() {
    return columns;
  }


  /**
   * Gets the column width.
   * <p>
   * The meaning of what a column is can be considered a fairly weak
   * notion for some fonts.  This method is used to define the width
   * of a column.  By default this is defined to be the width of the
   * character <em>m</em> for the font used.  This method can be
   * redefined to be some alternative amount
   *
   * @return the column width >= 1
   */
  protected int getColumnWidth() {
    if (columnWidth == 0) {
      FontMetrics metrics = getFontMetrics(getFont());
      columnWidth = metrics.charWidth('m');
    }
    return columnWidth;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to clear columnWidth.
   */
  @Override
  public void setFont(Font font) {
    super.setFont(font);
    columnWidth = 0;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to allow columns to be honored and the preferred fixed width/height
   * Returns the preferred size Dimensions needed for this
   * TextField.  If a non-zero number of columns has been
   * set, the width is set to the columns multiplied by
   * the column width.
   */
  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();

    if (preferredHeight != 0) {
      size.height = preferredHeight;
    }

    if (preferredWidth != 0)  {
      size.width = preferredWidth;
    }
    else  {
      if (columns != 0) {
        size.width = columns * getColumnWidth();
      }
    }

    return size;
  }


  /**
   * Sets the height to control Labelsize without automatic FontMetrics.
   * Use this method to control *ONLY* the height.
   *
   * @param height the preferred height, 0 = automatic according to font
   */
  public void setPreferredHeight(int height) {
    this.preferredHeight = height;
  }

  /**
   * Gets the preferred height.
   *
   * @return the preferred height, 0 = not yet computed
   */
  public int getPreferredHeight() {
    return preferredHeight;
  }


  /**
   * Sets the width to control Labelsize without automatic FontMetrics.
   * Use this method to control *ONLY* the width.
   *
   * @param width the preferred width, 0 = automatic according to font
   */
  public void setPreferredWidth(int width) {
    this.preferredWidth = width;
  }

  /**
   * Gets the preferred width.
   *
   * @return the preferred width, 0 = not yet computed
   */
  public int getPreferredWidth() {
    return preferredWidth;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to allow columns to be honored.
   */
  @Override
  public void setBounds(int x, int y, int width, int height) {
    if (columns != 0) {
      Dimension dim = getPreferredSize();
      width = dim.width;
      height = dim.height;
    }
    super.setBounds (x, y, width, height);
  }

  @Override
  public void setBounds(Rectangle r)  {
    if (columns != 0) {
      Dimension dim = getPreferredSize();
      r = new Rectangle(r.x, r.y, dim.width, dim.height);
    }
    super.setBounds(r);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden due to setBounds().
   */
  @Override
  public void setSize(Dimension d) {
    if (columns != 0) {
      d = getPreferredSize();
    }
    super.setSize(d);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden due to setBounds().
   */
  @Override
  public void setSize(int width, int height)  {
    if (columns != 0) {
      Dimension d = getPreferredSize();
      width = d.width;
      height = d.height;
    }
    super.setSize(width, height);
  }



  // ----------------------- implements PrintComponent --------------------------

  @Override
  public void setPrintValue(Object value) {
    this.value = value;
    setText(value == null ? null : value.toString());
  }


  @Override
  public Object getPrintValue() {
    return value;
  }

}