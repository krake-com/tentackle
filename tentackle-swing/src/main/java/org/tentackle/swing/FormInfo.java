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
import java.awt.EventQueue;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.Toolkit;



/**
 * An Info Dialog.<br>
 * Replacement for JOptionDialog providing multiline messages (without
 * HTML-hacks) and improved keyboard handling.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormInfo extends FormDialog {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(FormInfo.class);

  private Component messageComp;        // the component showing the message


  /**
   * Creates an info dialog with a default message component.
   */
  public FormInfo() {
    initComponents();
    messageComp = messagePanel;
  }


  /**
   * Shows the info dialog and waits for user's "ok".
   *
   * @param message the message to display (may contain newlines).
   * @param title the window title, null for default title
   * @param timeout the timeout in milliseconds for the dialog to disappear automatically. Also sets this
   * dialog to non-modal.
   */
  public void showDialog (String message, String title, final long timeout) {

    if (message != null) {
      messageField.setText(message);
      messageField.setSize(messageField.getOptimalSize());
    }

    if (title != null) {
      setTitle(title);
    }

    if (timeout > 0)  {
      setModal(false);
      buttonPanel.setVisible(false);
      new Thread("FormInfoDialog Auto Disposer") {
        @Override
        public void run() {
          try {
            sleep(timeout);
          }
          catch (InterruptedException ex) {
            LOGGER.warning("interrupted -> ignored", ex);
          }
          EventQueue.invokeLater(() -> {
            FormInfo.this.dispose();
          });
        };
      }.start();
    }

    pack();
    Toolkit.beep();  // beep
    setVisible(true);
  }



  /**
   * Shows the modal info dialog and waits for user's "ok".
   * The message component must have been set before.
   */
  public void showDialog() {
    showDialog(null, null, 0);
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
   * Creates an info dialog, shows a message and waits for user's ok.
   *
   * @param message the error message
   * @param logger the logger, null if none
   * @param timeout the timeout in milliseconds for the dialog to disappear automatically. Also sets this
   * dialog to non-modal.
   */
  static public void show(String message, Logger logger, long timeout) {
    if (logger != null)  {
      // log to console
      logger.info(message);
    }
    FormUtilities.getInstance().getEventQueue().dropKeyEvents();    // mintime for user to read
    new FormInfo().showDialog(message, null, timeout);
  }

  /**
   * Creates an info dialog, shows a message and waits for user's ok.
   *
   * @param message the error message
   * @param logger the logger, null if none
   */
  static public void show(String message, Logger logger)  {
    show(message, logger, 0);
  }

  /**
   * Creates an info dialog, shows a message and waits for user's ok.
   *
   * @param message the error message
   * @param log true if log the message via the default logger
   * @param timeout the timeout in milliseconds for the dialog to disappear automatically. Also sets this
   * dialog to non-modal.
   */
  static public void show(String message, boolean log, long timeout) {
    show(message, log ? LOGGER : null, timeout);
  }

  /**
   * Creates an info dialog, shows a message and waits for user's ok.
   *
   * @param message the error message
   * @param log true if log the message via the default logger
   */
  static public void show(String message, boolean log)  {
    show(message, log, 0);
  }

  /**
   * Creates an info dialog, shows a message and waits for user's ok.
   *
   * @param message the error message
   * @param timeout the timeout in milliseconds for the dialog to disappear automatically. Also sets this
   * dialog to non-modal.
   */
  static public void show(String message, long timeout)  {
    show (message, false, timeout);
  }

  /**
   * Creates an info dialog, shows a message and waits for user's ok.
   *
   * @param message the error message
   */
  static public void show(String message) {
    show(message, false);
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
    setTitle(SwingSwingBundle.getTranslation("INFORMATION")); // NOI18N
    setModal(true);

    messagePanel.setLayout(new java.awt.GridBagLayout());

    iconLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    iconLabel.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("InformationDialog"));
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
