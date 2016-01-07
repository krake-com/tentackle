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

import javax.swing.JLabel;



/**
 * Cell Editor for FormFieldComponents.<br>
 *
 * Extends the FormComponentCellEditor for table cell editors based
 * on FormFields.
 * <p>
 * Notice: Currently, tree cell editors are not enhanced any further.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormFieldComponentCellEditor extends FormComponentCellEditor {

  private String defaultFormat;               // default format for FormField
  private int    defaultHorizontalAlignment;  // default horizontal alignment
  private int    defaultVerticalAlignment;    // default vertical alignment


  /**
   * Creates a celleditor from a FormFieldComponent.
   *
   * @param editorComponent the editor formfield component
   */
  public FormFieldComponentCellEditor(FormFieldComponent editorComponent) {
    setEditorComponent(editorComponent);
  }

  /**
   * Creates a cell-editor with a default editing component.
   *
   * @see StringFormField
   */
  public FormFieldComponentCellEditor() {
    this(new StringFormField());
  }


  /**
   * Sets the editor FormFieldComponent.
   *
   * @param editor the editor component
   */
  public void setEditorComponent (FormFieldComponent editor) {
    super.setEditorComponent(editor);
    defaultFormat = editor.getFormat();
    defaultHorizontalAlignment = editor.getHorizontalAlignment();
    defaultVerticalAlignment   = JLabel.CENTER;
    if (editor instanceof FormTextArea) {
      // using a FormTextArea as an editor in nearly all cases implies the following settings
      setAutoRowHeight(true);
      ((FormTextArea)editor).setLineWrap(true);
      ((FormTextArea)editor).setWrapStyleWord(true);
    }
  }


  /**
   * Gets the current editor component.
   *
   * @return the editor component
   */
  @Override
  public FormFieldComponent getEditorComponent()  {
    return (FormFieldComponent)editor;
  }



  @Override
  protected void prepareTableCellEditorComponent(FormTable table,
                                                 boolean selected,
                                                 int row, int column) {

      FormFieldComponent editorComponent = getEditorComponent();

      // set format
      if (table != null)  {
        int modelColumn = table.convertColumnIndexToModel(column);
        String format = table.getFormat(modelColumn);
        if (format != null) {
          // set column-specific format
          editorComponent.setFormat(format);
        }
        else  {
          // set default format according to editor-component
          editorComponent.setFormat(defaultFormat);
        }

        // set horizontal alignment
        int alignment = table.getHorizontalAlignment(modelColumn);
        if (alignment != -1)  {
          editorComponent.setHorizontalAlignment(alignment);
        }
        else  {
          editorComponent.setHorizontalAlignment(defaultHorizontalAlignment);
        }

        alignment = table.getVerticalAlignment(modelColumn);
        if (alignment != -1)  {
          editorComponent.setVerticalAlignment(alignment);
        }
        else  {
          editorComponent.setVerticalAlignment(defaultVerticalAlignment);
        }

        Integer maxColumns = table.getMaxColumn(modelColumn);
        if (maxColumns != null) {
          editorComponent.setMaxColumns(maxColumns);
        }
        
        Integer scale = table.getScale(modelColumn);
        if (scale != null && editorComponent instanceof AbstractFractionNumberFormField) {
          ((AbstractFractionNumberFormField) editorComponent).setScale(scale);
        }

        // set adjust
        editorComponent.setAdjust(table.getAdjust(modelColumn));
        // set autoselect
        editorComponent.setAutoSelect(table.isAutoSelect(modelColumn));
        // set character conversion
        editorComponent.setConvert(table.getConvert(modelColumn));
        // set zero supression
        if (editorComponent instanceof AbstractNumberFormField) {
          ((AbstractNumberFormField)editorComponent).setBlankZero(table.isBlankZero(modelColumn));
        }
        // set erase-first according to autoselect
        if (editorComponent.isAutoSelect())  {
          // the first key typed should clear field instead of appending
          editorComponent.setEraseFirst(true);
        }
        else  {
          editorComponent.setEraseFirst(false);
        }
      }

      editorComponent.setCaretPosition(0);

      // run the default prepare
      super.prepareTableCellEditorComponent(table, selected, row, column);
  }


}