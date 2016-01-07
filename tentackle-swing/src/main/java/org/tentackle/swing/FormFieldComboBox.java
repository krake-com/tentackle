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
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.Serializable;
import java.util.function.Function;
import javax.swing.JList;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxRenderer;


/**
 * A {@code FormFieldComboBox} is a {@code FormComboBox} with a
 * {@code FormFieldComponent} as its editor.
 *
 * @param <E> the type of the elements of this combo box
 * @author harald
 */
@SuppressWarnings("serial")
public class FormFieldComboBox<E> extends FormComboBox<E> implements FormFieldComponent {


  /**
   * the editor
   */
  @SuppressWarnings("serial")
  private class FormFieldComboBoxEditor extends BasicComboBoxEditor implements Serializable {

    @Override
    public Component getEditorComponent ()  {
      return editorField;
    }

    @Override
    public void setItem (Object obj)  {
      editorField.setFormValue(obj);
    }

    @Override
    public Object getItem ()  {
      return editorField.getFormValue();
    }
  }


  /**
   * the renderer
   */
  @SuppressWarnings("serial")
  private class FormFieldComboBoxRenderer extends BasicComboBoxRenderer {

    @Override
    public Component getListCellRendererComponent (JList list, Object value,
                                                   int index, boolean isSelected,
                                                   boolean cellHasFocus)  {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      // set the formatted value
      setText (editorField.doFormat(value));
      return this;
    }
  }




  private AbstractFormField editorField;                    // the editor field


  /**
   * special key listener to make use of control keys
   */
  private final KeyListener keyListener = new KeyAdapter() {
    @Override
    public void keyPressed(KeyEvent e) {
      if (isEditable()) {
        // editable combobox
        if (isPopupVisible())  {
          // process Arrow-Keys if popped up
          if (e.getModifiers() == 0)  {
            int index = getSelectedIndex();
            if (e.getKeyCode() == KeyEvent.VK_DOWN &&
                index < getItemCount() - 1)  {
              setFormValueIndex(index + 1);
            }
            else if (e.getKeyCode() == KeyEvent.VK_UP &&
                index > 0)  {
              setFormValueIndex(index - 1);
            }
            else if (e.getKeyCode() == KeyEvent.VK_ENTER ||
                     e.getKeyCode() == KeyEvent.VK_ESCAPE ||
                     e.getKeyCode() == KeyEvent.VK_F3) {
              hidePopup();
            }
            if (e.getKeyCode() != KeyEvent.VK_ENTER)  {
              // ENTER = make selection, else consume event
              e.consume();
            }
          }
        }
        else  {
          // popup not visible
          if (e.getKeyCode() == KeyEvent.VK_F3 ||
              (e.isControlDown() &&
               (e.getKeyCode() == KeyEvent.VK_UP ||
                e.getKeyCode() == KeyEvent.VK_DOWN ||
                e.getKeyCode() == KeyEvent.VK_ENTER)) ||
              // handy only in tables:
              (e.getKeyCode() == KeyEvent.VK_DOWN && isCaretRight() && isCellEditorUsage()) ||
              (e.getKeyCode() == KeyEvent.VK_UP && isCaretLeft() && isCellEditorUsage())) {
            // display the popup if Ctrl-Down/Enter/Space
            showPopup();
            e.consume();
          }
          else if (e.getKeyCode() == KeyEvent.VK_TAB && isCellEditorUsage())  {
            /**
             * TAB in tables will discard the input. This is not desired.
             * Map TAB to ENTER in such cases.
             */
            e.setKeyCode(KeyEvent.VK_ENTER);
          }
        }
      }
    }
  };  // key listener for special keys



