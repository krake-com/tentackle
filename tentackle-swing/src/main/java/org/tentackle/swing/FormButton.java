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

// Created on September 7, 2002, 2:37 PM

package org.tentackle.swing;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;



/**
 * A Button which is aware of forms.<br>
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormButton extends JButton implements FormChangeableComponent {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(FormButton.class);

  /**
   * This cures some problems and side-effects.
   */
  private static final String TABLECELLEDITOR_PROPERTY = "JButton.isTableCellEditor";

  private String  helpURL;                        // != null for online help
  private boolean clickOnEnter;                   // run doClick() if ENTER pressed (only if formTraversable = true)
  private boolean actionPerformedDelayed = true;  // true = move fireActionPerformed to end of EventQueue (default)
  private boolean formTraversable;                // true = Button is FormFocusPolicy-traversable, i.e. can get Keyboard focus
  private TooltipDisplay tooltipDisplay;          // for tooltips
  private boolean honourChangeable = true;        // true if honour setChangeable
  private boolean changeable = true;              // true if component is changeable
  private boolean allChangeable = true;           // true if container is changeable

  /**
   * Creates a button with no set text or icon.
   */
  public FormButton() {
    super();
  }

  /**
   * Creates a button with an icon.
   *
   * @param icon  the Icon image to display on the button
   */
  public FormButton(Icon icon) {
    super(icon);
  }

  /**
   * Creates a button with text.
   *
   * @param text  the text of the button
   */
  public FormButton(String text) {
    super(text);
  }

  /**
   * Creates a button where properties are taken from the
   * <code>Action</code> supplied.
   *
   * @param a the <code>Action</code> used to specify the new button
   *
   * @since 1.3
   */
  public FormButton(Action a) {
    super(a);
  }

  /**
   * Creates a button with initial text and an icon.
   *
   * @param text  the text of the button
   * @param icon  the Icon image to display on the button
   */
  public FormButton(String text, Icon icon) {
    super(text, icon);
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


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to make sure that actionPerformed is executed
   * *after* focusLost has been processed by the opposite component!
   */
  @Override
  protected void fireActionPerformed(final ActionEvent event) {
    if (isActionPerformedDelayed()) {
      EventQueue.invokeLater(() -> FormButton.super.fireActionPerformed(event));
    }
    else  {
      super.fireActionPerformed(event);
    }
  }



  /**
   * shows the tooltip
   */
  private void showTooltip(String text)  {
    if (formTraversable)  {
      TooltipDisplay td = getTooltipDisplay();
      if (td != null) {
        td.setTooltip(text);
      }
    }
  }



  /**
   * Gets the tooltip display.
   */
  private TooltipDisplay getTooltipDisplay() {
    if (tooltipDisplay == null) {
      Window w = FormUtilities.getInstance().getParentWindow(this);
      if (w instanceof FormWindow) {
        // getParentWindow is fast, because its cached!
        tooltipDisplay = ((FormWindow) w).getTooltipDisplay();
      }
    }
    return tooltipDisplay;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden due to tooltip display
   */
  @Override
  protected void processFocusEvent(FocusEvent e) {
    if (!e.isTemporary()) {
      if (e.getID() == FocusEvent.FOCUS_GAINED) {
        if (!isEnabled()) {
          // dont show tooltip if button is not enabled
          showTooltip(null);
          transferFocus();
        }
        else  {
          showTooltip(getToolTipText());
        }
      }
      else if (e.getID() == FocusEvent.FOCUS_LOST)  {
        showTooltip(null);
      }
    }
    super.processFocusEvent(e);
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden due to navigation by keys
   */
  @Override
  protected void processKeyEvent(KeyEvent e)  {

    boolean isTableCellEditor = isCellEditorUsage();
    int keyCode = e.getKeyCode();

    if (e.getID() == KeyEvent.KEY_PRESSED)  {

      if (isFormTraversable())  {
        if (e.getModifiers() == 0)  {
          switch (keyCode) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_LEFT:
              if (!isTableCellEditor) {
                transferFocusBackward();
                e.consume();
              }
              break;

            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_RIGHT:
              if (!isTableCellEditor) {
                transferFocus();
                e.consume();
              }
              break;

            case KeyEvent.VK_ENTER:
              if (isClickOnEnter()) {
                doClick();
              }
              else  {
                if (!isTableCellEditor) {
                  transferFocus();
                  e.consume();
                }
              }
              break;
          }
        }
        else  {
          switch (keyCode) {
            case KeyEvent.VK_ENTER:
              if (e.isShiftDown()) {
                if (!isTableCellEditor) {
                  transferFocusBackward();
                  e.consume();
                }
              }
              break;
          }
        }
      }

      // help key always works
      switch (keyCode) {
        case KeyEvent.VK_HELP:
        case KeyEvent.VK_F1:
          e.consume();
          FormUtilities.getInstance().openHelpURL(this);
          break;
      }
    }

    if (isTableCellEditor && (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_TAB)) {
      Component p = getParent();
      while (p != null) {
        if (p instanceof FormTable) {
          p.dispatchEvent(new KeyEvent(p, e.getID(),
                                       e.getWhen(), e.getModifiers(),
                                       e.getKeyCode(), e.getKeyChar(),
                                       e.getKeyLocation()));
          p = null;
        }
        else {
          p = p.getParent();
        }
      }
      e.consume();
    }
    else  {
      super.processKeyEvent(e);
    }

  }




  /**
   * Sets the behaviour when user presses the Enter-Key if button has focus.
   *
   * @param clickOnEnter true if pressing the VK_ENTER performs a click, false if transfer focus (default)
   */
  public void setClickOnEnter(boolean clickOnEnter) {
    this.clickOnEnter = clickOnEnter;
  }

  /**
   * Gets the behaviour when user presses the Enter-Key
   *
   * @return true if pressing the VK_ENTER performs a click, false if transfer focus
   */
  public boolean isClickOnEnter() {
    return clickOnEnter;
  }



  /**
   * Gets the help url.
   * @return the help url
   */
  public String getHelpURL() {
    return helpURL;
  }

  /**
   * Sets the help url.
   *
   * @param helpURL the help url
   */
  public void setHelpURL(String helpURL) {
    this.helpURL = helpURL;
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
   * Returns whether this button is form traversable.<br>
   *
   * @return true if traversable, false if not (default)
   */
  public boolean isFormTraversable() {
    return formTraversable;
  }

  /**
   * Sets whether this button is form traversable.<br>
   * If the button is traversable it will receive focus according
   * to the keyboard focus policy.
   *
   * @param formTraversable true if traversable, false if not (default)
   * @see FormFocusTraversalPolicy
   */
  public void setFormTraversable(boolean formTraversable) {
    this.formTraversable = formTraversable;
  }



  /**
   * Sets a flag that this button is used as a cell editor.
   *
   * @param flag true if cell editor
   */
  public void setCellEditorUsage(boolean flag) {
      putClientProperty(TABLECELLEDITOR_PROPERTY, flag);
  }

  /**
   * Returns whether this button is used as a cell editor.
   *
   * @return true if cell editor
   */
  public boolean isCellEditorUsage() {
    try {
      Object obj = getClientProperty(TABLECELLEDITOR_PROPERTY);
      if (obj != null) {
        return (Boolean) obj;
      }
    }
    catch (Exception ex) {
      LOGGER.severe("property " + TABLECELLEDITOR_PROPERTY + " not Boolean! " + ex);
    }
    return false;
  }

}
