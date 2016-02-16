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
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.Document;
import org.tentackle.misc.Toolkit;
import org.tentackle.swing.bind.FormComponentBinding;



/**
 * Abstract base class for all text fields.
 * <p>
 * All Tentackle beans providing a single-line text field bean subclass
 * the AbstractFormField class which implements {@code FormFieldComponent}.
 * FormFields provide a lot of features essential for typical business
 * applications, such as data binding.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public abstract class AbstractFormField extends JTextField implements FormFieldComponent {

  /** caret position for (first) error, -1 if no error */
  protected int errorOffset = -1;

  /** the error message corresponding to errorOffset */
  protected String errorMessage;

  /** last savepoint */
  protected String savedValue;

  private boolean honourChangeable = true;          // true if honour setChangeable
  private boolean changeable = true;                // true if component is changeable
  private boolean allChangeable = true;             // true if container is changeable
  private boolean autoUpdate = true;                // true if data binding enabled (default)
  private char convert = CONVERT_NONE;              // character conversion, default is NONE
  private char adjust = ADJUST_TRIM;                // text adjustment, default is TRIM
  private boolean autoSelect;                       // true if autoselect on focus gained
  private boolean inhibitAutoSelect;                // inhibit autoselect once
  private boolean autoNext;                         // transfer focus to next field if all chars entered
  private int defaultColumns;                       // default columns for GUI designers (not checked by the model)
  private int maxColumns;                           // max. number of columns. 0 if no limit (checked by the model)
  private String validChars;                        // valid characters, null = all
  private String invalidChars;                      // invalid characters, null = none
  private char filler = ' ';                        // the fill character (defaults to space)
  private boolean override;                         // true if override, else insert mode
  private boolean startEditLeftmost;                // true if start edit leftmost in field
  private boolean eraseFirst;                       // erase text before first setText()
  private boolean wasError;                         // flag in autoupdate to inhibit toForm
  private boolean fireRunning;                      // indicates fireValue... running
  private int verticalAlignment = JLabel.CENTER;    // vertical alignment (only with Tentackle LAF)
  private String helpURL;                           // URL for online help,  null = none
  private Window parentWindow;                      // parent window (cached)
  private TooltipDisplay tooltipDisplay;            // tooltip display
  private boolean formTraversable = true;           // true if component is allowed for autofocus
  private boolean tableCellEditorUsage;             // true if component is used as a table cell editor
  private boolean transferFocusByEnter;             // focus lost due to ENTER
  private boolean transferFocusDone;                // transfer focus forward done
  private boolean transferFocusBackwardDone;        // transfer focus backward done
  private boolean focusGainedFromTransfer;          // focus gained by transfer focus forward
  private boolean focusGainedFromTransferBackward;  // focus gained by transfer focus backward
  private boolean valueChangedTriggered;            // value changed and propagated to parent(s)
  private boolean triggerValueChangedEnabled = true;  // true if trigger value changed is enabled
  private boolean skipNextFocusLost;                // keep focus on next focus lost
  private FormWindow formWrapWindow;                // the window to form wrap event on next focus lost
  private boolean bindable = true;                  // true if bindable
  private String componentPath;                     // the component's declaration path
  private String bindingPath;                       // the field path to bind, null if autobinding
  private FormComponentBinding binding;             // the current binding, null if none
  private boolean smartValueEntered = true;         // true if fire value entered only if really changed
  private String oldValueShown;                     // the string representation to detect whether there really was a change
  private boolean mandatory;                        // true if field is mandatory
  private boolean focusLostWasTemporary;            // true if last FOCUS_LOST was temporary
  private KeyEvent lastKeyEvent;                    // last key event
  private FocusTraversalGroup focusTraversalGroup;  // the focus traversal group, null = none
  private PropertyGroup propertyGroup;              // the property group, null = none



  /**
   * Creates a AbstractFormField.<br>
   * Notice: setting doc != null requires a doc derived from FormFieldDocument.
   *
   * @param doc the document model, null = default
   * @param str the initial text, null = empty
   * @param columns the number of columns, 0 = minimum width
   */
  public AbstractFormField (Document doc, String str, int columns) {

    super (doc, str, columns);

    setAlignmentX(0);

    // add Key mappings
    FormUtilities.getInstance().setupDefaultBindings(this);

    // set text again because of new document-model
    if (str != null)  {
      setText(str);
    }
  }

  /**
   * Creates a AbstractFormField with the default document model.<br>
   *
   * @param str the initial text, null = empty
   * @param columns the number of columns, 0 = minimum width
   */
  public AbstractFormField (String str, int columns)  {
    this (null, str, columns);
  }

  /**
   * Creates a AbstractFormField with the default document model,
   * minimum width and given initial text.<br>
   *
   * @param str the initial text, null = empty
   */
  public AbstractFormField (String str) {
    this (null, str, 0);
  }

  /**
   * Creates a AbstractFormField with the default document model and
   * given column width.<br>
   *
   * @param columns the number of columns, 0 = minimum width
   */
  public AbstractFormField (int columns)  {
    this (null, null, columns);
  }

  /**
   * Creates an empty AbstractFormField with the default document model,
   * and minimum column width.<br>
   */
  public AbstractFormField () {
    this (null, null, 0);
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden:
   * Changes the nerving behaviour that pressing backspace at the end
   * of a selection clears the whole selection. Especially in autoselected
   * numeric fields its often necessary to overtype the last digits.
   * With this hack backspace simply clears the selection.
   */
  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (pressed) {
      lastKeyEvent = e;   // remember last key event
      if (isAutoSelect() && ks.getKeyCode() == KeyEvent.VK_BACK_SPACE && ks.getModifiers() == 0) {
        int selStart = getSelectionStart();
        int selEnd   = getSelectionEnd();
        if (selEnd > selStart && getCaretPosition() == selEnd)  {
          // only if something selected and caret is at rightmost position of selection
          setSelectionStart(getSelectionEnd());   // clear selection, leave caret rightmost
        }
      }
      if (e.getKeyCode() == KeyEvent.VK_Z && e.getModifiers() == KeyEvent.CTRL_MASK) {
        // undo
        restoreSavedValue();
      }
    }
    return super.processKeyBinding(ks, e, condition, pressed);
  }


  /**
   * Restores the previously saved value.<br>
   * Method is provided to be overridden by {@link AbstractNumberFormField}.
   */
  protected void restoreSavedValue() {
    setText(savedValue);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to inhibit excessive geometry-management (for example in Gridbaglayout).
   */
  @Override
  public void setColumns(int columns) {
    super.setColumns(columns);
    // @todo: is this still necessary for the latest JDK?
    setMinimumSize(getPreferredSize());
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to set the default {@link FormFieldDocument}.
   */
  @Override
  protected Document createDefaultModel ()  {
    return new FormFieldDocument (this);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to perform focus handling and data binding
   */
  @Override
  protected void processFocusEvent(FocusEvent e) {
    super.processFocusEvent(e);
    if (e.isTemporary()) {
      if (e.getID() == FocusEvent.FOCUS_LOST) {
        focusLostWasTemporary = true;
      }
    }
    else {
      if (e.getID() == FocusEvent.FOCUS_GAINED) {
        if (focusLostWasTemporary) {
          focusLostWasTemporary = false;
        }
        else  {
          performFocusGained(e.getOppositeComponent());
        }
      }
      else if (e.getID() == FocusEvent.FOCUS_LOST)  {
        hideErrorPopup();
        if (skipNextFocusLost)  {
          skipNextFocusLost = false;
          performWrapEvent();
        }
        else  {
          performFocusLost();
        }
      }
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to turn off autoNext during inserts
   * and to implement eraseFirst.
   */
  @Override
  public void setText (String str)  {

    focusLostWasTemporary = false;

    if (autoNext) {
      autoNext = false;
      super.setText(str);
      autoNext = true;
    }
    else {
      super.setText(str);
    }

    if (eraseFirst) {
      // trigger eraseFirst in Document after this setText
      Document doc = getDocument();
      try {
        ((FormFieldDocument)doc).setEraseFirst(true);
      }
      catch (Exception e) {}
      eraseFirst = false;
    }

    // always start at left side (useful for long text not fitting into field)
    setCaretLeft();
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden because of text adjustment.
   */
  @Override
  public String getText() {
    String str = super.getText();
    if (str != null)  {
      int len = str.length();
      if (adjust == ADJUST_LEFT || adjust == ADJUST_TRIM) {
        int i = 0;
        while (i < len && str.charAt(i) == filler) {
          i++;
        }
        if (i > 0)  {
          str = str.substring(i);
          len -= i;
        }
      }
      if (adjust == ADJUST_RIGHT || adjust == ADJUST_TRIM)  {
        int i = len - 1;
        while (i > 0 && str.charAt(i) == filler) {
          i--;
        }
        i++;
        if (i < len)  {
          str = str.substring(0, i);
        }
      }
    }
    return str;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Ovwerwritten to set transferFocusDone.
   */
  @Override
  public void transferFocus() {
    transferFocusDone = true;
    super.transferFocus();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Ovwerwritten to set transferFocusBackwardDone.
   */
  @Override
  public void transferFocusBackward() {
    transferFocusBackwardDone = true;
    super.transferFocusBackward();
  }



  @Override
  public void showErrorPopup(String message) {
    FormUtilities.getInstance().showErrorPopup(this, message);
  }


  /**
   * Removes any error popup.
   */
  protected void hideErrorPopup() {
    FormUtilities.getInstance().showErrorPopup(this, null);
  }

  /**
   * Shows the error popup if parsing failed
   */
  protected void showErrorPopup() {
    showErrorPopup(errorMessage);
  }






  /**
   * shows the tooltip
   */
  private void showTooltip(String text)  {
    TooltipDisplay td = getTooltipDisplay();
    if (td != null) {
      td.setTooltip(text);
    }
  }


  /**
   * Gets the tooltip display.
   */
  private TooltipDisplay getTooltipDisplay() {
    if (tooltipDisplay == null) {
      Window w = getParentWindow();
      if (w instanceof FormWindow) {
        // getParentWindow is fast, because its cached!
        tooltipDisplay = ((FormWindow) w).getTooltipDisplay();
      }
    }
    return tooltipDisplay;
  }



  /**
   * process the focus gained event
   */
  private void performFocusGained (Component opposite)  {
    boolean wasEnter = false;
    if (opposite instanceof FormComponent)  {
      focusGainedFromTransfer         = ((FormComponent)opposite).wasTransferFocus();
      focusGainedFromTransferBackward = ((FormComponent)opposite).wasTransferFocusBackward();
      wasEnter                        = ((FormComponent)opposite).wasTransferFocusByEnter();
    }
    transferFocusDone = false;
    transferFocusBackwardDone = false;
    if (wasError)  {
      // in JTables the text will be empty, so we must check length before
      if (errorOffset >= 0 && getDocument().getLength() > errorOffset)  {
        setCaretPosition (errorOffset);
      }
      wasError = false;
    }
    else  {
      if (isAutoUpdate()) {
        int caretPos = getCaretPosition();    // remember caret-position
        // update the fields display
        fireValueChanged();
        // after setText(), the caret is at the left side of the field.
        // Move to the right if we came down with VK_DOWN.
        if (focusGainedFromTransfer && !wasEnter && !startEditLeftmost)  {
          setCaretRight();
        }
        else if (focusGainedFromTransferBackward || wasEnter) {
          setCaretLeft();
        }
        else  {
          // set to position marked by mouse (aligned for sure)
          if (caretPos < 0) {
            caretPos = 0;
          }
          else if (caretPos > getDocument().getLength()) {
            caretPos = getDocument().getLength();
          }
          setCaretPosition(caretPos);
        }
      }
      // select all if autoselect enabled
      if (isAutoSelect() && !inhibitAutoSelect)   {
        selectAll();
      }
    }
    transferFocusByEnter = false;
    inhibitAutoSelect = false;
    formWrapWindow = null;

    showTooltip(super.getToolTipText());

    oldValueShown = getValueShown();
  }


  /**
   * process the focus lost event
   */
  private void performFocusLost ()  {
    transferFocusByEnter = lastKeyEvent != null &&
                           (lastKeyEvent.getKeyCode() == KeyEvent.VK_ENTER || lastKeyEvent.getKeyCode() == KeyEvent.VK_TAB) &&
                           (lastKeyEvent.getModifiers() == 0 || lastKeyEvent.getModifiers() == KeyEvent.SHIFT_MASK);
    wasError = false;
    setEraseFirst(false);

    if (isChangeable()) {
      // update data field and show what has been read
      if (isCellEditorUsage() || !isSmartValueEntered() || isValueShownModified()) {
        errorOffset = -1;
        errorMessage = null;
        if (isAutoUpdate()) {
          // transfer to model
          fireValueEntered();
        }
        else  {
          // don't transfer to model, just update the view
          setFormValue(getFormValue());
        }
      }
      if (errorOffset >= 0) { // some conversion error
        // show where the error was
        setCaretPosition (errorOffset);
        Toolkit.beep();
        wasError = true;
        showErrorPopup();
        requestFocusLater();
      }
      else  {
        if (isAutoUpdate()) {
          // show what has been read
          fireValueChanged();
          // run the optional validation
          FormUtilities.getInstance().doValidate(this);
        }
      }
    }
    performWrapEvent();
    showTooltip(null);
    lastKeyEvent = null;
  }


  /**
   * process the form wrap event
   */
  private void performWrapEvent() {
    if (formWrapWindow != null)  {
      formWrapWindow.fireFormWrappedFocus(new FormWrapEvent(this));
      formWrapWindow = null;
    }
  }





  // -------------------- implements FormFieldComponent ---------------------


  @Override
  public synchronized void addValueListener (ValueListener listener) {
     listenerList.add (ValueListener.class, listener);
  }

  @Override
  public synchronized void removeValueListener (ValueListener listener) {
     listenerList.remove (ValueListener.class, listener);
  }

  @Override
  public void requestFocusLater() {
    FormUtilities.getInstance().requestFocusLater(this);
  }

  @Override
  public void fireValueChanged () {
    FormUtilities.getInstance().doFireValueChanged (this, listenerList.getListenerList());
    valueChangedTriggered = false;
  }

  @Override
  public void fireValueEntered () {
    valueChangedTriggered = false;  // check always after field exit
    FormUtilities.getInstance().doFireValueEntered (this, listenerList.getListenerList());
  }

  @Override
  public void saveValue() {
    if (isHonourChangeable()) {
      savedValue = super.getText();
      valueChangedTriggered = false;
    }
  }

  @Override
  public boolean isValueChanged() {
    if (isHonourChangeable()) {
      String value = super.getText();
      if (savedValue == null) {
        return value != null;
      }
      else {
        return value == null || !value.equals(savedValue);
      }
    }
    return false;
  }

  @Override
  public void setTriggerValueChangedEnabled(boolean enabled) {
    this.triggerValueChangedEnabled = enabled;
  }

  @Override
  public boolean isTriggerValueChangedEnabled() {
    return triggerValueChangedEnabled;
  }

  @Override
  public void triggerValueChanged()   {
    if (triggerValueChangedEnabled && !valueChangedTriggered && !fireRunning) {
      // if not already promoted
      FormUtilities.getInstance().triggerValueChanged(this);
      // don't trigger again when field is now changed (this is the usual case).
      // But trigger on next keystroke, if field became unchanged due to this keystoke!
      valueChangedTriggered = isValueChanged();
    }
  }

  @Override
  public void setCellEditorUsage(boolean flag) {
    tableCellEditorUsage = flag;
  }

  @Override
  public boolean isCellEditorUsage() {
    return tableCellEditorUsage;
  }

  @Override
  public void prepareFocusLost()  {
    if (!tableCellEditorUsage) {
      performFocusLost();
      if (errorOffset < 0) {
        skipNextFocusLost = true;
      }
    }
    // else: triggered by FOCUS_LOST
  }

  @Override
  public void showHelp()  {
    FormUtilities.getInstance().openHelpURL(this);
  }

  @Override
  public void clearText() {
    setText("");
  }

  @Override
  public boolean isEmpty()  {
    String text = getText();
    return text == null || text.length() == 0;
  }

  @Override
  public boolean isCaretLeft()  {
    return getCaretPosition() == 0;
  }

  @Override
  public boolean isCaretRight() {
    return getCaretPosition() == getDocument().getLength();
  }

  @Override
  public void upLeft()  {
    if (isCaretLeft())  {
      // go to previous field if we already are at start
      transferFocusBackward();
    }
    else  {
      setCaretLeft();
    }
  }

  @Override
  public void downRight()  {
    if (isCaretRight())  {
     // go to next field if we already stay at right bound
      transferFocus();
    }
    else  {
      setCaretRight();
    }
  }

  @Override
  public void setCaretLeft()  {
    setCaretPosition(0);
  }

  @Override
  public void setCaretRight() {
    setCaretPosition (getDocument().getLength());
  }

  @Override
  public void setEraseFirst(boolean erasefirst) {
    this.eraseFirst = erasefirst;
    if (!erasefirst)  {
      Document doc = getDocument();
      try {
        ((FormFieldDocument)doc).setEraseFirst(false);
      } catch (Exception e) {}
    }
  }

  @Override
  public boolean isEraseFirst()  {
    return eraseFirst;
  }

  @Override
  public void setChangeable (boolean changeable) {
    if (isHonourChangeable()) {
      this.changeable = changeable;
      super.setEditable(changeable && allChangeable);
    }
  }

  @Override
  public boolean isChangeable() {
    return changeable && allChangeable;
  }

  @Override
  public void setHonourChangeable(boolean flag) {
    this.honourChangeable = flag;
  }

  @Override
  public boolean isHonourChangeable() {
    return honourChangeable;
  }

  @Override
  public void updateAllChangeable(boolean allChangeable) {
    if (this.allChangeable != allChangeable) {
      this.allChangeable = allChangeable;
      setChangeable(changeable);
    }
  }

  @Override
  public void setEditable(boolean editable) {
    super.setEditable(editable);
    this.changeable = editable;
  }


  @Override
  public int getDefaultColumns() {
    return defaultColumns;
  }

  @Override
  public void setDefaultColumns(int defaultColumns) {
    if (defaultColumns < 0) {
      throw new IllegalArgumentException("defaultColumns less than zero.");
    }
    if (defaultColumns != this.defaultColumns) {
      this.defaultColumns = defaultColumns;
      if (getColumns() == 0) {
        invalidate(); // --> see overridden getPreferredSize below
      }
    }
  }

  @Override
  public void setMaxColumns (int maxColumns) {
    if (maxColumns < 0) {
      throw new IllegalArgumentException("maxColumns less than zero.");
    }
    if (maxColumns != this.maxColumns) {
      this.maxColumns = maxColumns;
      // this will set the filler, if any
      setText(getText());
      // set the size if columns hasn't been set yet
      if (getColumns() == 0) {
        invalidate(); // --> see overridden getPreferredSize below
      }
    }
  }

  @Override
  public int getMaxColumns ()  {
    return maxColumns;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (getColumns() == 0) {
      int cols = defaultColumns > 0 ? defaultColumns : maxColumns;
      // show component with minumum or maximum columns
      // if columns have not been set so far.
      if (cols > 0) {
        Insets insets = getInsets();
        size.width = cols * getColumnWidth() + insets.left + insets.right;
      }
    }
    return size;
  }





  @Override
  public void setConvert(char convert) {
    this.convert = convert;
    String text = getText();
    if (text != null) {
      switch (convert) {
        case CONVERT_UC:
          setText(text.toUpperCase());
          break;
        case CONVERT_LC:
          setText(text.toLowerCase());
          break;
      }
    }
  }

  @Override
  public char getConvert() {
    return convert;
  }

  @Override
  public void setAdjust(char adjust) {
    this.adjust = adjust;
  }

  @Override
  public char getAdjust() {
    return adjust;
  }

  @Override
  public void setAutoSelect(boolean autoselect) {
    this.autoSelect = autoselect;
  }

  @Override
  public boolean isAutoSelect() {
    return autoSelect;
  }

  @Override
  public void setAutoNext(boolean autonext) {
    this.autoNext = autonext;
  }

  @Override
  public boolean isAutoNext() {
    return autoNext;
  }

  @Override
  public void setAutoUpdate(boolean autoupdate) {
    this.autoUpdate = autoupdate;
  }

  @Override
  public boolean isAutoUpdate() {
    return autoUpdate;
  }

  @Override
  public void setValidChars(String validChars) {
    this.validChars = validChars;
  }

  @Override
  public String getValidChars ()  {
    return validChars;
  }

  @Override
  public  void  setInvalidChars (String invalidChars)  {
    this.invalidChars = invalidChars;
  }

  @Override
  public String getInvalidChars ()  {
    return invalidChars;
  }

  @Override
  public void setFiller(char filler) {
    char oldfiller = this.filler;
    this.filler = filler;
    // this will set the filler
    if (!isEmpty()) {
      setText(getText().replace(oldfiller, filler));
    }
  }

  @Override
  public char getFiller() {
    return filler;
  }

  @Override
  public void setOverwrite(boolean override) {
    this.override = override;
  }

  @Override
  public boolean isOverwrite() {
    return override;
  }

  @Override
  public void setStartEditLeftmost(boolean startEditLeftmost) {
    this.startEditLeftmost = startEditLeftmost;
  }

  @Override
  public boolean isStartEditLeftmost() {
    return startEditLeftmost;
  }

  @Override
  public int getErrorOffset() {
    return errorOffset;
  }

  @Override
  public void setErrorOffset(int errorOffset) {
    this.errorOffset = errorOffset;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @Override
  public void setFireRunning(boolean running) {
    this.fireRunning = running;
  }

  @Override
  public boolean isFireRunning()  {
    return fireRunning;
  }

  @Override
  public boolean isInhibitAutoSelect() {
    return inhibitAutoSelect;
  }

  /**
   * {@inheritDoc}
   * <p>
   * When set inhibits autoSelect once after having performed eraseFirst.
   * This is necessary for components in a JTable because after pressing a key
   * eraseFirst will be executed, then the key inserted and THEN the field
   * gets the focusGained which will selectAll if autoSelect enabled.
   * The eraseFirst is triggered in FormFieldComponentCellEditor.
   * This flag is really package internal!
   */
  @Override
  public void setInhibitAutoSelect(boolean inhibitAutoSelect) {
    this.inhibitAutoSelect = inhibitAutoSelect;
  }

  @Override
  public void setFormWrapWindow(FormWindow parent)  {
    formWrapWindow = parent;
  }

  @Override
  public void setVerticalAlignment(int alignment) {
    this.verticalAlignment = alignment;
  }

  @Override
  public int  getVerticalAlignment()  {
    return verticalAlignment;
  }

  @Override
  public String getHelpURL() {
    return helpURL;
  }

  @Override
  public void setHelpURL(String helpURL) {
    this.helpURL = helpURL;
  }

  @Override
  public Function<String,String> getConverter() {
    Document doc = getDocument();
    try {
      return ((FormFieldDocument)doc).getConverter();
    }
    catch (Exception e) {
      return null;
    }
  }

  @Override
  public void setConverter(Function<String,String> converter) {
    Document doc = getDocument();
    try {
      ((FormFieldDocument)doc).setConverter(converter);
    } catch (Exception e) {}
  }

  @Override
  public Window getParentWindow() {
    if (parentWindow == null) {
      parentWindow = FormUtilities.getInstance().getParentWindow(this);
    }
    return parentWindow;
  }

  @Override
  public void invalidateParentInfo()  {
    parentWindow = null;
    tooltipDisplay = null;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to show tooltip in tooltipdisplay OR via mouse
   * but not both.
   */
  @Override
  public String getToolTipText() {
    return getTooltipDisplay() == null ? super.getToolTipText() : null;
  }

  @Override
  public boolean wasTransferFocus() {
    return transferFocusDone;
  }

  @Override
  public boolean wasTransferFocusBackward() {
    return transferFocusBackwardDone;
  }

  @Override
  public boolean wasFocusGainedFromTransfer()  {
    return focusGainedFromTransfer;
  }

  @Override
  public boolean wasFocusGainedFromTransferBackward()  {
    return focusGainedFromTransferBackward;
  }

  @Override
  public boolean wasTransferFocusByEnter() {
    return transferFocusByEnter;
  }

  @Override
  public void setFormTraversable(boolean formTraversable) {
    this.formTraversable = formTraversable;
  }

  @Override
  public boolean isFormTraversable() {
    return formTraversable;
  }

  @Override
  public void setFocusTraversalGroup(FocusTraversalGroup group) {
    if (this.focusTraversalGroup != group) {
      if (this.focusTraversalGroup != null) {
        if (this.focusTraversalGroup.removeComponent(this)) {
          this.focusTraversalGroup = null;
        }
        else  {
          throw new GUIRuntimeException("component " + this + " not in focus travseral group " + this.focusTraversalGroup);
        }
      }
      if (group != null) {
        if (group.addComponent(this)) {
          this.focusTraversalGroup = group;
        }
        else  {
          throw new GUIRuntimeException("component " + this + " already in focus travseral group " + group);
        }
      }
    }
  }

  @Override
  public FocusTraversalGroup getFocusTraversalGroup() {
    return focusTraversalGroup;
  }


  @Override
  public void setPropertyGroup(PropertyGroup group) {
    if (this.propertyGroup != group) {
      if (this.propertyGroup != null) {
        if (this.propertyGroup.removeComponent(this)) {
          this.propertyGroup = null;
        }
        else  {
          throw new GUIRuntimeException("component " + this + " not in property group " + this.propertyGroup);
        }
      }
      if (group != null) {
        if (group.addComponent(this)) {
          this.propertyGroup = group;
        }
        else  {
          throw new GUIRuntimeException("component " + this + " already in property group " + group);
        }
      }
    }
  }

  @Override
  public PropertyGroup getPropertyGroup() {
    return propertyGroup;
  }


  @Override
  public void setMandatory(boolean mandatory) {
    if (this.mandatory != mandatory) {
      boolean oldValue = this.mandatory;
      this.mandatory = mandatory;
      firePropertyChange("mandatory", oldValue, mandatory);
      invalidate();
      repaint();
    }
  }

  @Override
  public boolean isMandatory() {
    return mandatory;
  }

  @Override
  public void setComponentPath(String componentPath) {
    this.componentPath = componentPath;
  }

  @Override
  public String getComponentPath() {
    return componentPath;
  }

  @Override
  public void setBindingPath(String bindingPath) {
    this.bindingPath = bindingPath;
  }

  @Override
  public String getBindingPath() {
    return bindingPath;
  }

  @Override
  public void setBinding(FormComponentBinding binding) {
    this.binding = binding;
  }

  @Override
  public FormComponentBinding getBinding() {
    return binding;
  }

  @Override
  public void setBindable(boolean bindable) {
    this.bindable = bindable;
  }

  @Override
  public boolean isBindable() {
    return bindable;
  }

  @Override
  public String getValueShown() {
    return super.getText();
  }

  @Override
  public boolean isValueShownModified() {
    return errorOffset >=0 || !Objects.equals(oldValueShown, getValueShown());
  }

  @Override
  public void clearValueShownModified() {
    oldValueShown = getValueShown();
  }

  @Override
  public void setSmartValueEntered(boolean smartValueEntered) {
    this.smartValueEntered = smartValueEntered;
  }

  @Override
  public boolean isSmartValueEntered() {
    return smartValueEntered;
  }

}


