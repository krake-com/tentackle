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
import java.awt.EventQueue;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import org.tentackle.swing.bind.FormComponentBinding;



/**
 * A toggle button aware of forms.
 * <p>
 * Notice that unlike the {@link FormButton} a {@link FormToggleButton} implements
 * {@link FormComponent} and behaves essentially the same as a {@link FormCheckBox}
 * or a {@link FormRadioButton}.

 * @author harald
 */
@SuppressWarnings("serial")
public class FormToggleButton extends JToggleButton implements FormComponent, ActionListener {

  // keyboard shortcuts
  private static final String SELECT_CHARS   = SwingSwingBundle.getString("YESKEYS");
  private static final String DESELECT_CHARS = SwingSwingBundle.getString("NOKEYS");


  private boolean autoUpdate = true;                // true if data binding enabled (default)
  private String helpURL;                           // != null for online help
  private FormWindow formWrapWindow;                // window to receive wrap event
  private boolean fireRunning;                      // true if some fire-.. method running
  private boolean savedValue;                       // the saved state
  private Color selectedColor;                      // color for "checked"
  private Color unselectedColor;                    // color for "not checked"
  private KeyEvent lastKeyEvent;                    // last pressed key
  private boolean tableCellEditorUsage;             // true if used as a cell editor
  private boolean skipNextFocusLost;                // true if ignore next focus lost
  private Window parentWindow;                      // parent window (cached)
  private TooltipDisplay tooltipDisplay;            // tooltip display
  private boolean transferFocusDone;                // transfer focus forward done
  private boolean transferFocusBackwardDone;        // transfer focus backward done
  private boolean transferFocusByEnter;             // focus lost due to ENTER
  private boolean focusGainedFromTransfer;          // focus gained by transfer focus forward
  private boolean focusGainedFromTransferBackward;  // focus gained by transfer focus backward
  private boolean formTraversable = true;           // true if checkbox gets keyboard focus
  private boolean actionPerformedDelayed = true;    // true = move fireActionPerformed to end of EventQueue (default)
  private boolean honourChangeable = true;          // honour the changeable-request
  private boolean changeable = true;                // true if component is changeable
  private boolean allChangeable = true;             // true if container is changeable
  private boolean bindable = true;                  // true if bindable
  private String componentPath;                     // if bound: the component's declaration path
  private String bindingPath;                       // the field path to bind, null if autobinding
  private FormComponentBinding binding;             // the current binding, null if none
  private boolean smartValueEntered = true;         // true if fire value entered only if really changed
  private boolean oldValueShown;                    // the string representation to detect whether there really was a change
  private boolean mandatory;                        // true if field is mandatory
  private FocusTraversalGroup focusTraversalGroup;  // the focus traversal group, null = none
  private PropertyGroup propertyGroup;              // the property group, null = none
  private boolean triggerValueChangedEnabled = true;  // true if trigger value changed is enabled


  /**
   * Creates a check box with text and icon,
   * and specifies whether or not it is initially selected.
   *
   * @param text the text of the check box.
   * @param icon  the Icon image to display
   * @param selected a boolean value indicating the initial selection
   *        state. If <code>true</code> the check box is selected
   */
  public FormToggleButton(String text, Icon icon, boolean selected) {
    super (text, icon, selected);
    // listen to action events
    addActionListener(this);
    // setup some handy key-bindings
    FormUtilities.getInstance().setupDefaultBindings(this);
  }

  /**
   * Creates a check box with text
   * and specifies whether or not it is initially selected.
   *
   * @param text the text of the check box.
   * @param selected a boolean value indicating the initial selection
   *        state. If <code>true</code> the check box is selected
   */
  public FormToggleButton (String text, boolean selected)  {
    this (text, null, selected);
  }

  /**
   * Creates a check box with text, unselected.
   *
   * @param text the text of the check box.
   */
  public FormToggleButton (String text) {
    this (text, false);
  }

  /**
   * Creates a check box without text, unselected.
   */
  public FormToggleButton () {
    this (null);
  }


