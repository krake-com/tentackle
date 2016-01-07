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


// Created on January 4, 2003, 9:07 PM

package org.tentackle.swing;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.ApplicationException;
import org.tentackle.misc.Toolkit;
import org.tentackle.reflect.ReflectionHelper;



/**
 * An Error Dialog.<br>
 * Replacement for JOptionDialog providing multiline messages (without
 * HTML-hacks) and improved keyboard handling.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormError extends FormDialog {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(FormError.class);

  private Component messageComp;      // the component showing the message


  /**
   * Creates an error dialog
   * with a default message component.
   */
  public FormError() {
    initComponents();
    messageComp = messagePanel;
  }


  /**
   * Shows the modal error dialog and waits for user's "ok".
   *
   * @param message the message to display (may contain newlines).
   * @param title the window title, null for default title
   * @param enableClipboardOption  true to enable transfer of error message to system clipboard
   */
  public void showDialog(String message, String title, boolean enableClipboardOption) {

    if (enableClipboardOption) {
      copyToClipboard(title, message, null);
    }

    if (message != null) {
      messageField.setText(message);
      messageField.setSize(messageField.getOptimalSize());
    }

    if (title != null) {
      setTitle(title);
    }

    pack();
    Toolkit.beep();  // beep
    setVisible(true);
  }

  /**
   * Shows the modal error dialog and waits for user's "ok".
   *
   * @param message the message to display (may contain newlines).
   * @param title the window title, null for default title
   */
  public void showDialog(String message, String title) {
    showDialog(message, title, false);
  }

  /**
   * Shows the modal error dialog and waits for user's "ok".
   * The message component must have been set before.
   */
  public void showDialog() {
    showDialog(null, null);
  }

  /**
   * Sets the message component.<br>
   * Replaces the center panel with some other component.
   *
   * @param comp the message component
   */
  public void setMessageComponent(Component comp)  {
    getContentPane().remove(messageComp);
    messageComp = comp;
    getContentPane().add(messageComp, java.awt.BorderLayout.CENTER);
  }

  /**
   * Gets the message component
   * @return the component
   */
  public Component getMessageComponent()  {
    return messageComp;
  }



  /**
   * Copies error infomation into the system clipboard.
   * @param errorTitle      The error title
   * @param errorMessage    The error message
   * @param t               The Throwable instance
   */
  protected void copyToClipboard(final String errorTitle, final String errorMessage, final Throwable t) {
    try {
      final String newLine = System.getProperty("line.separator");
      final Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
      StringWriter writer = new StringWriter();

      if (errorTitle != null) {
        writer.append(errorTitle).append(newLine);
      }
      if (errorMessage != null) {
        writer.append(errorMessage).append(newLine);
      }
      if (t != null) {
        final StackTraceElement[] stArr = t.getStackTrace();
        for (final StackTraceElement element : stArr) {
          writer.append(element.toString()).append(newLine);
        }
      }

      final StringSelection clipboardData = new StringSelection(writer.toString());
      clipboard.setContents(clipboardData, clipboardData);
    }
    catch (HeadlessException e) {
      LOGGER.logStacktrace(e);
    }
  }




  /**
   * Creates an error dialog, shows a message and waits for user's ok.
   *
   * @param message the error message
   * @param abort true if abort application
   * @param logger the logger, null if none
   * @param enableClipboardOption  true to enable transfer of error message to system clipboard
   */
  static public void show(String message, boolean abort, Logger logger, boolean enableClipboardOption) {

    if (message == null) {
      message = ReflectionHelper.getApplicationInvocationAsString();
    }

    if (logger != null) {
      if (abort) {
        logger.severe(message);
      }
      else {
        logger.warning(message);
      }
    }
    FormUtilities.getInstance().getEventQueue().dropKeyEvents();  // mintime for user to read

    new FormError().showDialog(message, null, enableClipboardOption);

    if (abort) {
      abort(logger);
    }
  }


  /**
   * Creates an error dialog, shows a message and waits for user's ok.
   *
   * @param message the error message
   * @param abort true if abort application
   * @param logger the logger, null if none
   */
  static public void show(String message, boolean abort, Logger logger) {
    FormError.show(message, abort, logger, false);
  }


  /**
   * Creates an error dialog, shows a message and waits for user's ok.
   * The default logger is used.
   *
   * @param message the error message
   * @param abort true if abort application
   */
  static public void show (String message, boolean abort)  {
    FormError.show(message, abort, LOGGER);
  }

  /**
   * Creates an error dialog, shows a message and waits for user's ok.
   * The default logger is used. No abort.
   *
   * @param message the error message
   */
  static public void show(String message)  {
    FormError.show (message, false);
  }




  /**
   * Shows an exception dialog.<br>
   * The method checks for headless and does not show a dialog, just logs,
   * as apps sometimes contain shared error handling code between GUI- and daemons.
   *
   * @param message the error message, null if {@link ReflectionHelper#getApplicationInvocationAsString()}.
   * @param ex the exception, null if none
   * @param abort true if abort application
   * @param logger the logger
   */
  static public void showException(String message, Throwable ex, boolean abort, Logger logger) {

    if (message == null) {
      message = ReflectionHelper.getApplicationInvocationAsString();
    }

    // show some meaningful message to the user
    String extraMsg = null;
    if (ex instanceof ApplicationException)  {
      extraMsg = ((ApplicationException) ex).getAllMessages();
    }
    else if (ex != null) {
      extraMsg = ex.getMessage();
    }

    if (extraMsg != null) {
      message += "\n" + extraMsg;
    }

    if (!GraphicsEnvironment.isHeadless()) {
      // show some meaningful message to the user
      show(message);
    }

    if (logger != null) {
      logger.log(abort ? Level.SEVERE : Level.WARNING, message, ex);
    }

    if (abort)  {
      abort(logger);
    }
  }







  /**
   * Prints an exception using the default logger.<br>
   *
   * @param message the error message, null if {@link ReflectionHelper#getApplicationInvocationAsString()}
   * @param ex the exception, null if none
   * @param abort true if abort application
   */
  static public void showException (String message, Throwable ex, boolean abort)  {
    FormError.showException(message, ex, abort, LOGGER);
  }

  /**
   * Prints an exception using the default logger, no abort<br>
   *
   * @param message the error message, null if {@link ReflectionHelper#getApplicationInvocationAsString()}
   * @param ex the exception, null if none
   */
  static public void showException (String message, Throwable ex) {
    FormError.showException (message, ex, false);
  }

  /**
   * Prints an exception using the default logger, default message.<br>
   *
   * @param ex the exception, null if none
   * @param abort true if abort application
   */
  static public void showException (Throwable ex, boolean abort) {
    FormError.showException(ex.getClass().getName(), ex, abort);
  }

  /**
   * Prints an exception using the default logger, no abort, default message<br>
   *
   * @param ex the exception, null if none
   */
  static public void showException (Throwable ex) {
    FormError.showException (ex, false);
  }




  private static void abort(Logger logger)  {
    if (logger != null) {
      // print stacktrace to logger
      CharArrayWriter writer = new CharArrayWriter();
      new Exception("Application Aborted! Stacktrace:").printStackTrace(new PrintWriter(writer));
      logger.severe(writer.toString());
    }
    throw new GUIRuntimeException("FormError");
  }


  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    messagePanel = new javax.swing.JPanel();
    iconLabel = new javax.swing.JLabel();
    messageField = new org.tentackle.swing.FormTextArea();
    buttonPanel = new javax.swing.JPanel();
    okButton = new org.tentackle.swing.FormButton();

    setAutoPosition(true);
    setTitle(SwingSwingBundle.getTranslation("ERROR")); // NOI18N
    setModal(true);

    messagePanel.setLayout(new java.awt.GridBagLayout());

    iconLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    iconLabel.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("ErrorDialog"));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    messagePanel.add(iconLabel, gridBagConstraints);

    messageField.setEditable(false);
    messageField.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    messageField.setLineWrap(true);
    messageField.setWrapStyleWord(true);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    messagePanel.add(messageField, gridBagConstraints);

    getContentPane().add(messagePanel, java.awt.BorderLayout.CENTER);

    okButton.setClickOnEnter(true);
    okButton.setFormTraversable(true);
    okButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("ok"));
    okButton.setName("ok"); // NOI18N
    okButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        okButtonActionPerformed(evt);
      }
    });
    buttonPanel.add(okButton);

    getContentPane().add(buttonPanel, java.awt.BorderLayout.SOUTH);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
    dispose();
  }//GEN-LAST:event_okButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel buttonPanel;
  private javax.swing.JLabel iconLabel;
  private org.tentackle.swing.FormTextArea messageField;
  private javax.swing.JPanel messagePanel;
  private org.tentackle.swing.FormButton okButton;
  // End of variables declaration//GEN-END:variables

}