  /**
   * special popup listsner to select first matchable item
   */
  private final PopupMenuListener popupListener = new PopupMenuListener() {

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {
    }

    /**
     * check for autoselecting first matchable item in editable
     * comboboxes when popup is shown.
     */
    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
      if (isEditable()) {
        String text = getFormValueText();
        int matchIndex = getItemIndexWithLeadString(text, getModel());
        if (matchIndex >= 0)  {
          setFormValueIndex(matchIndex);
        }
      }
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
    }
  };






  /**
   * Creates a {@code FormFielComboBox} for a given AbstractFormField.
   *
   * @param editorField the AbstractFormField used as the editor field and formatting
   *        null, if standard {@link StringFormField}
   *
   */
  public FormFieldComboBox (AbstractFormField editorField) {
    super();
    setEditorField(editorField);
    setup();
  }

  /**
   * Creates a {@code FormFielComboBox} for a default {@link StringFormField}.
   */
  public FormFieldComboBox ()  {
    this((AbstractFormField) null);
  }

  /**
   * Creates a {@code FormFielComboBox} for a given AbstractFormField.
   *
   * @param editorField the AbstractFormField used for editing  and formatting,
   *        null if standard {@link StringFormField}
   * @param items the array of items
   */
  public FormFieldComboBox (AbstractFormField editorField, E[] items) {
    super(items);
    setEditorField(editorField);
    setup();
  }

  /**
   * Creates a {@code FormFielComboBox} for a default {@link StringFormField}.
   *
   * @param items the array of items
   */
  public FormFieldComboBox (E[] items) {
    this ((AbstractFormField) null, items);
  }




  /**
   * setup editor, renderer and popuplistener
   */
  @SuppressWarnings("unchecked")
  protected void setup ()  {
    setEditor (new FormFieldComboBoxEditor());
    setRenderer(new FormFieldComboBoxRenderer());
    addPopupMenuListener(popupListener);
  }



  /**
   * Sets the editor field.
   *
   * @param editorField the AbstractFormField used for editing and formatting,
   *        null if standard {@link StringFormField}
   */
  public void setEditorField (AbstractFormField editorField)  {

    if (isEditable() && this.editorField != null)  {
      this.editorField.removeFocusListener(this);
    }

    if (this.editorField != null) {
      this.editorField.removeKeyListener(keyListener);
    }

    if (editorField == null)  {
      this.editorField = new StringFormField();
    }
    else  {
      this.editorField = editorField;
    }

    if (isEditable()) {
      this.editorField.addFocusListener(this);
    }
    // special key-handling for editable comboboxes
    this.editorField.addKeyListener(keyListener);
  }


  /**
   * Gets the editor field.
   *
   * @return the editor field
   */
  public AbstractFormField getEditorField ()  {
    return editorField;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden so that the editor field gets the focus.
   */
  @Override
  public boolean requestFocusInWindow() {
    if (isEditable()) {
      return editorField.requestFocusInWindow();
    }
    else  {
      return super.requestFocusInWindow();
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden so that the editor field gets the focus.
   */
  @Override
  public void requestFocus() {
    if (isEditable()) {
      editorField.requestFocus();
    }
    else  {
      super.requestFocus();
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to store tooltip in editorfield.
   * This allows editable comboboxes handle tooltips the same way
   * as non-editable.
   */
  @Override
  public void setToolTipText(String text) {
    editorField.setToolTipText(text);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to get the tooltip from the editorfield.
   * This allows editable comboboxes handle tooltips the same way
   * as non-editable.
   */
  @Override
  public String getToolTipText()  {
    return editorField.getToolTipText();
  }



  // ------------------------- implements FormFieldComponent ------------------

  @Override
  public boolean wasTransferFocus() {
    return isEditable() ? editorField.wasTransferFocus() : super.wasTransferFocus();
  }

  @Override
  public boolean wasTransferFocusBackward() {
    return isEditable() ? editorField.wasTransferFocusBackward() : super.wasTransferFocusBackward();
  }

  @Override
  public boolean wasFocusGainedFromTransfer()  {
    return isEditable() ? editorField.wasFocusGainedFromTransfer() : super.wasFocusGainedFromTransfer();
  }

  @Override
  public boolean wasFocusGainedFromTransferBackward()  {
    return isEditable() ? editorField.wasFocusGainedFromTransferBackward() : super.wasFocusGainedFromTransferBackward();
  }

  @Override
  public boolean wasTransferFocusByEnter() {
    return isEditable() ? editorField.wasTransferFocusByEnter() : super.wasTransferFocusByEnter();
  }



  @Override
  public Object getValueShown() {
    return isEditable() ? getEditorField().getValueShown() : super.getSelectedItem();
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to setFormValueText
   */
  @Override
  public void fireValueEntered () {
    if (isEditable()) {
      // try to find the value in the list and select it if found.
      // if no match found: do nothing, i.e. field not from list
      setFormValueText(editorField.getText());
    }
    super.fireValueEntered();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to show the value if editable.
   * @param e the focus lost event
   */
  @Override
  public void focusLost (FocusEvent e)  {
    super.focusLost(e);
    if (!e.isTemporary() && isEditable() && !isCellEditorUsage() && isAutoUpdate()) {
      // show what has been read
      fireValueChanged();
    }
  }

  @Override
  public boolean setFormValueText(String text) {
    boolean rv = super.setFormValueText(text);
    editorField.setText(text);  // in case setFormValueText() did not match
    return rv;
  }

  @Override
  public void setFormat(String format) {
    editorField.setFormat(format);
  }

  @Override
  public String getFormat() {
    return editorField.getFormat();
  }

  @Override
  public void setColumns(int columns) {
    editorField.setColumns(columns);
  }

  @Override
  public int getColumns() {
    return editorField.getColumns();
  }

  @Override
  public void setAutoSelect(boolean autoselect) {
    editorField.setAutoSelect(autoselect);
  }

  @Override
  public boolean isAutoSelect() {
    return editorField.isAutoSelect();
  }

  @Override
  public void setAutoNext(boolean autonext) {
    editorField.setAutoNext(autonext);
  }

  @Override
  public boolean isAutoNext() {
    return editorField.isAutoNext();
  }

  @Override
  public void setAutoUpdate(boolean autoupdate) {
    editorField.setAutoUpdate(autoupdate);
  }

  @Override
  public boolean isAutoUpdate() {
    return editorField.isAutoUpdate();
  }

  @Override
  public void setConvert(char convert) {
    editorField.setConvert(convert);
  }

  @Override
  public char getConvert() {
    return editorField.getConvert();
  }

  @Override
  public void setAdjust(char adjust) {
    editorField.setAdjust(adjust);
  }

  @Override
  public void setConverter(Function<String,String> converter) {
    editorField.setConverter(converter);
  }

  @Override
  public Function<String,String> getConverter() {
    return editorField.getConverter();
  }

  @Override
  public char getAdjust() {
    return editorField.getAdjust();
  }

  @Override
  public void setMaxColumns(int columns) {
    editorField.setMaxColumns(columns);
  }

  @Override
  public int getMaxColumns() {
    return editorField.getMaxColumns();
  }

  @Override
  public void setDefaultColumns(int columns) {
    editorField.setDefaultColumns(columns);
  }

  @Override
  public int getDefaultColumns() {
    return editorField.getDefaultColumns();
  }

  @Override
  public void setFiller(char filler) {
    editorField.setFiller(filler);
  }

  @Override
  public char getFiller() {
    return editorField.getFiller();
  }

  @Override
  public void setOverwrite(boolean override) {
    editorField.setOverwrite(override);
  }

  @Override
  public boolean isOverwrite() {
    return editorField.isOverwrite();
  }

  @Override
  public void setValidChars(String str) {
    editorField.setValidChars(str);
  }

  @Override
  public String getValidChars() {
    return editorField.getValidChars();
  }

  @Override
  public void setInvalidChars(String str) {
    editorField.setInvalidChars(str);
  }

  @Override
  public String getInvalidChars() {
    return editorField.getInvalidChars();
  }

  @Override
  public void setEraseFirst(boolean erasefirst) {
    editorField.setEraseFirst(erasefirst);
  }

  @Override
  public boolean isEraseFirst() {
    return editorField.isEraseFirst();
  }

  @Override
  public void setCaretPosition(int pos) {
    editorField.setCaretPosition(pos);
  }

  @Override
  public int getCaretPosition() {
    return editorField.getCaretPosition();
  }

  @Override
  public boolean isCaretLeft() {
    return editorField.isCaretLeft();
  }

  @Override
  public boolean isCaretRight() {
    return editorField.isCaretRight();
  }

  @Override
  public void setHorizontalAlignment(int alignment) {
    editorField.setHorizontalAlignment(alignment);
  }

  @Override
  public int getHorizontalAlignment() {
    return editorField.getHorizontalAlignment();
  }

  @Override
  public void setVerticalAlignment(int alignment) {
    editorField.setVerticalAlignment(alignment);
  }

  @Override
  public int getVerticalAlignment() {
    return editorField.getVerticalAlignment();
  }

  @Override
  public void requestFocusLater() {
    FormUtilities.getInstance().requestFocusLater(this);   // this will invoke requestFocusInWindow()
  }

  @Override
  public boolean isInhibitAutoSelect() {
    return editorField.isInhibitAutoSelect();
  }

  @Override
  public void setInhibitAutoSelect(boolean inhibitAutoSelect) {
    editorField.setInhibitAutoSelect(inhibitAutoSelect);
  }

  @Override
  public void clearText() {
    editorField.clearText();
  }

  @Override
  public void setCaretLeft()  {
    editorField.setCaretLeft();
  }

  @Override
  public void setCaretRight() {
    editorField.setCaretRight();
  }

  /**
   * {@inheritDoc}
   * <p>
   * If the popup is visible the UP-key will move to the item above
   * the current item.
   */
  @Override
  public void upLeft() {
    if (isPopupVisible()) {
      int max = getItemCount();
      if (max > 0) {
        int ndx = getSelectedIndex();
        if (ndx != -1 && ndx > 0) {
          setSelectedIndex(ndx - 1);
        }
        else {
          setSelectedIndex(max - 1);
        }
      }
    }
    else  {
      editorField.upLeft();
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * If the popup is visible the DOWN-key will move to the item below
   * the current item.
   */
  @Override
  public void downRight() {
    if (isPopupVisible()) {
      int max = getItemCount();
      if (max > 0) {
        int ndx = getSelectedIndex();
        if (ndx == -1 || ndx < max - 1) {
          setSelectedIndex(ndx + 1);
        }
        else {
          setSelectedIndex(0);
        }
      }
    }
    else  {
      editorField.downRight();
    }
  }

  @Override
  public int getErrorOffset() {
    return editorField.getErrorOffset();
  }

  @Override
  public void setErrorOffset(int errorOffset) {
    editorField.setErrorOffset(errorOffset);
  }

  @Override
  public String getErrorMessage() {
    return editorField.getErrorMessage();
  }

  @Override
  public void setErrorMessage(String errorMessage) {
    editorField.setErrorMessage(errorMessage);
  }

  @Override
  public void setText(String str) {
    editorField.setText(str);
  }

  @Override
  public String getText() {
    return editorField.getText();
  }

  @Override
  public boolean isEmpty()  {
    return editorField.isEmpty();
  }

  @Override
  public String doFormat(Object object)  {
    return editorField.doFormat(object);
  }

  @Override
  public void setStartEditLeftmost (boolean startEditLeftmost)  {
    editorField.setStartEditLeftmost(startEditLeftmost);
  }

  @Override
  public boolean isStartEditLeftmost()  {
    return editorField.isStartEditLeftmost();
  }

  @Override
  public void postActionEvent() {
    editorField.postActionEvent();
  }

  @Override
  public void saveValue() {
    editorField.saveValue();
    super.saveValue();
  }

  @Override
  public void setMandatory(boolean mandatory) {
    super.setMandatory(mandatory);
    editorField.setMandatory(mandatory);
  }



}