  /**
   * Returns whether a button press should be processed delayed or immediately.
   *
   * @return true if delayed (default), false if immediately (like JButton).
   */
  public boolean isActionPerformedDelayed() {
    return actionPerformedDelayed;
  }

  /**
   * Sets whether a button press should be processed delayed or immediately.
   *
   * @param delayActionPerformed true if delayed (set to end of event queue, default!), false if immediately
   */
  public void setActionPerformedDelayed(boolean delayActionPerformed) {
    this.actionPerformedDelayed = delayActionPerformed;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to make sure that actionPerformed is executed
   * *after* focusLost has been processed by the opposite component!
   */
  @Override
  protected void fireActionPerformed(final ActionEvent event) {
    if (isActionPerformedDelayed()) {
      EventQueue.invokeLater(() -> FormToggleButton.super.fireActionPerformed(event));
    }
    else  {
      super.fireActionPerformed(event);
    }
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
   * Gets the color for "checked".
   * @return the selected color
   */
  public Color getSelectedColor() {
    return selectedColor;
  }

  /**
   * Sets the color when item is selected.<br>
   * If the selected color is set, the deselected color should
   * be set too. If the tentackle-plafs are used,
   * these colors will be used regardless whether the component
   * is enabled or not. This is a nice feature to circumvent
   * the half-intensity display of disabled components.
   *
   * @param selectedColor the selected color, null if default
   */
  public void setSelectedColor(Color selectedColor) {
    this.selectedColor   = selectedColor;
  }


  /**
   * Gets the color for "unchecked".
   * @return the unselected color
   */
  public Color getUnselectedColor() {
    return unselectedColor;
  }

  /**
   * Sets the color when item is unselected.<br>
   * If the unselected color is set, the selected color should
   * be set too. If the tentackle-plafs are used,
   * these colors will be used regardless whether the component
   * is enabled or not. This is a nice feature to circumvent
   * the half-intensity display of disabled components.
   *
   * @param unselectedColor the unselected color, null if default
   */
  public void setUnselectedColor(Color unselectedColor) {
    this.unselectedColor = unselectedColor;
  }


  @Override
  public void setAutoUpdate(boolean autoupdate) {
    this.autoUpdate = autoupdate;
  }

  @Override
  public boolean isAutoUpdate() {
    return autoUpdate;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to show tooltip in tooltipdisplay _or_ via mouse hover
   * but _not_ both.
   */
  @Override
  public String getToolTipText() {
    return getTooltipDisplay() == null ? super.getToolTipText() : null;
  }


  @Override
  public void transferFocus() {
    transferFocusDone = true;
    super.transferFocus();
  }

  @Override
  public void transferFocusBackward() {
    transferFocusBackwardDone = true;
    super.transferFocusBackward();
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to implement keyboard shortcuts.
   */
  @Override
  protected void processKeyEvent(KeyEvent e) {

    if (e.getID() == KeyEvent.KEY_PRESSED)  {

      lastKeyEvent = e;

      if (e.getModifiers() == 0) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_UP:
            e.consume();
            transferFocusBackward();
            return;
          case KeyEvent.VK_DOWN:
            e.consume();
            transferFocus();
            return;
        }
      }

      char key = e.getKeyChar();

      if (key != KeyEvent.CHAR_UNDEFINED) {
        if (DESELECT_CHARS.indexOf(key) >= 0) {
          e.consume();
          setSelected(true);
          doClick();
          return;
        }
        else if (SELECT_CHARS.indexOf(key) >= 0) {
          e.consume();
          setSelected(false);
          doClick();
          return;
        }
      }

      if (e.getKeyCode() == KeyEvent.VK_Z && e.getModifiers() == KeyEvent.CTRL_MASK) {
        // undo
        setSelected(savedValue);
      }

    }

    super.processKeyEvent(e);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden for advanced focus handling
   */
  @Override
  protected void processFocusEvent(FocusEvent e) {
    super.processFocusEvent(e);
    if (!e.isTemporary()) {
      if (e.getID() == FocusEvent.FOCUS_GAINED) {
        performFocusGained(e.getOppositeComponent());
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
  }




  private void performFocusGained (Component opposite)  {
    if (opposite instanceof FormComponent)  {
      focusGainedFromTransfer         = ((FormComponent)opposite).wasTransferFocus();
      focusGainedFromTransferBackward = ((FormComponent)opposite).wasTransferFocusBackward();
    }
    transferFocusDone = false;
    transferFocusBackwardDone = false;
    if (isAutoUpdate()) {
      fireValueChanged();
    }
    formWrapWindow = null;
    showTooltip(super.getToolTipText());
    oldValueShown = getValueShown();
  }

  private void performFocusLost ()  {
    transferFocusByEnter = lastKeyEvent != null &&
                           (lastKeyEvent.getKeyCode() == KeyEvent.VK_ENTER || lastKeyEvent.getKeyCode() == KeyEvent.VK_TAB) &&
                           (lastKeyEvent.getModifiers() == 0 || lastKeyEvent.getModifiers() == KeyEvent.SHIFT_MASK);
    performWrapEvent();
    showTooltip(null);
    lastKeyEvent = null;
  }

  private void performWrapEvent() {
    if (formWrapWindow != null)  {
      formWrapWindow.fireFormWrappedFocus(new FormWrapEvent(this));
      formWrapWindow = null;
    }
  }




  private void showTooltip(String text)  {
    TooltipDisplay td = getTooltipDisplay();
    if (td != null) {
      td.setTooltip(text);
    }
  }


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



  // --------------------------- implements FormComponent ---------------------

  @Override
  public void requestFocusLater() {
    FormUtilities.getInstance().requestFocusLater(this);
  }


  @Override
  public void actionPerformed (ActionEvent evt) {
    if (isAutoUpdate()) {
      fireValueEntered();
      FormUtilities.getInstance().doValidate(this);
    }
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
  }

  @Override
  public void fireValueEntered () {
    FormUtilities.getInstance().doFireValueEntered (this, listenerList.getListenerList());
  }


  /**
   * @param selected a Boolean object, else the checkbox is unselected
   */
  @Override
  public void setFormValue (Object selected)  {
    setSelected (selected instanceof Boolean && (Boolean) selected);
  }

  /**
   * @return either {@link Boolean#TRUE} or {@link Boolean#FALSE}
   */
  @Override
  public Boolean getFormValue ()  {
    return isSelected() ? Boolean.TRUE : Boolean.FALSE;
  }


  @Override
  public void saveValue() {
    if (honourChangeable) {
      savedValue = super.isSelected();
    }
  }

  @Override
  public boolean isValueChanged() {
    if (!honourChangeable) {
      return false;
    }
    boolean value = super.isSelected();
    return value != savedValue;
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
  public void triggerValueChanged() {
    if (triggerValueChangedEnabled) {
      FormUtilities.getInstance().triggerValueChanged(this);
    }
  }


  @Override
  public void setChangeable (boolean changeable) {
    if (isHonourChangeable()) {
      this.changeable = changeable;
      super.setEnabled(changeable && allChangeable);
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
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    this.changeable = enabled;
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
  public void prepareFocusLost()  {
    if (!tableCellEditorUsage) {
      performFocusLost();
      skipNextFocusLost = true;
    }
    // else: triggered by FOCUS_LOST
  }

  @Override
  public void setFormWrapWindow(FormWindow parent)  {
    formWrapWindow = parent;
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


  @Override
  public void setCellEditorUsage(boolean flag) {
    tableCellEditorUsage = flag;
  }

  @Override
  public boolean isCellEditorUsage() {
    return tableCellEditorUsage;
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
  public void setSmartValueEntered(boolean smartValueEntered) {
    this.smartValueEntered = smartValueEntered;
  }

  @Override
  public boolean isSmartValueEntered() {
    return smartValueEntered;
  }

  @Override
  public Boolean getValueShown() {
    return super.isSelected();
  }

  @Override
  public boolean isValueShownModified() {
    return oldValueShown != getValueShown();
  }

  @Override
  public void clearValueShownModified() {
    oldValueShown = getValueShown();
  }

}
