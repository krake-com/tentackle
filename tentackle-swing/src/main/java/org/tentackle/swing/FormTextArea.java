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
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.swing.bind.FormComponentBinding;

import static org.tentackle.swing.FormFieldComponent.ADJUST_TRIM;
import static org.tentackle.swing.FormFieldComponent.CONVERT_LC;
import static org.tentackle.swing.FormFieldComponent.CONVERT_NONE;
import static org.tentackle.swing.FormFieldComponent.CONVERT_UC;



/**
 * Extended JTextArea as a {@link FormFieldComponent}.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormTextArea extends JTextArea implements FormFieldComponent {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(FormTextArea.class);

  private static final String TABLECELLEDITOR_PROPERTY = "JTextArea.isTableCellEditor";

  private boolean nullIfEmpty = true;               // getText() returns null if string is empty
  private boolean autoUpdate = true;                // true if data binding enabled (default)
  private boolean startEditLeftmost;                // true if start edit leftmost
  private int horizontalAlignment = JLabel.LEADING; // horizontal alignment
  private int verticalAlignment = JLabel.CENTER;    // is a dummy so far...
  private boolean skipNextFocusLost;                // keep focus on next focus lost
  private FormWindow formWrapWindow;                // window to receive wrap event
  private String savedValue;                        // last savepoint
  private String helpURL;                           // != null for online help
  private boolean smartEnter = true;                // true = smart ENTER
  private boolean enterWillInsert;                  // true = ENTER will insert a new line
  private boolean transferFocusDone;                // transfer focus forward done
  private boolean transferFocusBackwardDone;        // transfer focus backward done
  private boolean transferFocusByEnter;             // focus lost due to ENTER
  private boolean focusGainedFromTransfer;          // focus gained by transfer focus forward
  private boolean focusGainedFromTransferBackward;  // focus gained by transfer focus backward
  private boolean formTraversable = true;           // true if textarea gets keyboard focus
  private boolean valueChangedTriggered;            // value changed and propagated to parent(s)
  private boolean triggerValueChangedEnabled = true;  // true if trigger value changed is enabled
  private boolean honourChangeable = true;          // true if honour setChangeable
  private boolean changeable = true;                // true if component is changeable
  private boolean allChangeable = true;             // true if container is changeable
  private boolean fireRunning;                      // indicates fireValue... running
  private boolean eraseFirst;                       // erase text before first setText()
  private char convert = CONVERT_NONE;              // character conversion, default is NONE
  private char adjust = ADJUST_TRIM;                // text adjustment, default is TRIM
  private boolean autoSelect;                       // true if autoselect on focus gained
  private boolean inhibitAutoSelect;                // inhibit autoselect once
  private boolean autoNext;                         // transfer focus to next field if all chars entered
  private int maxColumns;                           // max. number of columns. 0 if no limit
  private String validChars;                        // valid characters, null = all
  private String invalidChars;                      // invalid characters, null = none
  private char filler = ' ';                        // the fill character (defaults to space)
  private boolean override;                         // true if override, else insert mode
  private Window parentWindow;                      // parent window (cached)
  private TooltipDisplay tooltipDisplay;            // tooltip display
  private boolean bindable = true;                  // true if bindable
  private String componentPath;                     // if bound: the component's declaration path
  private String bindingPath;                       // the field path to bind, null if autobinding
  private FormComponentBinding binding;             // the current binding, null if none
  private boolean smartValueEntered = true;         // true if fire value entered only if really changed
  private String oldValueShown;                     // the string representation to detect whether there really was a change
  private boolean mandatory;                        // true if field is mandatory
  private boolean focusLostWasTemporary;            // true if last FOCUS_LOST was temporary
  private KeyEvent lastKeyEvent;                    // last key event
  private FocusTraversalGroup focusTraversalGroup;  // the focus traversal group, null = none
  private PropertyGroup propertyGroup;              // the property group, null = none

  // default actions
  private final Action upAction;                    // action for up-key
  private final Action downAction;                  // action for down-key



  /**
   * Creates a {@code FormTextArea}.
   *
   * @param doc the document model, null if default {@link FormFieldDocument}.
   * @param text the text to be displayed, null if none
   * @param rows the number of rows &ge; 0
   * @param columns the number of columns &ge; 0
   */
  public FormTextArea(Document doc, String text, int rows, int columns) {

    super (doc, text, rows, columns);

    // add Key mappings
    setupActions();

    // get default actions
    upAction = getActionMap().get(DefaultEditorKit.upAction);
    downAction = getActionMap().get(DefaultEditorKit.downAction);
  }


  /**
   * Creates a {@code FormTextArea} with the default document model.
   *
   * @param text the text to be displayed, null if none
   * @param rows the number of rows &ge; 0
   * @param columns the number of columns &ge; 0
   */
  public FormTextArea (String text, int rows, int columns)  {
    this (null, text, rows, columns);
  }

  /**
   * Creates a {@code FormTextArea} with the default document model.
   *
   * @param text the text to be displayed, null if none
   */
  public FormTextArea (String text) {
    this (null, text, 0, 0);
  }

  /**
   * Creates an empty {@code FormTextArea} with the default document model.
   *
   * @param rows the number of rows &ge; 0
   * @param columns the number of columns &ge; 0
   */
  public FormTextArea (int rows, int columns)  {
    this (null, null, rows, columns);
  }

  /**
   * Creates an empty {@code FormTextArea} with the default document model.
   */
  public FormTextArea () {
    this (null, null, 0, 0);
  }



  @Override
  public void showErrorPopup(String message) {
    FormUtilities.getInstance().showErrorPopup(this, message);
  }



  /**
   * Sets whether empty strings should be returned as null.<br>
   *
   * @param nullIfEmpty true if zero-length input is treated as null (default)
   *
   */
  public void setNullIfEmpty(boolean nullIfEmpty) {
    this.nullIfEmpty = nullIfEmpty;
  }

  /**
   * Returns whether empty strings should be returned as null.<br>
   * @return true if zero-length input is treated as null (default)
   */
  public boolean isNullIfEmpty() {
    return nullIfEmpty;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden because of nullIfEmpty
   */
  @Override
  public String getText() {
    String text = super.getText();
    if (nullIfEmpty && text != null && text.length() == 0) {
      text = null;
    }
    return text;
  }


  /**
   * Returns whether smart enter is enabled.
   *
   * @return true if smart enter is enabled (default)
   */
  public boolean isSmartEnter() {
    return smartEnter;
  }

  /**
   * Enable smart enter.
   * When enabled pressing the Enter-key at the start of the
   * text area (as long as there are no other keys typed or mouse clicks)
   * will move to the next field and will _not_ insert a newline.
   * Inserting a newline can be achieved with Ctrl+Enter.
   *
   * @param smartEnter true to enable smart enter (default)
   */
  public void setSmartEnter(boolean smartEnter) {
    this.smartEnter = smartEnter;
  }

  /**
   * Transfers the focus to the next field if it is
   * the first key pressed in this JTextArea (and the cursor hasn't
   * been moved by the mouse). This allows the user to ENTER
   * through all fields in a mask without worrying about textareas.
   * In all other cases ENTER inserts a newline and moves the cursor
   * after the newline (as expected).
   */
  public void doSmartEnter() {
    if (enterWillInsert && smartEnter && lastKeyEvent != null && lastKeyEvent.getKeyCode() == KeyEvent.VK_ENTER)  {
      insert("\n", getCaretPosition());
    }
    else  {
      prepareFocusLost();
      transferFocus();
    }
  }



  /**
   * Adds the specified action listener to receive
   * action events from this textfield.
   *
   * @param l the action listener to be added
   */
  public synchronized void addActionListener(ActionListener l) {
    listenerList.add(ActionListener.class, l);
  }

  /**
   * Removes the specified action listener so that it no longer
   * receives action events from this textfield.
   *
   * @param l the action listener to be removed
   */
  public synchronized void removeActionListener(ActionListener l) {
    listenerList.remove(ActionListener.class, l);
  }


  /**
   * Notifies all listeners that have registered interest for
   * notification on this event type.  The event instance
   * is lazily created.
   * The listener list is processed in last to
   * first order.
   */
  protected void fireActionPerformed() {
    // Guaranteed to return a non-null array
    Object[] listeners = listenerList.getListenerList();
    ActionEvent e = new ActionEvent(
                  this, ActionEvent.ACTION_PERFORMED,
                  getText(),
                  EventQueue.getMostRecentEventTime(), 0);

    // Process the listeners last to first, notifying
    // those that are interested in this event
    for (int i = listeners.length-2; i>=0; i-=2) {
      if (listeners[i]==ActionListener.class) {
        ((ActionListener)listeners[i+1]).actionPerformed(e);
      }
    }
  }


  /**
   * Processes action events occurring on this textarea by
   * dispatching them to any registered <code>ActionListener</code> objects.
   */
  @Override
  public void postActionEvent() {
    fireActionPerformed();
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden for enhanced focus handling
   */
  @Override
  protected void processFocusEvent(FocusEvent e) {
    super.processFocusEvent(e);
    if (!e.isTemporary()) {
      if (e.getID() == FocusEvent.FOCUS_GAINED) {
        if (focusLostWasTemporary) {
          focusLostWasTemporary = false;
        }
        else  {
          performFocusGained(e.getOppositeComponent());
        }
      }
      else if (e.getID() == FocusEvent.FOCUS_LOST)  {
        if (skipNextFocusLost)  {
          skipNextFocusLost = false;
          performWrapEvent();
        }
        else  {
          performFocusLost();
        }
      }
    }
    else {
      if (e.getID() == FocusEvent.FOCUS_LOST) {
        focusLostWasTemporary = true;
      }
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden due to smart enter.
   */
  @Override
  protected void processKeyEvent(KeyEvent e)  {
    if (e.getID() == KeyEvent.KEY_PRESSED) {
      lastKeyEvent = e;
      if (e.getKeyCode() != KeyEvent.VK_ENTER) {
        enterWillInsert = true;
      }
      if (e.getKeyCode() == KeyEvent.VK_Z && e.getModifiers() == KeyEvent.CTRL_MASK) {
        // undo
        setText(savedValue);
        e.consume();
      }
    }
    super.processKeyEvent(e);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden due to smart enter.
   */
  @Override
  protected void processMouseEvent(MouseEvent e)  {
    super.processMouseEvent(e);
    if (e.getID() == MouseEvent.MOUSE_PRESSED)  {
      enterWillInsert = true;
    }
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to create a {@link FormFieldDocument}.
   */
  @Override
  protected Document createDefaultModel ()  {
    return new FormFieldDocument (this);
  }


  /**
   * Calculates the dimensions for displaying the text according
   * to the number of lines and max. chars in lines.
   * If the text area is empty the preferred size is returned.
   *
   * @return the optimal size
   */
  public Dimension getOptimalSize() {
    String text = getText();
    if (text != null) {
      FontMetrics fm = getFontMetrics(getFont());
      StringTokenizer tokenizer = new StringTokenizer(text, "\n");
      int numberOfLines = tokenizer.countTokens();
      int width = 0;
      for(int i=0; i < numberOfLines; i++) {
        int lineWidth = fm.stringWidth(tokenizer.nextToken());
        if (lineWidth > width) {
          width = lineWidth;
        }
      }
      Insets insets = getInsets();
      return new Dimension(width + insets.left + insets.right,
                           fm.getHeight() * numberOfLines + insets.bottom + insets.top);
    }
    return getPreferredSize();
  }



  @Override
  public String getToolTipText() {
    return getTooltipDisplay() == null ? super.getToolTipText() : null;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to implement wasTransferFocus
   */
  @Override
  public void transferFocus() {
    transferFocusDone = true;
    super.transferFocus();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to implement wasTransferFocusBackward
   */
  @Override
  public void transferFocusBackward() {
    transferFocusBackwardDone = true;
    super.transferFocusBackward();
  }




  // --------------------------- implements FormComponent ---------------------



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
  public void setFormValue (Object object)  {
    if (object != null)  {
      setText (object.toString());   // this allows all objects to setFormValue()
    }
    else  {
      clearText();
    }
  }

  @Override
  public String getFormValue ()  {
    return getText();
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
  public void showHelp()  {
    FormUtilities.getInstance().openHelpURL(this);
  }

  @Override
  public void requestFocusLater() {
    FormUtilities.getInstance().requestFocusLater(this);
  }

  @Override
  public synchronized void addValueListener (ValueListener l) {
     listenerList.add (ValueListener.class, l);
  }

  @Override
  public synchronized void removeValueListener (ValueListener l) {
     listenerList.remove (ValueListener.class, l);
  }

  @Override
  public void fireValueChanged () {
    FormUtilities.getInstance().doFireValueChanged (this, listenerList.getListenerList());
    valueChangedTriggered = false;
    enterWillInsert = false;
  }

  @Override
  public void fireValueEntered () {
    valueChangedTriggered = false;  // check always after field exit
    FormUtilities.getInstance().doFireValueEntered (this, listenerList.getListenerList());
  }

  @Override
  public void saveValue() {
    if (honourChangeable) {
      savedValue = super.getText();
      valueChangedTriggered = false;
    }
  }

  @Override
  public boolean isValueChanged() {
    if (honourChangeable) {
      String value = super.getText();
      if (savedValue == null) {
        return value != null;
      }
      else {
        return value == null || value.compareTo(savedValue) != 0;
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
      valueChangedTriggered = true;
    }
  }

  @Override
  public void setCellEditorUsage(boolean flag) {
    putClientProperty(TABLECELLEDITOR_PROPERTY, flag);
  }

  @Override
  public boolean isCellEditorUsage() {
    try {
      Object obj = getClientProperty(TABLECELLEDITOR_PROPERTY);
      if (obj instanceof Boolean)  {
        return ((Boolean) obj);
      }
    }
    catch (Exception ex) {
      LOGGER.severe("FormTextArea: isTableCellEditor not Boolean! " + ex);
    }
    return false;
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
  public void prepareFocusLost()  {
    if (!isCellEditorUsage()) {
      performFocusLost();
      skipNextFocusLost = true;
    }
    // else: triggered by FOCUS_LOST
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






  // --------------------------- implements FormFieldComponent -----------------


  @Override
  public void setText (String str)  {

    focusLostWasTemporary = false;

    if (str == null) {
      str = "";
    }

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
      if (doc instanceof FormFieldDocument) {
        ((FormFieldDocument)doc).setEraseFirst(true);
      }
      eraseFirst = false;
    }

    setCaretLeft(); // always start at left side (useful for long text not fitting into field)
  }


  @Override
  public void clearText() {
    setText("");
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
  public void setCaretLeft()  {
    setCaretPosition(0);
  }

  @Override
  public void setCaretRight() {
    setCaretPosition (getDocument().getLength());
  }

  @Override
  public void upLeft()  {
    if (isCaretLeft())  {
      // go to previous field if we already are at start
      transferFocusBackward();
    }
    // else move up one line
    if (upAction != null) {
      int pos = getCaretPosition();
      upAction.actionPerformed(new ActionEvent(this, 0, null));
      if (pos == getCaretPosition())  {
        // we are already in first line
        setCaretLeft();
      }
    }
  }

  @Override
  public void downRight()  {
    if (isCaretRight())  {
     // go to next field if we already stay at right bound
      transferFocus();
    }
    // else move down one line
    if (downAction != null) {
      int pos = getCaretPosition();
      downAction.actionPerformed(new ActionEvent(this, 0, null));
      if (pos == getCaretPosition())  {
        // we are already in last line
        setCaretRight();
      }
    }
  }

  @Override
  public void setConvert (char convert)  {
    this.convert = convert;
    String text = getText();
    if (text != null) {
      switch (convert) {
      case CONVERT_UC:  setText (text.toUpperCase());
                        break;
      case CONVERT_LC:  setText (text.toLowerCase());
                        break;
      }
    }
  }

  @Override
  public char getConvert ()  {
    return convert;
  }


  @Override
  public  void  setAutoSelect (boolean autoselect)  {
    this.autoSelect = autoselect;
  }

  @Override
  public boolean isAutoSelect()  {
    return autoSelect;
  }


  @Override
  public  void  setAutoUpdate (boolean autoupdate)  {
    this.autoUpdate = autoupdate;
  }

  @Override
  public boolean isAutoUpdate()  {
    return autoUpdate;
  }

  @Override
  public  void  setValidChars (String str)  {
    this.validChars = str.length() == 0 ? null : str;
  }

  @Override
  public String getValidChars ()  {
    return validChars;
  }

  @Override
  public  void  setInvalidChars (String str)  {
    this.invalidChars = str.length() == 0 ? null : str;
  }

  @Override
  public String getInvalidChars ()  {
    return invalidChars;
  }

  @Override
  public  void  setFiller (char filler)  {
    char oldfiller = this.filler;
    this.filler = filler;
    // this will set the filler
    setText (getText().replace(oldfiller, filler));
  }

  @Override
  public char getFiller ()  {
    return filler;
  }

  @Override
  public  void  setOverwrite (boolean override)  {
    this.override = override;
  }

  @Override
  public boolean isOverwrite()  {
    return override;
  }


  @Override
  public void setEraseFirst(boolean erasefirst) {
    this.eraseFirst = erasefirst;
    if (!erasefirst)  {
      Document doc = getDocument();
      if (doc instanceof FormFieldDocument) {
        ((FormFieldDocument)doc).setEraseFirst(false);
      }
    }
  }

  @Override
  public boolean isEraseFirst()  {
    return eraseFirst;
  }


  /**
   * {@inheritDoc}
   * <p>
   * The format will be ignored in FormTextArea.
   */
  @Override
  public void setFormat (String pattern) {
    // do nothing
  }

  /**
   * {@inheritDoc}
   * <p>
   * The format will be ignored in FormTextArea.
   */
  @Override
  public String getFormat ()  {
    return "";
  }

  /**
   * {@inheritDoc}
   * <p>
   * The format will be ignored in FormTextArea.
   * @return just returns the given string
   */
  @Override
  public String doFormat (Object object) {
    return object == null ? null : object.toString();
  }


  @Override
  public void  setAutoNext (boolean autonext) {
    this.autoNext = autonext;
  }

  @Override
  public boolean isAutoNext()  {
    return autoNext;
  }


  @Override
  public  void  setAdjust (char adjust) {
    this.adjust = adjust;
  }

  @Override
  public  char  getAdjust ()  {
    return adjust;
  }


  @Override
  public  void  setMaxColumns (int maxColumns) {
    this.maxColumns = maxColumns;
  }

  @Override
  public int  getMaxColumns ()  {
    return maxColumns;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Ignored in FormTextArea.
   */
  @Override
  public void setDefaultColumns (int defaultColumns) {
    // ignored
  }

  @Override
  public int  getDefaultColumns ()  {
    return getColumns();
  }


  /**
   * Not implemented in FormTextArea.
   * <p>
   * {@inheritDoc}
   */
  @Override
  public void setHorizontalAlignment(int alignment) {
    this.horizontalAlignment = alignment;
  }

  /**
   * Not implemented in FormTextArea.
   * <p>
   * {@inheritDoc}
   */
  @Override
  public int  getHorizontalAlignment() {
    return horizontalAlignment;
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
  public boolean isInhibitAutoSelect() {
    return inhibitAutoSelect;
  }

  @Override
  public void setInhibitAutoSelect(boolean inhibitAutoSelect) {
    this.inhibitAutoSelect = inhibitAutoSelect;
  }


  @Override
  public void setFireRunning(boolean fireRunning) {
    this.fireRunning = fireRunning;
  }

  @Override
  public boolean isFireRunning()  {
    return fireRunning;
  }

  @Override
  public void setFormWrapWindow(FormWindow parent)  {
    formWrapWindow = parent;
  }


  @Override
  public Function<String,String> getConverter() {
    Document doc = getDocument();
    if (doc instanceof FormFieldDocument) {
      return ((FormFieldDocument)doc).getConverter();
    }
    return null;
  }

  @Override
  public void setConverter(Function<String,String> converter) {
    Document doc = getDocument();
    if (doc instanceof FormFieldDocument) {
      ((FormFieldDocument)doc).setConverter(converter);
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * By default, TextArea's don't provide formatting and thus no
   * possibilities for errors.
   * @return always -1 (no error)
   */
  @Override
  public int getErrorOffset() {
    return -1;
  }

  @Override
  public void setErrorOffset(int errorOffset) {
  }

  /**
   * {@inheritDoc}
   * <p>
   * By default, TextArea's don't provide formatting and thus no
   * possibilities for errors.
   * @return always null (no error)
   */
  @Override
  public String getErrorMessage() {
    return null;
  }

  @Override
  public void setErrorMessage(String errorMessage) {
  }


  @Override
  public boolean isEmpty()  {
    String text = getText();
    return text == null || text.length() == 0;
  }

  @Override
  public void setStartEditLeftmost (boolean startEditLeftmost)  {
    this.startEditLeftmost = startEditLeftmost;
  }

  @Override
  public boolean isStartEditLeftmost()  {
    return startEditLeftmost;
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
  public void setSmartValueEntered(boolean smartValueEntered) {
    this.smartValueEntered = smartValueEntered;
  }

  @Override
  public boolean isSmartValueEntered() {
    return smartValueEntered;
  }


  /**
   * register actions.
   * We make some important changes to default behaviour here:
   *
   * 1. TAB will *not* insert a tab as in default swing.
   *    Instead it will transfer focus. This allows the user to tab
   *    through all fields in a mask without accidently inserting tabs
   *    when tabbing into a JTextArea.
   *
   * 2. Instead, Ctrl-TAB will insert a tab. (is there any application
   *    using tabs in JTextAreas, seriously??)
   *
   * 3. Shift-TAB will transfer the focus to the previous field as expected.
   *    The default swing does nothing.
   *
   * 4. ENTER will transfer the focus to the next field if it is
   *    the first key pressed in this JTextArea (and the cursor hasn't
   *    been moved by the mouse). Again, this allows the user to ENTER
   *    through all fields in a mask without worrying about textareas.
   *    In all other cases ENTER inserts a newline and moves the cursor
   *    after the newline (as expected).
   *
   * 5. Shift-ENTER moves to the previous field (as expected)
   *
   * 6. Ctrl-ENTER inserts a newline at the current caret position but
   *    leaves the cursor where it was.
   *
   * Notice: if the FormTextArea is used as a TableCellEditor, ENTER will transfer focus
   * and Ctrl-ENTER will insert a newline and move the cursor *after* the newline.
   */
  private void setupActions () {

    // default keys
    FormUtilities.getInstance().setupDefaultBindings(this);

    // insert tab
    Action tabAction = new AbstractAction ("insertTab") {
      @Override
      public void actionPerformed (ActionEvent e)  {
        insert("\t", getCaretPosition());
      }
    };
    getActionMap().put(tabAction.getValue(Action.NAME), tabAction);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, Event.CTRL_MASK),
                           tabAction.getValue(Action.NAME));


    // insert newline and keep caret position
    Action newlineAction = new AbstractAction ("insertNewline") {
      @Override
      public void actionPerformed (ActionEvent e)  {
        int pos = getCaretPosition();
        insert("\n", pos);
        if (smartEnter)  {
          setCaretPosition(pos);
        }
      }
    };
    getActionMap().put(newlineAction.getValue(Action.NAME), newlineAction);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Event.CTRL_MASK),
                           newlineAction.getValue(Action.NAME));
  }




  @Override
  public String getValueShown() {
    return super.getText();
  }

  @Override
  public boolean isValueShownModified() {
    return !Objects.equals(oldValueShown, getValueShown());
  }

  @Override
  public void clearValueShownModified() {
    oldValueShown = getValueShown();
  }


  /**
   * handle focus gained
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
    if (isAutoUpdate()) {
      int caretPos = getCaretPosition();    // remember caret-position
      // update the fields display
      fireValueChanged();
      // after setText(), the caret is at the left side of the field.
      // Move to the right if we came down with VK_DOWN.
      if (focusGainedFromTransfer && !wasEnter && !startEditLeftmost) {
        setCaretRight();
      }
      else if (focusGainedFromTransferBackward || wasEnter) {
        setCaretLeft();
      }
      else {
        // set to position marked by mouse (aligned for sure)
        if (caretPos > getDocument().getLength()) {
          caretPos = getDocument().getLength() - 1;
        }
        if (caretPos < 0) {
          caretPos = 0;
        }
        setCaretPosition(caretPos);
      }
    }
    // select all if autoselect enabled
    if (isAutoSelect() && !inhibitAutoSelect)   {
      selectAll();
    }
    inhibitAutoSelect = false;
    formWrapWindow = null;
    transferFocusByEnter = false;
    showTooltip(super.getToolTipText());
    oldValueShown = getValueShown();
  }


  /**
   * handle focus lost
   */
  private void performFocusLost ()  {
    transferFocusByEnter = lastKeyEvent != null &&
                           (lastKeyEvent.getKeyCode() == KeyEvent.VK_ENTER || lastKeyEvent.getKeyCode() == KeyEvent.VK_TAB) &&
                           (lastKeyEvent.getModifiers() == 0 || lastKeyEvent.getModifiers() == KeyEvent.SHIFT_MASK);
    if (isAutoUpdate() && isChangeable()) {
      if (isCellEditorUsage() || !isSmartValueEntered() || isValueShownModified()) {
        // update data field and show what has been read
        fireValueEntered();
      }
      // run the optional validation via binding
      FormUtilities.getInstance().doValidate(this);
    }
    setEraseFirst(false);
    performWrapEvent();
    showTooltip(null);
    lastKeyEvent = null;
  }


  /**
   * handle form wrap
   */
  private void performWrapEvent() {
    if (formWrapWindow != null)  {
      formWrapWindow.fireFormWrappedFocus(new FormWrapEvent(this));
      formWrapWindow = null;
    }
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
   * gets the tooltip diaplay
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

}
