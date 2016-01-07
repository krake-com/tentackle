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


package org.tentackle.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.Serializable;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.Date;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellRenderer;
import org.tentackle.common.BMoney;
import org.tentackle.misc.FormatHelper;


/**
 * Default cell renderer in FormTables for text fields.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormTableCellRenderer implements TableCellRenderer, Serializable {

  private static final Border DEFAULT_NO_FOCUS_BORDER = new EmptyBorder(1, 1, 1, 1);
  private static final Border DEFAULT_FOCUS_BORDER = new LineBorder(Color.GRAY, 1);


  // the rendering component
  private JComponent component;

  // the borders
  private Border focusBorder;
  private Border noFocusBorder;

  // the display font
  private Font  renderingFont;

  // colors (will be retrieved from formtable)
  private Color selectedForeground;
  private Color selectedBackground;
  private Color unselectedForeground;
  private Color unselectedBackground;
  private Color focusedForeground;
  private Color focusedBackground;
  private Color selectedMandatoryBackground;
  private Color unselectedMandatoryBackground;




  /**
   * Creates a table cell renderer.
   * @param component the component
   */
  public FormTableCellRenderer(JComponent component) {
    if (component == null) {
      throw new NullPointerException("component must not be null");
    }
    this.component = component;

    focusBorder = UIManager.getBorder("Table.focusCellHighlightBorder");
    if (focusBorder == null) {
      focusBorder = DEFAULT_FOCUS_BORDER;
    }
    noFocusBorder = UIManager.getBorder("Table.cellNoFocusBorder");
    if (noFocusBorder == null) {
      noFocusBorder = DEFAULT_NO_FOCUS_BORDER;
    }

    component.setOpaque(true);
    component.setName("Table.cellRenderer");
  }


  /**
   * Creates a default table cell renderer.
   */
  public FormTableCellRenderer() {
    this(new JLabel() {
      /**
       * see DefaultTableCellRenderer
       */
      @Override
      public void updateUI() {
        super.updateUI();
        setForeground(null);
        setBackground(null);
      }
    });
  }



  /**
   * Gets the rendering component.
   *
   * @return the rendering component
   */
  public JComponent getComponent() {
    return component;
  }



  /**
   * Gets the selected mandatory background.
   *
   * @return mandatory background if not default from table
   */
  public Color getSelectedMandatoryBackground() {
    return selectedMandatoryBackground;
  }

  /**
   * Sets the selected mandatory background.
   *
   * @param selectedMandatoryBackground mandatory background if not default from table
   */
  public void setSelectedMandatoryBackground(Color selectedMandatoryBackground) {
    this.selectedMandatoryBackground = selectedMandatoryBackground;
  }

  /**
   * Gets the unselected mandatory background.
   *
   * @return mandatory background if not default from table
   */
  public Color getUnselectedMandatoryBackground() {
    return unselectedMandatoryBackground;
  }

  /**
   * Sets the unselected mandatory background.
   *
   * @param unselectedMandatoryBackground mandatory background if not default from table
   */
  public void setUnselectedMandatoryBackground(Color unselectedMandatoryBackground) {
    this.unselectedMandatoryBackground = unselectedMandatoryBackground;
  }

  /**
   * Sets the color for the selected foreground.
   * @param c the foreground color
   */
  public void setSelectedForeground(Color c) {
    selectedForeground = c;
  }

  /**
   * Gets the color for the selected foreground.
   * @return the foreground color
   */
  public Color getSelectedForeground()  {
    return selectedForeground;
  }

  /**
   * Sets the color for the selected background.
   * @param c the background color
   */
  public void setSelectedBackground(Color c) {
    selectedBackground = c;
  }

  /**
   * Gets the color for the selected background.
   * @return the background color
   */
  public Color getSelectedBackground()  {
    return selectedBackground;
  }


  /**
   * Sets the color for the unselected foreground.
   * @param c the unselected foreground color
   */
  public void setUnselectedForeground(Color c) {
    unselectedForeground = c;
  }

  /**
   * Gets the color for the unselected foreground.
   * @return the unselected foreground color
   */
  public Color getUnselectedForeground()  {
    return unselectedForeground;
  }

  /**
   * Sets the color for the unselected background.
   * @param c the unselected background color
   */
  public void setUnselectedBackground(Color c) {
    unselectedBackground = c;
  }

  /**
   * Gets the color for the unselected background.
   * @return the unselected background color
   */
  public Color getUnselectedBackground()  {
    return unselectedBackground;
  }


  /**
   * Sets the color for the focused foreground.
   * @param c the focused foreground color
   */
  public void setFocusedForeground(Color c) {
    focusedForeground = c;
  }

  /**
   * Gets the color for the focused foreground.
   * @return the focused foreground color
   */
  public Color getFocusedForeground()  {
    return focusedForeground;
  }

  /**
   * Sets the color for the focused background.
   * @param c the focused background color
   */
  public void setFocusedBackground(Color c) {
    focusedBackground = c;
  }

  /**
   * Gets the color for the focused background.
   * @return the focused background color
   */
  public Color getFocusedBackground()  {
    return focusedBackground;
  }


  /**
   * Sets the rendering default font.
   * @param font the font
   */
  public void setRenderingFont(Font font)  {
    this.renderingFont = font;
  }

  /**
   * Gets the rendering default font.
   * @return the font
   */
  public Font getRenderingFont() {
    return renderingFont;
  }


  /**
   * Gets the focus border
   *
   * @return the focusBorder
   */
  public Border getFocusBorder() {
    return focusBorder;
  }

  /**
   * Sets the focus border.
   *
   * @param focusBorder the focusBorder to set
   */
  public void setFocusBorder(Border focusBorder) {
    this.focusBorder = focusBorder;
  }


  /**
   * Gets the no focus border
   *
   * @return the focusBorder
   */
  public Border getNoFocusBorder() {
    return noFocusBorder;
  }

  /**
   * Sets the no focus border.
   *
   * @param noFocusBorder the focusBorder to set
   */
  public void setNoFocusBorder(Border noFocusBorder) {
    this.noFocusBorder = noFocusBorder;
  }



  /**
   * Sets the value of the rendering component.
   *
   * @param value the value
   */
  protected void setValue(Object value) {
    ((JLabel) component).setText(value == null ? "" : value.toString());
  }



  /**
   * Gets the text content of the rendering component.
   *
   * @return the text
   */
  protected String getText() {
    return ((JLabel) component).getText();
  }



  protected void setHorizontalAlignment(int alignment) {
    ((JLabel) component).setHorizontalAlignment(alignment);
  }

  protected void setVerticalAlignment(int alignment) {
    ((JLabel) component).setVerticalAlignment(alignment);
  }



  /**
   * {@inheritDoc}
   * <p>
   * Ovewritten due to formatting.
   * @return the component
   */
  @Override
  public Component getTableCellRendererComponent(JTable table,
          Object value, boolean isSelected, boolean hasFocus,
          int row, int column) {

    FormTable formTable = null;

    if (table instanceof FormTable) {
      formTable = (FormTable) table;
    }

    int modelColumn = table.convertColumnIndexToModel(column);

    // determine mandatory state
    boolean mandatory = ((FormTable) table).isCellMandatory(row, column);
    boolean editable = hasFocus && table.isCellEditable(row, column);

    component.setBackground(getBackgroundColor(formTable, isSelected, hasFocus, editable, mandatory));
    component.setForeground(getForegroundColor(formTable, isSelected, hasFocus, editable, mandatory));

    component.setFont(renderingFont == null ? table.getFont() : renderingFont);

    if (hasFocus) {
      component.setBorder(getFocusBorder());
    }
    else {
      component.setBorder(getNoFocusBorder());
    }

    // set the default value
    setValue(value);

    int hAlign = JLabel.LEFT;
    int vAlign = JLabel.CENTER;

    try {

      if (formTable != null) {
        if (value instanceof Number) {

          hAlign = SwingConstants.TRAILING;

          if (formTable.isBlankZero(modelColumn)) {
            boolean blankZero = false;
            if (value instanceof Double) {
              if (((Double) value) == 0.0d) {
                blankZero = true;
              }
            }
            else if (value instanceof Float) {
              if (((Float) value) == 0.0f) {
                blankZero = true;
              }
            }
            else if (value instanceof BMoney) {
              if (((BMoney) value).isZero()) {
                blankZero = true;
              }
            }
            else if (value instanceof Number) {
              if (((Number) value).longValue() == 0l) {
                blankZero = true;
              }
            }

            if (blankZero) {
              setValue(null);
              return component;    // do the rest of formatting in the finally clause
            }
          }

          if (value instanceof BMoney) {
            String fmt = formTable.getFormat(modelColumn);
            if (fmt == null) {
              formTable.setFormat(modelColumn, FormatHelper.getMoneyPattern());
            }
            DecimalFormat format = (DecimalFormat) formTable.getNumberFormat(modelColumn);
            FormatHelper.setScale(format, ((BMoney) value).scale());
            setValue(format.format(value));
            if (fmt == null) {
              formTable.setFormat(modelColumn, format.toPattern());
            }
          }
          else {
            if (value instanceof Float || value instanceof Double) {
              String fmt = formTable.getFormat(modelColumn);
              if (fmt == null) {
                formTable.setFormat(modelColumn, FormatHelper.getFloatingNumberPattern());
              }
            }
            setValue(value == null ? "" : formTable.getNumberFormat(modelColumn).format((Number) value));
          }
        }
        else {
          // not a number
          if (value instanceof Timestamp) {
            hAlign = JLabel.CENTER;
            setValue(value == null ? "" : formTable.getDateFormat(modelColumn, true).format((Date) value));
          }
          else if (value instanceof Date) {
            hAlign = JLabel.CENTER;
            setValue(value == null ? "" : formTable.getDateFormat(modelColumn).format((Date) value));
          }
        }
      }
    }

    finally {

      if (formTable != null) {
        // do some other formatting stuff
        switch (formTable.getConvert(modelColumn)) {
          case AbstractFormField.CONVERT_LC:
            setValue(getText().toLowerCase());
            break;
          case AbstractFormField.CONVERT_UC:
            setValue(getText().toUpperCase());
            break;
        }
        int alignment = formTable.getHorizontalAlignment(modelColumn);
        if (alignment != -1) {
          hAlign = alignment;
        }
        alignment = formTable.getVerticalAlignment(modelColumn);
        if (alignment != -1) {
          vAlign = alignment;
        }
      }

      // set alignments
      setHorizontalAlignment(hAlign);
      setVerticalAlignment(vAlign);
    }

    return component;
  }



  /**
   * Determines the foreground color.
   *
   * @param table the table
   * @param selected true if cell is selected
   * @param hasFocus true if cell has focus
   * @param editable true if cell is editable
   * @param mandatory true if cell is mandatory
   * @return the color
   */
  protected Color getForegroundColor(JTable table, boolean selected, boolean hasFocus, boolean editable, boolean mandatory) {

    Color color;

    if (table instanceof FormTable) {
      if (selected) {
        color = selectedForeground == null ? ((FormTable) table).getSelectedForeground() : selectedForeground;
      }
      else {
        color = unselectedForeground == null ? ((FormTable) table).getUnselectedForeground() : unselectedForeground;
      }

      if (hasFocus && editable) {
        color = focusedForeground == null ? ((FormTable) table).getFocusedForeground() : focusedForeground;
      }
    }
    else {
      if (selected) {
        color = selectedForeground != null ? selectedForeground : table.getSelectionForeground();
      }
      else {
        color = unselectedForeground != null ? unselectedForeground : table.getForeground();
      }

      if (hasFocus && editable) {
        color = focusedForeground == null ? UIManager.getColor("Table.focusCellForeground") : focusedForeground;
      }
    }
    return color;
  }


  /**
   * Determines the background color.
   *
   * @param table the table
   * @param selected true if cell is selected
   * @param hasFocus true if cell has focus
   * @param editable true if cell is editable
   * @param mandatory true if cell is mandatory
   * @return the color
   */
  protected Color getBackgroundColor(JTable table, boolean selected, boolean hasFocus, boolean editable, boolean mandatory) {

    Color color;

    if (table instanceof FormTable) {
      if (selected) {
        color = selectedBackground == null ? ((FormTable) table).getSelectedBackground() : selectedBackground;
      }
      else {
        color = unselectedBackground == null ? ((FormTable) table).getUnselectedBackground() : unselectedBackground;
      }

      if (hasFocus && editable) {
        color = focusedBackground == null ? ((FormTable) table).getFocusedBackground() : focusedBackground;
      }

      if (mandatory) {
        if (selected) {
          color = selectedMandatoryBackground == null ? ((FormTable) table).getSelectedMandatoryBackground() : selectedMandatoryBackground;
        }
        else {
          color = unselectedMandatoryBackground == null ? ((FormTable) table).getUnselectedMandatoryBackground() : unselectedMandatoryBackground;
        }
      }
    }
    else  {
      if (selected) {
        color = selectedBackground != null ? selectedBackground : table.getSelectionBackground();
      }
      else {
        color = unselectedBackground != null ? unselectedBackground : table.getBackground();
      }

      if (hasFocus && editable) {
        color = focusedBackground == null ? UIManager.getColor("Table.focusCellBackground") : focusedBackground;
      }

      // no mandatory support for non-FormTables
    }

    return color;
  }




}