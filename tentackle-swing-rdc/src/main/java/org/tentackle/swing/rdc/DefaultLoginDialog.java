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

// Created on February 17, 2004, 4:40 PM

package org.tentackle.swing.rdc;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.SplashScreen;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPasswordField;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.swing.FormUtilities;
import org.tentackle.swing.StringFormField;

/**
 * The default tentackle login dialog.
 *
 * @author harald
 */
public class DefaultLoginDialog extends org.tentackle.swing.FormDialog implements LoginDialog {

  private static final long serialVersionUID = -1244250738977558564L;

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLoginDialog.class);


  private final SessionInfo sessionInfo;  // returned login information
  private boolean waitLogin;              // true = login info not completed yet
  private boolean ok;                     // true if user pressed ok-Button
  private boolean promptLogin;            // prompt user for login?
  private boolean capsLockInitialized;    // true if caps lock has been determined the first time
  private boolean capsLock;               // true if caps lock is on
  private volatile boolean locked;        // true if sync'd using the monitor object


  private final KeyListener capsLockDetector = new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_CAPS_LOCK) {
          // toggle caps lock
          setCapsLock(!capsLock);
        }
        if (!capsLockInitialized) {
          // first key checks for caps lock
          if (Character.isUpperCase(e.getKeyChar()) && !e.isShiftDown()) {
            setCapsLock(true);
          }
          capsLockInitialized = true;
        }
      }
    };


  /**
   * Creates a login dialog.
   *
   * @param sessionInfo the user info, null if default (see {@link SessionInfo})
   * @param promptLogin true if prompt for username/password, else show only
   * @param logo the application's logo icon, null = tentackle default icon
   */
  public DefaultLoginDialog(SessionInfo sessionInfo, boolean promptLogin, Icon logo) {

    this.sessionInfo = sessionInfo == null ? Pdo.createSessionInfo(null, null, null) : sessionInfo;
    this.promptLogin = promptLogin;

    initComponents();

    if (logo == null) {
      logo = new ImageIcon(DefaultLoginDialog.class.getResource("/org/tentackle/swing/rdc/images/loginLogo.png"));
    }

    // layered pane has no layout manager, so we layout explicitly
    Dimension size = imageLabel.getPreferredSize();
    int width = size.width;
    int height = size.height;
    layeredPane.setPreferredSize(size);
    imageLabel.setBounds(0, 0, width, height);
    loginPanel.setBounds(0, 0, width, height);
    logoPanel.setBounds(0, 0, width, height);
    statusPanel.setBounds(0, 0, width, height);
    statusPanel.setVisible(false);
    int logoWidth = logo.getIconWidth();
    int logoHeight = logo.getIconHeight();
    logoLabel.setIcon(logo);
    logoLabel.setBounds((width/2 - logoWidth)/2, (height - logoHeight)/2, logoWidth, logoHeight);

    // turn caps lock off if it was on
    try {
      Toolkit.getDefaultToolkit().setLockingKeyState(KeyEvent.VK_CAPS_LOCK, false);
    }
    catch (UnsupportedOperationException ex) {
      LOGGER.info("turning off CAPS LOCK not supported for this platform");
    }

    nameField.addKeyListener(capsLockDetector);
    passField.addKeyListener(capsLockDetector);

    setCapsLock(false);
  }

  /**
   * Creates a login dialog.
   * The user will be prompted for username/password if ui or ui.username
   * or ui.password is null.
   *
   * @param ui the user info, null if default (see {@link SessionInfo})
   * @param logo the application's logo icon, null = tentackle default icon
   */
  public DefaultLoginDialog(SessionInfo ui, Icon logo) {
    this(ui, ui == null || ui.getUserName() == null || ui.getPassword() == null, logo);
  }

  /**
   * Creates a login dialog with a default SessionInfo, default Icon and prompts the
   * user for name/password.
   */
  public DefaultLoginDialog() {
    this(null, true, null);
  }



  /**
   * Displays the dialog and prompts for username and password.<br>
   *
   * If the method is invoked from within the GUI thread, the
   * dialog is modal and will dispose after login data entered.
   * Otherwise the dialog remains open and showStatus() can be
   * used to further update the login process.
   *
   * @return  null if user aborts
   */
  @Override
  public SessionInfo getSessionInfo()  {

    locked = !EventQueue.isDispatchThread();

    if (locked) {
      // unblocking...
      EventQueue.invokeLater(this::prepareDialog);

      // wait for data entered or a button pressed
      waitLogin = true;

      synchronized(this) {
        while (waitLogin) {
          try {
            wait();
          }
          catch (InterruptedException ex) {
            // nothing to do
          }
        }
      }
      // login data entered...

      FormUtilities.getInstance().removeWindow(this);    // don't change GUI etc...

      EventQueue.invokeLater(() -> {
        setLoginPanelVisible(false);
        statusPanel.setVisible(true);
      });
    }
    else  {
      prepareDialog();    // wait here for dispose the modal dialog...
    }

    if (ok) {
      char[] pass = passField.getPassword();
      if (pass != null && pass.length == 0) {
        pass = null;
      }
      sessionInfo.setPassword(pass);
      sessionInfo.setUserName(nameField.getText());
      return sessionInfo;
    }

    return null;    // aborted
  }


  /**
   * Shows a message status.
   *
   * @param status the message text
   */
  @Override
  public void showStatus(final String status)  {
    if (locked) {
      EventQueue.invokeLater(() -> setStatus(status));
    }
    else  {
      setStatus(status);
    }
  }


  /**
   * Provides access to the username input field.
   *
   * @return the username field
   */
  public StringFormField getUsernameField() {
    return nameField;
  }


  /**
   * Provides access to the password input field.
   *
   * @return the password field
   */
  public JPasswordField getPasswordField() {
    return passField;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden in case setUndecorated() did not work and/or
   * the window is closed somehow.
   */
  @Override
  protected void processWindowEvent(java.awt.event.WindowEvent e) {
    if (e.getID() == WindowEvent.WINDOW_CLOSING) {
      waitLogin = false;
    }
    super.processWindowEvent(e);    // this will close finally
  }



  private void setStatus(String status) {
    if (!statusPanel.isVisible()) {
      statusPanel.setVisible(true);
    }
    capsLockLabel.setText("");
    statusLabel.setText(status);
  }


  private void setLoginPanelVisible(boolean visible) {
    loginPanel.setVisible(visible);
    cancelStatusButton.setVisible(visible);   // remove 2nd statusbutton if loginpanel is invisible
  }


  private void setCapsLock(boolean capsLock) {
    this.capsLock = capsLock;
    capsLockLabel.setText(capsLock ? RdcSwingRdcBundle.getString("CAPS LOCK!") : "");
    if (capsLock && statusPanel.isVisible()) {
      statusPanel.setVisible(false);
    }
  }


  private void prepareDialog() {

    nameField.setText(sessionInfo.getUserName());
    passField.setText(null);
    passField.setCaretPosition(0);

    if (promptLogin)  {
      setLoginPanelVisible(true);
      nameField.requestFocusLater();
    }
    else  {
      setLoginPanelVisible(false);
      statusPanel.setVisible(true);
      promptLogin = true;   // next round we will prompt again!
    }

    pack();

    SplashScreen splash = SplashScreen.getSplashScreen();
    if (splash != null) {
      splash.close();   // close splashscreen, if any
    }

    setModal(!locked);  // if invoked from within application: dispose if user/password entered

    ok = false;

    setVisible(true);           // this will block if in dispatch thread, else will not block!
  }


  private void setOk(boolean ok) {
    this.ok = ok;
    waitLogin = false;
    if (!locked) {
      dispose();
    }
    else  {
      synchronized(this) {
        notifyAll();
      }
    }
  }


  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    layeredPane = new javax.swing.JLayeredPane();
    imageLabel = new javax.swing.JLabel();
    loginPanel = new org.tentackle.swing.FormPanel();
    jLabel1 = new org.tentackle.swing.FormLabel();
    jLabel2 = new org.tentackle.swing.FormLabel();
    nameField = new org.tentackle.swing.StringFormField();
    passField = new javax.swing.JPasswordField();
    okButton = new org.tentackle.swing.FormButton();
    cancelLoginButton = new org.tentackle.swing.FormButton();
    capsLockLabel = new org.tentackle.swing.FormLabel();
    statusPanel = new org.tentackle.swing.FormPanel();
    statusLabel = new org.tentackle.swing.FormLabel();
    cancelStatusButton = new org.tentackle.swing.FormButton();
    logoPanel = new org.tentackle.swing.FormPanel();
    logoLabel = new javax.swing.JLabel();

    setAutoPosition(true);
    setUndecorated(true);

    layeredPane.setBackground(new java.awt.Color(2, 2, 206));
    layeredPane.setOpaque(true);

    imageLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/tentackle/swing/rdc/images/login.png"))); // NOI18N
    layeredPane.add(imageLabel);
    imageLabel.setBounds(0, 0, 500, 350);

    loginPanel.setOpaque(false);

    jLabel1.setForeground(new java.awt.Color(204, 204, 255));
    jLabel1.setText(RdcSwingRdcBundle.getString("USERNAME:")); // NOI18N
    jLabel1.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N

    jLabel2.setForeground(new java.awt.Color(204, 204, 255));
    jLabel2.setText(RdcSwingRdcBundle.getString("PASSWORD:")); // NOI18N
    jLabel2.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N

    nameField.setAutoSelect(true);
    nameField.setBackground(new java.awt.Color(204, 204, 255));
    nameField.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 255), 2));
    nameField.setColumns(8);
    nameField.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
    nameField.setName("username"); // NOI18N

    passField.setBackground(new java.awt.Color(204, 204, 255));
    passField.setColumns(8);
    passField.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
    passField.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 255), 2));
    passField.setName("password"); // NOI18N
    passField.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        passFieldActionPerformed(evt);
      }
    });

    okButton.setBackground(new java.awt.Color(204, 204, 255));
    okButton.setText(RdcSwingRdcBundle.getString("LOGIN")); // NOI18N
    okButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
    okButton.setName("login"); // NOI18N
    okButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        okButtonActionPerformed(evt);
      }
    });

    cancelLoginButton.setBackground(new java.awt.Color(204, 204, 255));
    cancelLoginButton.setText(RdcSwingRdcBundle.getString("CANCEL")); // NOI18N
    cancelLoginButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
    cancelLoginButton.setName("cancelLogin"); // NOI18N
    cancelLoginButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cancelLoginButtonActionPerformed(evt);
      }
    });

    capsLockLabel.setForeground(java.awt.Color.orange);
    capsLockLabel.setText(RdcSwingRdcBundle.getString("CAPS LOCK")); // NOI18N

    javax.swing.GroupLayout loginPanelLayout = new javax.swing.GroupLayout(loginPanel);
    loginPanel.setLayout(loginPanelLayout);
    loginPanelLayout.setHorizontalGroup(
      loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(loginPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, loginPanelLayout.createSequentialGroup()
            .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
              .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
              .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
              .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
              .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                .addComponent(nameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(passField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addGap(41, 41, 41))
          .addGroup(loginPanelLayout.createSequentialGroup()
            .addComponent(cancelLoginButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(capsLockLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 410, Short.MAX_VALUE)
            .addContainerGap())))
    );
    loginPanelLayout.setVerticalGroup(
      loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, loginPanelLayout.createSequentialGroup()
        .addContainerGap(137, Short.MAX_VALUE)
        .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(nameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addGap(23, 23, 23)
        .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(passField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addGap(26, 26, 26)
        .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(77, 77, 77)
        .addGroup(loginPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(cancelLoginButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(capsLockLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addContainerGap())
    );

    layeredPane.setLayer(loginPanel, javax.swing.JLayeredPane.PALETTE_LAYER);
    layeredPane.add(loginPanel);
    loginPanel.setBounds(0, 0, 500, 355);

    statusPanel.setOpaque(false);

    statusLabel.setForeground(java.awt.Color.orange);
    statusLabel.setText(RdcSwingRdcBundle.getString("LOGIN...")); // NOI18N

    cancelStatusButton.setBackground(new java.awt.Color(204, 204, 255));
    cancelStatusButton.setText(RdcSwingRdcBundle.getString("CANCEL")); // NOI18N
    cancelStatusButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
    cancelStatusButton.setName("cancelStatus"); // NOI18N
    cancelStatusButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cancelStatusButtonActionPerformed(evt);
      }
    });

    javax.swing.GroupLayout statusPanelLayout = new javax.swing.GroupLayout(statusPanel);
    statusPanel.setLayout(statusPanelLayout);
    statusPanelLayout.setHorizontalGroup(
      statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(statusPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addComponent(cancelStatusButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(statusLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 410, Short.MAX_VALUE)
        .addContainerGap())
    );
    statusPanelLayout.setVerticalGroup(
      statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, statusPanelLayout.createSequentialGroup()
        .addContainerGap(317, Short.MAX_VALUE)
        .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(cancelStatusButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(statusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addContainerGap())
    );

    layeredPane.setLayer(statusPanel, javax.swing.JLayeredPane.MODAL_LAYER);
    layeredPane.add(statusPanel);
    statusPanel.setBounds(0, 0, 500, 350);

    logoPanel.setOpaque(false);
    logoPanel.setLayout(null);

    logoLabel.setIconTextGap(0);
    logoPanel.add(logoLabel);
    logoLabel.setBounds(60, 110, 130, 145);

    layeredPane.setLayer(logoPanel, javax.swing.JLayeredPane.POPUP_LAYER);
    layeredPane.add(logoPanel);
    logoPanel.setBounds(0, 0, 500, 350);

    getContentPane().add(layeredPane, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void cancelStatusButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelStatusButtonActionPerformed
    setOk(false);
  }//GEN-LAST:event_cancelStatusButtonActionPerformed

  private void cancelLoginButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelLoginButtonActionPerformed
    setOk(false);
  }//GEN-LAST:event_cancelLoginButtonActionPerformed

  private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
    setOk(true);
  }//GEN-LAST:event_okButtonActionPerformed

  private void passFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_passFieldActionPerformed
    okButton.doClick();
  }//GEN-LAST:event_passFieldActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private org.tentackle.swing.FormButton cancelLoginButton;
  private org.tentackle.swing.FormButton cancelStatusButton;
  private org.tentackle.swing.FormLabel capsLockLabel;
  private javax.swing.JLabel imageLabel;
  private org.tentackle.swing.FormLabel jLabel1;
  private org.tentackle.swing.FormLabel jLabel2;
  private javax.swing.JLayeredPane layeredPane;
  private org.tentackle.swing.FormPanel loginPanel;
  private javax.swing.JLabel logoLabel;
  private org.tentackle.swing.FormPanel logoPanel;
  private org.tentackle.swing.StringFormField nameField;
  private org.tentackle.swing.FormButton okButton;
  private javax.swing.JPasswordField passField;
  private org.tentackle.swing.FormLabel statusLabel;
  private org.tentackle.swing.FormPanel statusPanel;
  // End of variables declaration//GEN-END:variables

}
