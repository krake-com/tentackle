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



package org.tentackle.swing.rdc.security;

import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.text.MessageFormat;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.TrackedList;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.security.pdo.Security;
import org.tentackle.swing.AcceptDenyCheckBox;
import org.tentackle.swing.FormDialog;
import org.tentackle.swing.FormError;
import org.tentackle.swing.FormTable;
import org.tentackle.swing.rdc.CancelSaveDiscardDialog;
import org.tentackle.swing.rdc.PdoTablePanel;
import org.tentackle.swing.rdc.Rdc;




/**
 * Dialog to edit security rules.<br>
 * The dialog edits the class rules in one tab and the optional object rules
 * in a second.
 * The user can createPdo, modify, delete and re-arrange rules to setup
 * the desired ACLs.
 */
public class SecurityDialog extends FormDialog {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityDialog.class);

  /** transaction name for "save rules" **/
  public static final String TX_SAVE_RULES  = "save rules";

  private static final long serialVersionUID = 7560878743739380186L;

  /** database context. */
  private final DomainContext context;
  /** managed class. */
  private final Class<?> clazz;
  /** managed object. */
  private final long id;
  /** classname (null if pdo). */
  private final String className;
  /** != 0 if clazz is a persistent domain object. */
  private final int pdoClassId;
  /** panel for to edit the class rules. */
  private final PdoTablePanel<Security> clazzPanel;
  /** panel to edit the object rules. */
  private final PdoTablePanel<Security> objectPanel;
  /** security rules for the class. */
  private final TrackedList<Security> clazzList;
  /** security rules for the object. */
  private final TrackedList<Security> objectList;
  /** true if current user has the write permission to security rules. */
  private final boolean writeAllowed;



  /**
   * Creates a dialog for editing security rule sets.<br>
   * The SecurityDialog should only
   * be invoked indirectly by the SecurityManager.
   *
   * @param context the domain context
   * @param clazz   the class to set security rules for
   * @param id      the object id in clazz (if clazz is a PersistentDomainObject) or null
   *                if all objects or clazz is not a PersistentDomainObject.
   */
  @SuppressWarnings("unchecked")
  public SecurityDialog(DomainContext context, Class<?> clazz, long id) {

    this.context = context;
    this.clazz = clazz;
    this.id = id;

    initComponents();

    pdoClassId = PdoUtilities.getInstance().getClassId(clazz.getName());
    className = pdoClassId != 0 ? null : clazz.getName();

    // create empty instance of a Security object to load the rules
    Security sec = Pdo.create(Security.class, context);
    writeAllowed = sec.isWriteAllowed();
    setupUserLabel();

    // load class rules
    clazzList = pdoClassId == 0 ? sec.selectByObjectClass(className) : sec.selectByObject(pdoClassId, 0);

    // createPdo class rules table
    clazzPanel = new PdoTablePanel<Security>(Rdc.createGuiProvider(sec).getFormTableEntry(), clazzList, true, "securityDialogTable") {
      @Override
      protected Security createPdo() {
        Security sec = super.createPdo();
        sec.setObjectClassName(className);
        sec.setObjectClassId(pdoClassId);
        return sec;
      }
    };
    configureTable(clazzPanel.getFormTable());
    clazzPanel.addActionListener((ActionEvent e) -> {
      if (e.getActionCommand() != null && e.getActionCommand().equals(PdoTablePanel.ACTION_SAVE)) {
        doSave();
      }
      else  {
        doCancel();
      }
    });

    // determine the tab name
    String tabName = ReflectionHelper.getClassBaseName(clazz);
    if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
      try {
        tabName = ((Class<PersistentDomainObject>) clazz).newInstance().getSingular(); // unchecked
      }
      catch (Exception e) {
        // leave it as it is
      }
    }
    topicPane.addTab(tabName, clazzPanel);

    if (id != 0 && pdoClassId != 0)  {   // object id given: load object rules
      // load object rules
      objectList = sec.selectByObject(pdoClassId, id);
      // createPdo object rules table
      objectPanel = new PdoTablePanel<Security>(Rdc.createGuiProvider(sec).getFormTableEntry(), objectList, true, "securityDialogTable") {
        @Override
        protected Security createPdo() {
          Security sec = super.createPdo();
          sec.setObjectClassId(pdoClassId);
          sec.setObjectId(id);
          return sec;
        }
      };
      configureTable(objectPanel.getFormTable());
      objectPanel.addActionListener((ActionEvent e) -> {
        if (e.getActionCommand() != null && e.getActionCommand().equals(PdoTablePanel.ACTION_SAVE)) {
          doSave();
        }
        else  {
          doCancel();
        }
      });

      // determine the name of the object tab
      tabName = className + "[" + id + "]";
      try {
        tabName = Pdo.create((Class) clazz, context).selectCached(id).toString();
      }
      catch (Exception e) {
        LOGGER.severe("could not determine tabName", e);
      }

      topicPane.addTab(tabName, objectPanel);
    }
    else  {
      objectList = null;
      objectPanel = null;
    }
  }

  /**
   * Gets the security PDO class.
   *
   * @return the class
   */
  public Class<?> getClazz() {
    return clazz;
  }

  /**
   * Gets the domain context.
   *
   * @return the domain context
   */
  public DomainContext getDomainContext() {
    return context;
  }


  /**
   * Returns whether writing security rules is allowed for current user.
   *
   * @return true if allowed
   */
  public boolean isWriteAllowed() {
    return writeAllowed;
  }

  /**
   * Sets up the userlabel.
   * By default the userlabel gets the username from the UserInfo.
   */
  protected void setupUserLabel() {
    userLabel.setText(MessageFormat.format(SecuritySwingRdcBundle.getString("YOU ARE LOGGED IN AS {0}"), context.getSessionInfo().getUserName()));
  }


  /**
   * Shows the (modal) dialog.
   */
  public void showDialog()  {
    setFormValues();    // in case something else shown
    saveValues();       // to determine changes
    pack();
    setVisible(true);
  }


  /**
   * Saves the rules.
   *
   * @return true if done, else false if error
   */
  protected boolean saveRules()  {

    long txVoucher = context.getSession().begin(TX_SAVE_RULES);   // large transaction!

    try {
      // all class settings
      if (clazzList.isSomeRemoved()) {
        PdoUtilities.getInstance().deleteCollection(clazzList.getRemovedObjects());
      }
      int prioIndex = 0;
      for (Security sec: clazzList) {
        sec.setObjectClassName(className);
        sec.setObjectId(0);
        sec.setPriority(prioIndex++);
        if (sec.isModified()) {
          sec.save();
        }
      }

      if (objectList != null) {
        // object settings
        if (objectList.isSomeRemoved()) {
          PdoUtilities.getInstance().deleteCollection(objectList.getRemovedObjects());
        }
        prioIndex = 0;
        for (Security sec: objectList) {
          sec.setPriority(prioIndex++);
          sec.setObjectClassName(className);
          sec.setObjectId(id);
          if (sec.isModified()) {
            sec.save();
          }
        }
      }

      context.getSession().commit(txVoucher);
      return true;
    }
    catch (RuntimeException e) {
      context.getSession().rollback(txVoucher);
      LOGGER.logStacktrace(Logger.Level.WARNING, e);
      return false;
    }
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overritten so we can exit when window is closed:
   * do some stuff before exit.
   */
  @Override
  protected void processWindowEvent(WindowEvent e) {
    if (e.getID() == WindowEvent.WINDOW_CLOSING)  {
      doCancel();
      return;
    }
    super.processWindowEvent(e);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to enable/disable the save-button.
   */
  @Override
  public void triggerValuesChanged()  {
    super.triggerValuesChanged();
    boolean enabled = areValuesChanged() && writeAllowed;
    clazzPanel.getSaveButton().setEnabled(enabled);
    if (objectPanel != null) {
      objectPanel.getSaveButton().setEnabled(enabled);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to update the save-button.
   */
  @Override
  public void saveValues()  {
    super.saveValues();
    clazzPanel.getSaveButton().setEnabled(false);
    if (objectPanel != null) {
      objectPanel.getSaveButton().setEnabled(false);
    }
  }



  /**
   * Configures a table.
   *
   * @param table the table (object rules or class rules)
   */
  @SuppressWarnings("unchecked")
  protected void configureTable(FormTable<Security> table) {
    table.setDefaultRenderer(Boolean.class, AcceptDenyCheckBox.getTableCellRenderer());
    table.setDefaultEditor(Boolean.class, AcceptDenyCheckBox.getTableCellEditor());
    table.setSurrendersFocusOnKeystroke(true);
    table.setCellTraversal(FormTable.CELLTRAVERSAL_COLUMN | FormTable.CELLTRAVERSAL_WRAPINLINE);
  }



  /**
   * save the rules and print errormessage if failed.
   * Dispose if save ok.
   */
  private void doSave() {
    if (!saveRules()) {
      FormError.show(SecuritySwingRdcBundle.getString("ERROR SAVING RULES"));
    }
    else  {
      dispose();
    }
  }



  /**
   * Cancels the dialog.
   */
  private void doCancel() {
    if (clazzPanel.isDataChanged() ||
        objectPanel != null && objectPanel.isDataChanged()) {
      int answer = CancelSaveDiscardDialog.getAnswer();
      if (answer == CancelSaveDiscardDialog.DISCARD) {
        dispose();
      }
      else if (answer == CancelSaveDiscardDialog.SAVE) {
        doSave();
      }
    }
    else  {
      dispose();
    }
  }


  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    headerPanel = new org.tentackle.swing.FormPanel();
    userLabel = new javax.swing.JLabel();
    topicPane = new javax.swing.JTabbedPane();

    setAutoPosition(true);
    setHelpURL("#securitymanager");
    setTitle(SecuritySwingRdcBundle.getString("SECURITY RULES")); // NOI18N
    setModal(true);

    headerPanel.setLayout(new java.awt.GridBagLayout());
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 20);
    headerPanel.add(userLabel, gridBagConstraints);

    getContentPane().add(headerPanel, java.awt.BorderLayout.NORTH);
    getContentPane().add(topicPane, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private org.tentackle.swing.FormPanel headerPanel;
  private javax.swing.JTabbedPane topicPane;
  protected javax.swing.JLabel userLabel;
  // End of variables declaration//GEN-END:variables

}
