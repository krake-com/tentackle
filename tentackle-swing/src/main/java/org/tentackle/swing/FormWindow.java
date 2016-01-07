/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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


/**
 * All Tentackle windows (top level container) must implement this interface.<br>
 * Adds features like auto positioning, related window, form wrapping,
 * autoclose, etc...
 * 
 * @author  harald
 */
public interface FormWindow extends FormContainer {
  
  /**
   * Sets the related FormWindow.<br>
   * Related windows are somewhat similar to Owners, but
   * they don't change fg/bg-behaviour of the window-manager.
   * In Swing you can't assign an Owner without forcing its childs
   * to always stay in front of the owning window.
   * Furthermore, some windows don't support Owners, such as Frames.
   * The related window is just a link that will default to
   * the owner but can be set to any other window.
   *
   * @param relatedWindow the related FormWindow
   */
  public void setRelatedWindow(FormWindow relatedWindow);
  
  /**
   * Gets the related window.<br>
   * Falls back to getOwner() if not set and owner is a formwindow.
   *
   * @return the related window, the owner of null if not a FormWindow.
   */
  public FormWindow getRelatedWindow();
  
  
  /**
   * Sets the autoposition feature.<br>
   * Windows with this autoposition enabled will place themselves
   * in a nice manner on the screen when displayed initially.
   * 
   * @param autoPosition true to enable autoposition, false if not (default) 
   */
  public void setAutoPosition(boolean autoPosition);

  /**
   * Returns whether auto position is enabled.
   * 
   * @return true if autoposition is enabled
   */
  public boolean isAutoPosition();

  
  /** 
   * Adss a wrap listener which will be invoked
   * whenever the focus wraps in this window, i.e. from the logically
   * last field to the logically first.
   *
   * @param listener the form wrap listener
   * @see FormFocusTraversalPolicy
   */
  public void addFormWrapListener(FormWrapListener listener);
  
  /**
   * Removes a form wrap listener.
   * 
   * @param listener the listener to remove
   */
  public void removeFormWrapListener(FormWrapListener listener);
  
  /** 
   * Fires all form wrap listeners for this window.
   * 
   * @param evt the form wrap event 
   */
  public void fireFormWrappedFocus(FormWrapEvent evt);
  
  
  /**
   * Sets a version number for the current look and feel of this window.
   * Allows to track LookAndFeel changes and automatically update UI if changed.
   * @param version the n-th look and feel version
   */
  public void setUIVersion(int version);
  
  /**
   * Gets the look and feel version for this window.
   * 
   * @return the look and feel version
   */
  public int getUIVersion();
  
  
  /**
   * Sets the tooltip display for this window.<br>
   * If a tooltip display is set the component's tooltips will
   * be displayed for each component receiving the keyboard focus
   * (instead of popping up on mouse hover).
   * 
   * @param tooltipDisplay the tooltip display, null = none (default)
   */
  public void setTooltipDisplay(TooltipDisplay tooltipDisplay);
  
  /**
   * Gets the tooltip display for this window.
   * 
   * @return the tooltip display, null = none (default)
   */
  public TooltipDisplay getTooltipDisplay();
  

  /**
   * Sets the behaviour of setFormValues() of FormContainers in this FormWindow. 
   * 
   * @param keepChanged true if setFormValues() should NOT override values
   *        changed by the user. The default is false.
   */
  public void setKeepChangedValues(boolean keepChanged);
  
  /**
   * Gets the behaviour for setFormValues().
   * 
   * @return true if keep changed values
   */
  public boolean getKeepChangedValues();

  
  /**
   * Aligns the location of this window in order not
   * to cover other windows (best as possible).
   */
  public void alignLocation();
  
  
  /**
   * Set a timer to close the window automatically.<br>
   * FormContainers use this feature to close after a certain
   * time of inactivity and unchanged data.<br>
   * Note: autoClosing must be set before displaying the window (just like setModal()).
   * 
   * @param timeout the time in milliseconds, 0 = no autoclose
   */
  public void setAutoClose(long timeout);
  
  /**
   * Gets the autoclose timeout.
   * 
   * @return the time in milliseconds, 0 = no autoclose
   */
  public long getAutoClose();
  
  /**
   * Returns whether this window is a candidate for autoClose-monitoring.
   * The method is invoked when displaying the window.
   *
   * @return true if window should be monitored by the autoclose-thread
   */
  public boolean isAutoCloseable();
  
  /**
   * Checks if window should be autoclosed.
   * 
   * @return true if window can safely be closed due to autoclosing-rules.
   */
  public boolean checkAutoClose();
  
  
  /**
   * Sets the system-time of last setFormValues() or triggerValuesChanged() 
   * for this window in milliseconds.
   * 
   * @param millis the time of last values changed
   */
  public void setTimeOfLastValuesChanged(long millis);
  
  /**
   * Gets the system-time of last setFormValues() or triggerValuesChanged() 
   * for this window in milliseconds.
   * 
   * @return the time of last values changed
   */
  public long getTimeOfLastValuesChanged();
  
}
