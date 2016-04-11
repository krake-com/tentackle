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
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import javax.swing.JPanel;
import org.tentackle.swing.bind.FormComponentBinder;




/**
 * Extended {@code JPanel} implementing {@code FormContainer}.
 *
 * @author harald
 */

@SuppressWarnings("serial")
public class FormPanel extends JPanel implements FormContainer, ContainerListener {

  private String helpURL;                             // default help for this container
  private String  title;                              // title is null by default
  private boolean honourChangeable = true;            // honour the changeable-request
  private boolean triggerValuesChangedEnabled = true; // true if trigger value changed is enabled
  private boolean changeable = true;                  // last operation from setChangeable()
  private Window parentWindow;                        // the (cached) parent window
  private boolean parentWindowLoaded;                 // true if parent window cached
  private boolean bindable = true;                    // true if bindable
  private boolean autoUpdate = true;                  // true if autoupdate model
  private FormComponentBinder binder;                 // Binding implementation
  private PropertyGroup propertyGroup;                // the property group, null = none


  /**
   * Creates a new FormPanel with the specified layout manager and buffering
   * strategy.
   *
   * @param layout the LayoutManager to use
   * @param doubleBuffered true for double-buffering, which
   *        uses additional memory space to achieve fast, flicker-free
   *        updates (default)
   */
  public FormPanel(LayoutManager layout, boolean doubleBuffered) {
    super(layout, doubleBuffered);
    FormUtilities.getInstance().setDefaultFocusTraversalPolicy(this);    // set traversal policy
    addContainerListener(this);
  }

  /**
   * Creates a new FormPanel with the default FlowLayout manager
   * and buffering strategy.
   *
   * @param doubleBuffered true for double-buffering, which
   *        uses additional memory space to achieve fast, flicker-free
   *        updates (default)
   */
  public FormPanel(boolean doubleBuffered) {
    this (new FlowLayout(), doubleBuffered);
  }

  /**
   * Creates a new FormPanel with the specified layout manager and
   * double buffering.
   *
   * @param layout the LayoutManager to use
   */
  public FormPanel(LayoutManager layout) {
    this(layout, true);
  }

  /**
   * Creates a new FormPanel with the default FlowLayout manager and
   * double buffering.
   */
  public FormPanel()  {
    this(true);
  }


  /**
   * Sets the property group for this component.
   *
   * @param group the property group
   */
  public void setPropertyGroup(PropertyGroup group) {
    if (this.propertyGroup != group) {
      if (this.propertyGroup != null) {
        if (this.propertyGroup.removeComponent(this)) {
          this.propertyGroup = null;
        }
        else  {
          throw new GUIRuntimeException("panel " + this + " not in property group " + this.propertyGroup);
        }
      }
      if (group != null) {
        if (group.addComponent(this)) {
          this.propertyGroup = group;
        }
        else  {
          throw new GUIRuntimeException("panel " + this + " already in property group " + group);
        }
      }
    }
  }

  /**
   * Gets the property group for this component.
   *
   * @return the property group
   */
  public PropertyGroup getPropertyGroup() {
    return propertyGroup;
  }



  /**
   * Adds an ActionListener.<br>
   * The listener can be invoked by the application on demand.
   * Currently there is no events that will trigger fireActionPerformed()
   * automatically. This depends on the application.
   *
   * @param listener the field listener to add
   */
  public synchronized void addActionListener (ActionListener listener) {
     listenerList.add (ActionListener.class, listener);
  }

  /**
   * Removes an ActionListener
   *
   * @param listener the field listener to remove
   */
  public synchronized void removeActionListener (ActionListener listener) {
     listenerList.remove (ActionListener.class, listener);
  }


  /**
   * Invokes all actionListeners for this panel.
   *
   * @param e the action event
   */
  public void fireActionPerformed(ActionEvent e)  {
    if (listenerList != null)  {
      for (ActionListener l: listenerList.getListeners(ActionListener.class)) {
        l.actionPerformed(e);
      }
    }
  }





  // ----------------------- implements ContainerListener --------------------


  @Override
  public void componentAdded(ContainerEvent e)  {
    if (getParent() != null)  {
      // this optimization delays invalidateParentInfo() until the
      // panel is added in a chain that ends at a toplevel-window
      componentRemoved(e);
    }
  }

