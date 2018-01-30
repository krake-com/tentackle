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

// Created on December 21, 2004, 7:36 PM

package org.tentackle.swing;

import java.awt.Window;
import javax.swing.JComponent;
import org.tentackle.swing.bind.FormComponentBinding;

/**
 * A FormPanel with an embedded FormComponent.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormComponentPanel extends FormPanel implements FormComponent {

  private FormComponent comp;       // the component to be embedded by the panel


  /**
   * Creates a {@code FormComponentPanel} for a given {@code FormComponent}.
   *
   * @param comp the component
   */
  public FormComponentPanel(FormComponent comp) {
    /**
     * Notice: we're not using setFormComponent here to allow subclasses to
     * override that method and throw an exception if changing the component
     * is not allowed.
     */
    this.comp = comp;
  }

  /**
   * Creates a {@code FormComponentPanel} for a {@link StringFormField}.
   */
  public FormComponentPanel() {
    this(new StringFormField());
  }


  /**
   * Gets the embedded form component.
   *
   * @return the component
   */
  public FormComponent getFormComponent() {
    return comp;
  }

  /**
   * Sets the embedded form field component.
   *
   * @param comp the component.
   */
  public void setFormComponent(FormComponent comp)  {
    this.comp = comp;
  }


  @Override
  public void requestFocus() {
    if (isCellEditorUsage()) {
      ((JComponent) comp).requestFocus();
    }
    else  {
      super.requestFocus();
    }
  }

  @Override
  public boolean requestFocus(boolean temporary) {
    if (isCellEditorUsage()) {
      return ((JComponent) comp).requestFocus(temporary);
    }
    else  {
      return super.requestFocus(temporary);
    }
  }

  @Override
  public boolean requestFocusInWindow() {
    if (isCellEditorUsage()) {
      return ((JComponent) comp).requestFocusInWindow();
    }
    else  {
      return super.requestFocusInWindow();
    }
  }


  // ---------------------- implements FormComponent --------------------


  @Override
  public void setFireRunning(boolean running) {
    comp.setFireRunning(running);
  }

  @Override
  public boolean isFireRunning()  {
    return comp.isFireRunning();
  }

  @Override
  public synchronized void addValueListener (ValueListener l) {
     comp.addValueListener(l);
  }

  @Override
  public synchronized void removeValueListener (ValueListener l) {
     comp.removeValueListener(l);
  }

  @Override
  public void fireValueChanged () {
    comp.fireValueChanged();
  }

  @Override
  public void fireValueEntered () {
    comp.fireValueEntered();
  }

  @Override
  public void setFormValue (Object obj) {
    comp.setFormValue(obj);
  }

  @Override
  public Object getFormValue () {
    return comp.getFormValue();
  }

  @Override
  public void setChangeable (boolean flag)  {
    comp.setChangeable(flag);
  }

  @Override
  public boolean isChangeable() {
    return comp.isChangeable();
  }

  @Override
  public  void  setAutoUpdate (boolean autoupdate)  {
    getFormComponent().setAutoUpdate(autoupdate);
  }

  @Override
  public boolean isAutoUpdate()  {
    return getFormComponent().isAutoUpdate();
  }

  @Override
  public void setHonourChangeable (boolean flag)  {
    comp.setHonourChangeable(flag);
  }

  @Override
  public boolean isHonourChangeable() {
    return comp.isHonourChangeable();
  }

  @Override
  public void updateAllChangeable(boolean flag) {
    comp.updateAllChangeable(flag);
  }

  @Override
  public void requestFocusLater()  {
    comp.requestFocusLater();
  }

  @Override
  public void setFormWrapWindow(FormWindow parent) {
    comp.setFormWrapWindow(parent);
  }

  @Override
  public boolean isValueChanged() {
    return comp.isValueChanged();
  }

  @Override
  public void saveValue() {
    comp.saveValue();
  }

  @Override
  public void triggerValueChanged() {
    comp.triggerValueChanged();
  }

  @Override
  public void setTriggerValueChangedEnabled(boolean enabled) {
    comp.setTriggerValueChangedEnabled(enabled);
  }

  @Override
  public boolean isTriggerValueChangedEnabled() {
    return comp.isTriggerValueChangedEnabled();
  }

  @Override
  public String getHelpURL() {
    return comp.getHelpURL();
  }

  @Override
  public void setHelpURL(String helpURL) {
    comp.setHelpURL(helpURL);
  }

  @Override
  public void showHelp()  {
    comp.showHelp();
  }

  @Override
  public void showErrorPopup(String message) {
    comp.showErrorPopup(message);
  }

  @Override
  public boolean hasFocus() {
    return comp.hasFocus();
  }

  @Override
  public boolean wasTransferFocus() {
    return comp.wasTransferFocus();
  }

  @Override
  public boolean wasTransferFocusBackward() {
    return comp.wasTransferFocusBackward();
  }

  @Override
  public boolean wasFocusGainedFromTransfer()  {
    return comp.wasFocusGainedFromTransfer();
  }

  @Override
  public boolean wasFocusGainedFromTransferBackward()  {
    return comp.wasFocusGainedFromTransferBackward();
  }

  @Override
  public boolean wasTransferFocusByEnter() {
    return comp.wasTransferFocusByEnter();
  }

  @Override
  public Window getParentWindow() {
    return comp.getParentWindow();
  }

  @Override
  public void invalidateParentInfo()  {
    comp.invalidateParentInfo();
  }

  @Override
  public void setCellEditorUsage(boolean flag) {
    comp.setCellEditorUsage(flag);
  }

  @Override
  public boolean isCellEditorUsage() {
    return comp.isCellEditorUsage();
  }

  @Override
  public void prepareFocusLost() {
    comp.prepareFocusLost();
  }

  @Override
  public void setFormTraversable(boolean formTraversable) {
    comp.setFormTraversable(formTraversable);
  }

  @Override
  public boolean isFormTraversable() {
    return comp.isFormTraversable();
  }

  @Override
  public void setFocusTraversalGroup(FocusTraversalGroup group) {
    comp.setFocusTraversalGroup(group);
  }

  @Override
  public FocusTraversalGroup getFocusTraversalGroup() {
    return comp.getFocusTraversalGroup();
  }

  @Override
  public void setPropertyGroup(PropertyGroup group) {
    comp.setPropertyGroup(group);
  }

  @Override
  public PropertyGroup getPropertyGroup() {
    return comp.getPropertyGroup();
  }

  @Override
  public void setMandatory(boolean mandatory) {
    comp.setMandatory(mandatory);
  }

  @Override
  public boolean isMandatory() {
    return comp.isMandatory();
  }

  @Override
  public void setComponentPath(String componentPath) {
    comp.setComponentPath(componentPath);
  }

  @Override
  public String getComponentPath() {
    return comp.getComponentPath();
  }

  @Override
  public void setBindingPath(String bindingPath) {
    comp.setBindingPath(bindingPath);
  }

  @Override
  public String getBindingPath() {
    return comp.getBindingPath();
  }

  @Override
  public void setBinding(FormComponentBinding binding) {
    comp.setBinding(binding);
  }

  @Override
  public FormComponentBinding getBinding() {
    return comp.getBinding();
  }

  @Override
  public void setBindable(boolean bindable) {
    comp.setBindable(bindable);
  }

  @Override
  public boolean isBindable() {
    return comp.isBindable();
  }

  @Override
  public void setSmartValueEntered(boolean smartValueEntered) {
    comp.setSmartValueEntered(smartValueEntered);
  }

  @Override
  public boolean isSmartValueEntered() {
    return comp.isSmartValueEntered();
  }

  @Override
  public Object getValueShown() {
    return comp.getValueShown();
  }

  @Override
  public boolean isValueShownModified() {
    return comp.isValueShownModified();
  }

  @Override
  public void clearValueShownModified() {
    comp.clearValueShownModified();
  }

  @Override
  public int getBaseline(int width, int height) {
    return getFormComponent().getBaseline(width, height);
  }

}
