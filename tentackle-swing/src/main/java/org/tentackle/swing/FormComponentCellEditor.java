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

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableCellEditor;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeCellEditor;
import org.tentackle.swing.plaf.PlafUtilities;


/**
 * Cell editor for FormComponents.<br>
 *
 * Provides cell editors for tables and trees.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormComponentCellEditor extends AbstractCellEditor implements TableCellEditor, TreeCellEditor {

  /** last table referenced **/
  protected FormTable<?>    table;
  /** last tree referenced **/
  protected FormTree        tree;
  /** current row (table and tree) **/
  protected int             row;
  /** current column (table only) **/
  protected int             column;
  /** true if cell is selected (table only) **/
  protected boolean         selected;
  /** true if cell is expanded (tree only) **/
  protected boolean         expanded;
  /** true if cell is a leaf node (tree only) **/
  protected boolean         leaf;
  /** the editor component **/
  protected FormComponent   editor;
  /** number of clicks to start editing (default is 2) **/
  protected int             clickCountToStart = 2;


  private boolean autoRowHeight;    // flag to adjust the rowHeight of the JTable automatically
  private boolean startOver;        // flag to force the editor remain editing in field
  private boolean editingCanceled;  // flag set to true if editing was canceled


  /**
   * Creates a cell-editor from a FormComponent.
   *
   * @param editorComponent the editor form component
   */
  public FormComponentCellEditor(FormComponent editorComponent) {
    setEditorComponent(editorComponent);
  }

  /**
   * Creates a cell-editor with a default editing component.
   *
   * @see StringFormField
   */
  public FormComponentCellEditor() {
    this(new StringFormField());
  }




  /**
   * Sets the editor Component.
   *
   * @param editor the editor component
   */
  public void setEditorComponent (FormComponent editor) {

    this.editor = editor;

    // signal component that it's a cell editor.
    // usually this will change the behaviour in comboboxes and formfields
    // when to perform focus lost processing
    editor.setCellEditorUsage(true);

    if (editor instanceof FormTextArea)  {
      // disable smart enter
      ((FormTextArea)editor).setSmartEnter(false);
    }

    editor.addValueListener(new ValueListener()  {
      @Override
      public void valueChanged (ValueEvent e)  {
      }
      @Override
      public void valueEntered (ValueEvent e)  {
        stopCellEditing();
      }
    });
    registerTextDocumentListener();   // register if editor is a JTextComponent
  }

  /**
   * Gets the current editor component.
   *
   * @return the editor component
   */
  public FormComponent getEditorComponent()  {
    return editor;
  }


  /**
   * Turns automatic adjustment of the row height on/off.
   *
   * @param autoRowHeight true to enable automatic row height, false if fixed
   */
  public void setAutoRowHeight(boolean autoRowHeight)  {
    this.autoRowHeight = autoRowHeight;
    registerTextDocumentListener();   // register if editor is a JTextComponent
  }

  /**
   * Returns whether auto row height is enabled.
   *
   * @return true if automatic row height is active
   */
  public boolean isAutoRowHeight() {
    return autoRowHeight;
  }


  /**
   * Sets a flag that will inhibit stopCellEditing() once, i.e.
   * force the editing mode staying in the current field.
   * Useful after error-messages.
   * The flag will always be cleared during the next stopCellEditing().
   */
  public void startOver()  {
    startOver = true;
  }

  /**
   * clears the startOver flag
   */
  public void clearStartOver() {
    startOver = false;
  }


  /**
   * Inhibits enter-key traversal once.<br>
   * Useful if we should stay in selected field even after Enter-key has been pressed.
   * For ex. to disable wrapping to the next line if we are at end of line.
   */
  public void inhibitCellTraversal() {
    if (table != null) {
      table.inhibitCellTraversal();
    }
  }

  /**
   * Clears inhibit if for some reason set erroneously
   */
  public void clearInhibitCellTraversal() {
    if (table != null) {
      table.clearInhibitCellTraversal();
    }
  }


  /**
   * Determines whether editing was stopped due to canceling.
   *
   * @return true if editing was cancelled
   */
  public boolean wasEditingCanceled() {
    // this fixes the lack of implementation of "cancelCellEditing" in JTable.
    // isEditing() will be false if the editing was cancelled as of JDK 1.4.
    // cancelCellEditing will never be called
    return editingCanceled || (table != null && !table.isEditing());
  }



  /**
   * Requests the focus of the editing component.
   *
   * @return true if request will succeed, false if failed
   */
  public boolean requestFocusInWindow() {
    return editor instanceof Component && ((Component) editor).requestFocusInWindow();
  }


  /**
   * Requests the focus of the editing component as late as possible.
   */
  public void requestFocusLater()  {
    editor.requestFocusLater();
  }



  /**
   * Sets the number of clicks to start editing.
   * Default ist 2. (even for CheckBoxes!)
   * @param clicks the number of clicks to start editing
   */
  public void setClickCountToStart(int clicks)  {
    this.clickCountToStart = clicks;
  }


  /**
   * Gets the number of clicks to start editing.
   *
   * @return the number of clicks to start editing
   */
  public int getClickCountToStart()  {
    return clickCountToStart;
  }


  /**
   * Gets the table.
   *
   * @return the form table
   */
  public FormTable<?> getFormTable() {
    return table;
  }





  @Override
  public boolean isCellEditable(EventObject anEvent) {
    if (anEvent instanceof MouseEvent) {
        return ((MouseEvent)anEvent).getClickCount() >= clickCountToStart;
    }
    return super.isCellEditable(anEvent);   // currently returns true
  }


  @Override
  public boolean shouldSelectCell(EventObject anEvent) {
    if (editor instanceof FormComboBox) {
      if (anEvent instanceof MouseEvent) {
          MouseEvent e = (MouseEvent)anEvent;
          return e.getID() != MouseEvent.MOUSE_DRAGGED;
      }
    }
    return super.shouldSelectCell(anEvent); // currently returns always true
  }


  @Override
  public boolean stopCellEditing() {

    if (startOver)  {   // stay in current field!
      // don't stop editing once
      startOver = false;
      if (editor instanceof JComponent) {
        // invokelater necessary since 1.4.2 for whatever reason, otherwise
        // any FormInfo/Error-popup will arise twice
        EventQueue.invokeLater(() ->
          // stay in field
          ((JComponent)editor).requestFocusInWindow()
        );
      }
      return false;
    }

    return super.stopCellEditing();   // perform fireEditingStopped()
  }


  @Override
  public void cancelCellEditing() {
    // will obviously never be called (see fix in stopCellEditing())
    editingCanceled = true;
    startOver = false;
    super.cancelCellEditing();        // perform fireEditingCanceled
  }





  /**
   * In some cases it is necessary to update celleditors before
   * invocations of getTableCellEditorComponent(), especially if FormTableEntry.isCellEditorFixed()==true,
   * which is the default.<p>
   *
   * FormTable will invoke prepare() *before* getTableCellEditorComponent() for
   * every FormComponentCellEditor.
   * The default implementation does nothing.
   * Usually, the method will be overridden in a FormTableEntry like this:
   * <pre>
   *   public TableCellEditor getCellEditor(int col) {
   *     return new MySpecialPdoCellEditor() {
   *       public void prepare(FormTableEntry entry, int column) {
   *         setContextDb(contextDb);
   *       }
   *     }
   *   }
   * </pre>
   *
   * @param entry is the FormTableEntry the editor will edit some data of
   * @param column is the column index (table-column)
   */
  public void prepare(FormTableEntry<?> entry, int column) {
    //
  }



  @Override
  public Object getCellEditorValue() {
    return editor.getFormValue();
  }



  /**
   * Returns the editor initialized for the table cell.<br>
   * Does _not_ set the value.
   * Useful for editors that can't use setFormValue())

   * @param table the formtable
   * @param selected true if cell is selected
   * @param row the table row
   * @param column the table column
   * @return the editor component
   */
  public FormComponent getTableCellEditorComponent(FormTable<?> table,
                                                   boolean selected,
                                                   int row, int column) {

    prepareTableCellEditorComponent(table, selected, row, column);
    // return the component
    return editor;
  }


  /**
   * {@inheritDoc}
   * <p>
   * @throws ClassCastException if table is not a FormTable
   */
  @Override
  public Component getTableCellEditorComponent(JTable table,
                                               Object value,
                                               boolean isSelected,
                                               int row, int column) {
      // set value according to format
      editor.setFormValue(value);
      editor.saveValue();
      return (Component) getTableCellEditorComponent((FormTable)table, isSelected, row, column);
  }



  /**
   * Returns the editor initialized for the tree cell.<br>
   * Does _not_ set the value.
   * Useful for editors that can't use setFormValue())

   * @param tree the formtree
   * @param selected true if cell is selected
   * @param expanded if tree node is expanded
   * @param leaf is node is a leaf node
   * @param row the row index of the node being edited
   * @return the editor component
   */
  public FormComponent getTreeCellEditorComponent(FormTree tree,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf, int row) {

    prepareTreeCellEditorComponent(tree, selected, expanded, leaf, row);
    // return the component
    return editor;
  }

  /**
   * {@inheritDoc}
   * <p>
   * @throws ClassCastException if tree is not a FormTree
   */
  @Override
  public Component getTreeCellEditorComponent(JTree tree,
                                              Object value,
                                              boolean isSelected,
                                              boolean expanded,
                                              boolean leaf, int row) {
    // set the value
    editor.setFormValue(value);
    editor.saveValue();
    return (Component)getTreeCellEditorComponent((FormTree)tree, isSelected, expanded, leaf, row);
  }






  /**
   * Prepares the table cell editor.
   *
   * @param table the table
   * @param selected true if cell is selected
   * @param row the table row
   * @param column the table column
   */
  protected void prepareTableCellEditorComponent(FormTable<?> table,
                                                 boolean selected,
                                                 int row,
                                                 int column) {
      // save for later reference
      this.table            = table;
      this.row              = row;
      this.column           = column;
      this.selected         = selected;
      this.startOver        = false;
      this.editingCanceled  = false;

      // update height if not a text component (see FormFieldComponentCellEditor)
      if (autoRowHeight && !(editor instanceof JTextComponent)) {
        updateHeight();
      }

      if (editor instanceof JComponent) {
        // set a not too noisy border color
        ((JComponent)editor).setBorder(BorderFactory.createLineBorder(PlafUtilities.getInstance().getTableEditCellBorderColor()));
      }

      // check mandatory attribute if cell is bound
      editor.setMandatory(table.isCellMandatory(row, column));

  }




  /**
   * Prepares the tree cell editor.
   *
   * @param tree the tree
   * @param selected true if cell is selected
   * @param expanded if tree node is expanded
   * @param leaf is node is a leaf node
   * @param row the row index of the node being edited
   */
  protected void prepareTreeCellEditorComponent(FormTree tree,
                                              boolean selected,
                                              boolean expanded,
                                              boolean leaf,
                                              int row) {
      // save for later reference
      this.tree             = tree;
      this.row              = row;
      this.selected         = selected;
      this.expanded         = expanded;
      this.leaf             = leaf;
      this.startOver        = false;
      this.editingCanceled  = false;
  }





  /**
   * updates the height of the table row to the preferred height
   * of a JComponent (usually a FormTextArea)
   */
  private void updateHeight() {
    if (table != null &&
        ((editor instanceof JTextComponent && editor.hasFocus()) ||
         !(editor instanceof JTextComponent)))  {
      // must be invoked after component got its size computed correctly
      EventQueue.invokeLater(() -> {
        int preferredHeight = ((JComponent) editor).getPreferredSize().height;
        int rowHeight = table.getRowHeight(row);
        if (preferredHeight != rowHeight)  {
          table.setRowHeight(row, preferredHeight);
        }
      });
    }
  }


  /**
   * registers a doc-listener if editor is a JTextComponent (usually a FormTextArea)
   * to allow automatic resizing of the row height in a JTable
   */
  private void registerTextDocumentListener() {

    if (autoRowHeight && editor instanceof JTextComponent)  {

      ((JTextComponent)editor).getDocument().addDocumentListener(new DocumentListener() {

        @Override
        public void insertUpdate(DocumentEvent e) {
          updateHeight();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
          updateHeight();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
          updateHeight();
        }

      });
    }
  }

}