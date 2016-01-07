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

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.event.EventListenerList;
import org.tentackle.locale.I18nTranslatable;
import org.tentackle.locale.I18nTranslation;
import org.tentackle.swing.bind.FormComponentBinder;



/**
 * Extended {@code JFrame} implementing {@code FormWindow} (and {@code FormContainer}).
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormFrame extends JFrame implements FormWindow, I18nTranslatable, ContainerListener {

  private String helpURL;                             // default help for this container
  private TooltipDisplay tooltipDisplay;              // to automatically display tooltips
  private boolean honourChangeable = true;            // honour the changeable-request
  private boolean changeable = true;                  // last operation from setChangeable()
  private boolean triggerValuesChangedEnabled = true; // true if trigger value changed is enabled
  private boolean autoPosition;                       // automatically compute position on screen
  private EventListenerList listeners;                // special listeners (not managed by AWT)
  private int uiVersion = 0;                          // to track LookAndFeel changes
  private boolean keepChanged;                        // keep changed values?
  private long autoClose = 0;                         // timeout to close in seconds
  private long lastValuesChanged;                     // time of last change
  private FormWindow relatedWindow;                   // related (owner) window
  private Window parentWindow;                        // the (cached) parent window
  private boolean parentWindowLoaded;                 // true if parent window cached
  private boolean bindable = true;                    // true if bindable
  private boolean autoUpdate = true;                  // true if autoupdate model
  private FormComponentBinder binder;                 // Binding implementation
  private I18nTranslation i18nTranslation;            // the I18N translated title


  /**
   * Creates a new, initially invisible <code>FormFrame</code> with the
   * specified title.
   *
   * @param title the title for the frame, null if no title
   * @see JFrame#JFrame(java.lang.String)
   */
  public FormFrame (String title) {
    // JFrame does not explicitly allow a null title (?)
    super(title == null ? "" : title);
    // setup
    FormUtilities.getInstance().setDefaultFocusTraversalPolicy(this);    // set traversal policy
    FormUtilities.getInstance().setUIVersionOfFormWindow(this);
    enableEvents(AWTEvent.WINDOW_EVENT_MASK);
    getContentPane().addContainerListener(this);
    setAutoClose(FormUtilities.getInstance().getAutoClose());
  }

  /**
   * Constructs a new <code>FormFrame</code> that is initially invisible
   * without at title.
   * @see JFrame#JFrame()
   */
  public FormFrame () {
    this(null);
  }


  /**
   * Sets the translated title.<br>
   * This method is used by I18n-Wizards in IDEs to set the original text,
   * the locale and the translated text.
   *
   * @param i18nTranslation the translated text, null if none
   */
  public void setTitle(I18nTranslation i18nTranslation) {
    setI18nTranslation(i18nTranslation);
  }

  @Override
  public void setI18nTranslation(I18nTranslation i18nTranslation) {
    this.i18nTranslation = i18nTranslation;
    setTitle(i18nTranslation == null ? null : i18nTranslation.getText());
  }

  @Override
  public I18nTranslation getI18nTranslation() {
    return i18nTranslation;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to allow FormUtilities keeping track of windows
   */
  @Override
  protected void processWindowEvent(WindowEvent e)  {
    FormUtilities.getInstance().processWindowEvent(e);
    super.processWindowEvent(e);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to autolocate window if possible
   */
  @Override
  public void pack ()  {
    super.pack();
    if (autoPosition) {
      alignLocation();
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden in order to bring modal windows
   * to front in case accidently covered by another window.
   * This is the case whenever a modal dialog is not owned
   * by the window covering it.
   * It solves the problem of "freezing" an application because
   * the user clicked on another window.
   */
  @Override
  public void paint(Graphics g) {
    super.paint(g);
    FormUtilities.getInstance().modalToFront();
  }




  // --------------------- implements ContainerListener -----------------------


  @Override
  public void componentAdded(ContainerEvent e)  {
    Component comp = e.getChild();
    if (comp instanceof FormComponent)  {
      // tell child that next invocation of getParentWindow() should walk up the tree again
      ((FormComponent)comp).invalidateParentInfo();
    }
    else {
      FormUtilities.getInstance().invalidateParentInfo(comp);
    }
  }

  @Override
  public void componentRemoved(ContainerEvent e)  {
    componentAdded(e);    // same code
  }




  // --------------------- implements FormContainer -----------------------


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
    FormUtilities.getInstance().invalidateParentInfo(getContentPane());
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
    if (keepChanged) {
      setFormValuesKeepChanged();
    }
    else {
      FormUtilities.getInstance().setFormValue(getContentPane());
    }
  }

  @Override
  public void setFormValuesKeepChanged ()  {
    if (binder != null) {
      binder.requestMandatoryUpdate();
      binder.requestChangeableUpdate();
    }
    FormUtilities.getInstance().setFormValueKeepChanged (getContentPane());
  }


  @Override
  public void getFormValues ()  {
    FormUtilities.getInstance().getFormValue (getContentPane());
  }


  @Override
  public void saveValues()  {
    FormUtilities.getInstance().saveValue(getContentPane());
  }


  @Override
  public boolean areValuesChanged() {
    return FormUtilities.getInstance().isValueChanged(getContentPane(), true);
  }


  @Override
  public void triggerValuesChanged()  {
    lastValuesChanged = System.currentTimeMillis();
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




  // --------------------- implements FormWindow ------------------------------


  @Override
  public int getUIVersion() {
    return uiVersion;
  }

  @Override
  public void setUIVersion(int version) {
    this.uiVersion = version;
  }

  @Override
  public void setTooltipDisplay(TooltipDisplay display) {
    tooltipDisplay = display;
  }

  @Override
  public TooltipDisplay getTooltipDisplay() {
    return tooltipDisplay;
  }


  @Override
  public void fireFormWrappedFocus(FormWrapEvent evt) {
    if (listeners != null)  {
      Object[] lList = listeners.getListenerList();
      for (int i = lList.length-2; i>=0; i-=2) {
          if (lList[i] == FormWrapListener.class) {
              ((FormWrapListener)lList[i+1]).formWrapped(evt);
          }
      }
    }
  }


  @Override
  public void addFormWrapListener(FormWrapListener l) {
    if (listeners == null)  {
      listeners = new EventListenerList();
    }
    listeners.add(FormWrapListener.class, l);
  }

  @Override
  public void removeFormWrapListener(FormWrapListener l) {
    if (listeners != null)  {
      listeners.remove(FormWrapListener.class, l);
    }
  }


  @Override
  public void setAutoPosition(boolean flag) {
    this.autoPosition = flag;
  }

  @Override
  public boolean isAutoPosition()  {
    return autoPosition;
  }

  /**
   * {@inheritDoc}
   * <p>
   * FormFrames never have an owner (except the default shared swing object,
   * which is no FormWindow).
   */
  @Override
  public void setRelatedWindow(FormWindow relatedWindow) {
    this.relatedWindow = relatedWindow;
  }

  /**
   * {@inheritDoc}
   * <p>
   * FormFrames never have an owner (except the default shared swing object,
   * which is no FormWindow).
   */
  @Override
  public FormWindow getRelatedWindow() {
    return relatedWindow;
  }


  @Override
  public void alignLocation() {
    Point location;           // new location
    if (isShowing()) {
      // check if position is still ok
      location = FormUtilities.getInstance().getAlignedLocation(this, this.getLocation());   // align location if necessary
    }
    else  {
      // initial align
      location = FormUtilities.getInstance().getPreferredLocation(this, getOwner());         // position to preferred location
    }
    if (getLocation().equals(location) == false)  {
      setLocation (location);   // set new location
    }
  }


  @Override
  public void setKeepChangedValues(boolean keepChanged)  {
    this.keepChanged = keepChanged;
  }

  @Override
  public boolean getKeepChangedValues() {
    return keepChanged;
  }


  @Override
  public long getTimeOfLastValuesChanged() {
    return lastValuesChanged;
  }

  @Override
  public void setTimeOfLastValuesChanged(long millis) {
    lastValuesChanged = millis;
  }


  @Override
  public void setAutoClose(long autoClose)  {
    this.autoClose = autoClose;
  }

  @Override
  public long getAutoClose()  {
    return autoClose;
  }

  @Override
  public boolean isAutoCloseable() {
    return autoClose > 0;
  }

  @Override
  public boolean checkAutoClose() {
    return isAutoCloseable() && isVisible() &&
           lastValuesChanged + autoClose < System.currentTimeMillis() &&
           !areValuesChanged();
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