  @Override
  public void componentRemoved(ContainerEvent e)  {
    // same for add and remove
    Component comp = e.getChild();
    if (comp instanceof FormComponent)  {
      // tell child that next invocation of getParentWindow() should walk up the tree again
      ((FormComponent)comp).invalidateParentInfo();
    }
    else {
      FormUtilities.getInstance().invalidateParentInfo(comp);
    }
  }



  // -------------------- implements FormContainer ---------------------------


  @Override
  public Window getParentWindow() {
    if (!parentWindowLoaded) {
      parentWindow = FormUtilities.getInstance().getParentWindow(this);
      parentWindowLoaded = true;
    }
    return parentWindow;
  }

  @Override
  public void invalidateParentInfo()  {
    parentWindowLoaded = false;
    FormUtilities.getInstance().invalidateParentInfo(this);
  }

  @Override
  public void setAutoUpdate(boolean autoupdate) {
    this.autoUpdate = autoupdate;
    FormUtilities.getInstance().setAutoUpdate(this, autoUpdate);
  }

  @Override
  public boolean isAutoUpdate() {
    return autoUpdate;
  }

  @Override
  public void setFormValues ()  {
    if (binder != null) {
      binder.requestMandatoryUpdate();
      binder.requestChangeableUpdate();
    }
    Window p = getParentWindow();
    boolean keepChanged = false;
    if (p instanceof FormWindow)  {
      keepChanged = ((FormWindow)p).getKeepChangedValues();
    }
    if (keepChanged) {
      FormUtilities.getInstance().setFormValueKeepChanged(this);
    }
    else {
      FormUtilities.getInstance().setFormValue(this);
    }
  }


  @Override
  public void setFormValuesKeepChanged ()  {
    if (binder != null) {
      binder.requestMandatoryUpdate();
      binder.requestChangeableUpdate();
    }
    FormUtilities.getInstance().setFormValueKeepChanged (this);
  }


  @Override
  public void getFormValues ()  {
    FormUtilities.getInstance().getFormValue (this);
  }


  @Override
  public void saveValues()  {
    FormUtilities.getInstance().saveValue(this);
  }


  @Override
  public void setTriggerValuesChangedEnabled(boolean enabled) {
    this.triggerValuesChangedEnabled = enabled;
  }

  @Override
  public boolean isTriggerValuesChangedEnabled() {
    return triggerValuesChangedEnabled;
  }


  @Override
  public boolean areValuesChanged() {
    return FormUtilities.getInstance().isValueChanged(this, true);
  }


  @Override
  public void triggerValuesChanged()  {
    // no action
  }


  @Override
  public void setChangeable (boolean flag) {
    if (isHonourChangeable())  {
      FormUtilities.getInstance().setChangeable (this, flag);
      changeable = flag;
    }
  }

  @Override
  public boolean isChangeable()  {
    return changeable;
  }

  @Override
  public void setHonourChangeable(boolean flag) {
    this.honourChangeable = flag;
  }

  @Override
  public boolean isHonourChangeable() {
    return this.honourChangeable;
  }


  @Override
  public String getTitle() {
    return title;
  }

  @Override
  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public void setHelpURL(String helpURL)  {
    this.helpURL = helpURL;
  }

  @Override
  public String getHelpURL() {
    return helpURL;
  }

  @Override
  public void showHelp() {
    FormUtilities.getInstance().openHelpURL(this);
  }


  // --------------- binding delegation -----------------

  /**
   * Creates a binder for this form.<br>
   * The default implementation invokes
   * {@code FormBindingFactory.createFormComponentBinder(this)}.
   *
   * @return the binder
   */
  protected FormComponentBinder createBinder() {
    return FormUtilities.getInstance().getBindingFactory().createFormComponentBinder(this);
  }


  @Override
  public FormComponentBinder getBinder() {
    if (binder == null) {
      binder = createBinder();
    }
    return binder;
  }

  @Override
  public void setBindable(boolean bindable) {
    this.bindable = bindable;
  }

  @Override
  public boolean isBindable() {
    return bindable;
  }

}
