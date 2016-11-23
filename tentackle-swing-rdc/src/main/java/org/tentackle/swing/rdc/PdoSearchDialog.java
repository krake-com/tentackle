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

// Created on July 23, 2002, 3:33 PM

package org.tentackle.swing.rdc;


import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FocusTraversalPolicy;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.TreeSelectionModel;
import org.tentackle.common.StringHelper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.SecurityResult;
import org.tentackle.swing.FormDialog;
import org.tentackle.swing.FormInfo;
import org.tentackle.swing.FormPanel;
import org.tentackle.swing.FormUtilities;




/**
 * A generic search dialog.
 *
 * @param <T> the pdo type
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class PdoSearchDialog<T extends PersistentDomainObject<T>> extends FormDialog implements KeyEventDispatcher {

  private PdoSearch<T>            pdoSearch;                // the search plugin according to PDO-class
  private SelectionFilter         selectionFilter;          // the selection filter, null if all of the PDO-class are allowed
  private boolean                 allowCreate;              // true if creating new is allowed
  private boolean                 multiSelection;           // true if modal and multiselections allowed
  private boolean                 autoSelectFirstItem;      // true if autoselect first item, default = true
  private boolean                 showMessageIfNotFound;    // true = show message if no objects found

  private FormPanel               searchPanel;              // panel from plugin
  private String                  morePattern;              // searchPattern im Suchergebnis
  private PersistentDomainObject<?> selectedObject;         // returned selected object
  private List<PersistentDomainObject<?>> selectedObjects;  // returned selected objects, if multiSelection
  private PdoNavigationPanel<T>   naviPanel;                // navigation panel
  private List<T>                 naviList;                 // list of objects shown in naviPanel
  private boolean                 packed;                   // true if dialog is already packed



  /**
   * Creates a search dialog.
   *
   * @param owner the owner window, null if none
   * @param context the database context
   * @param pdoClass the PDO class to search
   * @param selectionFilter the section filter, null if all of PDO class are allowed
   * @param allowCreate true if "new"-button for creation of a new object of searchClass
   * @param modal true if modal dialog
   */
  public PdoSearchDialog(Window owner, DomainContext context, Class<T> pdoClass,
                         SelectionFilter selectionFilter, boolean allowCreate, boolean modal) {
    super (owner, modal);
    setup (context, pdoClass, selectionFilter, allowCreate);
  }

  /**
   * Creates a search dialog.
   *
   * @param context the database context
   * @param pdoClass the object class'es table to search in
   * @param selectionFilter the section filter, null if all of PDO class are allowed
   * @param allowCreate true if "new"-button for creation of a new object of searchClass
   * @param modal true if modal dialog
   */
  public PdoSearchDialog(DomainContext context, Class<T> pdoClass, SelectionFilter selectionFilter,
                         boolean allowCreate, boolean modal) {
    this (null, context, pdoClass, selectionFilter, allowCreate, modal);
  }

  /**
   * Creates a search dialog.
   *
   * @param owner the owner window, null if none
   * @param pdoSearch the search plugin
   * @param selectionFilter the section filter, null if all of PDO class are allowed
   * @param allowCreate true if "new"-button for creation of a new object of searchClass
   * @param modal true if modal dialog
   */
  public PdoSearchDialog(Window owner, PdoSearch<T> pdoSearch, SelectionFilter selectionFilter, boolean allowCreate, boolean modal) {
    super (owner, modal);
    setup (pdoSearch, selectionFilter, allowCreate);
  }




  /**
   * Gets the selection filter.
   *
   * @return the selection filter, null if all of PDO class are allowed
   */
  public SelectionFilter getSelectionFilter() {
    return selectionFilter;
  }


  /**
   * Gets the allowcreate flag.
   *
   * @return true if "new"-button for creation of a new object of searchClass
   */
  public boolean isAllowCreate() {
    return allowCreate;
  }


  /**
   * get the search plugin.
   *
   * @return Value of property pdoSearch.
   */
  public PdoSearch<T> getPdoSearch() {
    return pdoSearch;
  }

  /**
   * Set the search plugin (if custom).
   *
   * @param pdoSearch New value of property pdoSearch.
   *
   */
  public void setPdoSearch(PdoSearch<T> pdoSearch) {
    this.pdoSearch = pdoSearch;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to allow autoclosing whether the values in search panels are changed or not.<br>
   * The time of last change is set in updateResult() and whenever a field is changed.
   */
  @Override
  public boolean checkAutoClose() {
    return isAutoCloseable() && isVisible() &&
           getTimeOfLastValuesChanged() + getAutoClose() < System.currentTimeMillis();
  }


  /**
   * Enables/disables all buttons.
   * <p>
   * Notice: the buttons of the plugin panel are not modified.
   *
   * @param flag true if all buttons enabled, false if all disabled
   */
  public void setButtonsEnabled(boolean flag) {
    searchButton.setEnabled(flag);
    cancelButton.setEnabled(flag);
    moreButton.setEnabled(flag);
    newButton.setEnabled(flag);
  }


  /**
   * Gives access to the current navigation panel.
   *
   * @return the navigation panel
   */
  public PdoNavigationPanel<T> getNaviPanel()  {
    return naviPanel;
  }


  /**
   * Sets whether multiple selections are allowed or not.
   *
   * @param multiSelection true if allowed, false if only single object (default)
   */
  public void setMultiSelection(boolean multiSelection) {
    this.multiSelection = multiSelection;
  }

  /**
   * Returns whether multiple selections are allowed or not.
   *
   * @return true if allowed, false if only single object (default)
   */
  public boolean isMultiSelection() {
    return multiSelection;
  }


  /**
   * Sets whether the first object is automatically selected or not.
   *
   * @param autoSelectFistItem true if selected, false if not (default)
   */
  public void setAutoSelectFirstItem(boolean autoSelectFistItem) {
    this.autoSelectFirstItem = autoSelectFistItem;
  }

  /**
   * Returns whether the first object is automatically selected or not.
   *
   * @return true if selected, false if not (default)
   */
  public boolean isAutoSelectFirstItem() {
    return autoSelectFirstItem;
  }



  /**
   * Shows the dialog (modal or non-modal).
   * <p>
   * Notice: if multiSelection=true the first selected object is returned. U
   * se getSelectedObjects() to get all objects
   *
   * @return the selected object, null if nothing selected or non-modal
   */
  public PersistentDomainObject<?> showDialog ()  {

    // check permissions
    SecurityResult sr = SecurityFactory.getInstance().getSecurityManager().evaluate(
            pdoSearch.getDomainContext(), SecurityFactory.getInstance().getReadPermission(), pdoSearch.getPdoClassId(), 0);
    if (!sr.isAccepted())  {
      FormInfo.show(sr.explain(RdcSwingRdcBundle.getString("YOU DON'T HAVE ENOUGH PERMISSIONS TO VIEW THIS KIND OF DATA!")));
      return null;
    }

    if (pdoSearch.isSearchImmediate() && pdoSearch.isSearchPanelInvisible()) {
      searchPanel.setVisible(false);
    }

    setFormValues();
    saveValues();   // to allow areValuesChanged, if some app needs that
    pack();

    if (pdoSearch.isSearchImmediate())  {
      runSearch();
      if (pdoSearch.isDialogNotShownIfSearchImmediateFindsSingleMatch() && naviList != null && naviList.size() == 1)  {
        selectedObject = naviList.get(0);
        return selectedObject;
      }
      if (!pdoSearch.isDialogShownIfSearchImmediateFails() && (naviList == null || naviList.isEmpty())) {
        return null;
      }
    }

    setVisible(true);
    return selectedObject;    // returns null if non-modal
  }


  /**
   * Gets the (first) selected object.
   *
   * @return the selected object, null if none
   */
  public PersistentDomainObject<?> getSelectedObject() {
    return selectedObject;
  }

  /**
   * Gets the list of all selected objects.
   *
   * @return the selected objects, null if none
   */
  public List<PersistentDomainObject<?>> getSelectedObjects()  {
    return selectedObjects;
  }

  /**
   * Gets the list of all objects.
   *
   * @return the list of all objects
   */
  public List<T> getObjects() {
    return naviList;
  }


  /**
   * Returns whether a message should be displayed if no object is found.
   *
   * @return true if show (default), false if don't show
   */
  public boolean isShowMessageIfNotFound() {
    return showMessageIfNotFound;
  }


  /**
   * Sets whether a message should be displayed if no object is found.
   *
   * @param showMessageIfNotFound true if show (default), false if don't show
   */
  public void setShowMessageIfNotFound(boolean showMessageIfNotFound) {
    this.showMessageIfNotFound = showMessageIfNotFound;
  }



  // ----------------------------- implements KeyEventDispatcher ---------------

  /**
   * dispatch the special keys
   */
  @Override
  public boolean dispatchKeyEvent(KeyEvent e) {

    if (isFocused() &&    // window has focus (there is only ONE dispatcher for ALL windows!)
        e.getID() == KeyEvent.KEY_PRESSED) {

      if (e.getModifiers() == KeyEvent.CTRL_MASK)  {   // CTRL pressed

        switch (e.getKeyCode()) {

          case KeyEvent.VK_N:
            if (newButton.isVisible() && newButton.isEnabled()) {
              newButton.doClick();
            }
            break;

          case KeyEvent.VK_F:
            if (searchButton.isVisible() && searchButton.isEnabled()) {
              searchButton.doClick();
            }
            break;

          case KeyEvent.VK_W:
            if (cancelButton.isVisible() && cancelButton.isEnabled()) {
              cancelButton.doClick();
            }
            break;

          default:
            return false;
        }
        // don't dispatch to components!
        e.consume();
        return true;
      }

      else if (e.getModifiers() == 0) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && isModal()) {
          if (cancelButton.isVisible() && cancelButton.isEnabled()) {
            cancelButton.doClick();
          }
          e.consume();
          return true;
        }
      }
    }

    // else proceed with standard dispatching
    return false;
  }

  // ----------------- end KeyEventDispatcher ----------------------------------




  /**
   * {@inheritDoc}
   * <p>
   * Overridden to catch WINDOW_CLOSING event.
   */
  @Override
  protected void processWindowEvent(WindowEvent e) {
    if (e.getID() == WindowEvent.WINDOW_ACTIVATED) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }
    if (e.getID() == WindowEvent.WINDOW_DEACTIVATED) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    }
    super.processWindowEvent(e);
  }





  /**
   * Runs the search.
   * <p>
   * Depending on the search parameter the search is run in a separate thread.
   */
  protected void runSearch() {

    if (pdoSearch.isSearchCriteriaValid()) {

      setButtonsEnabled(false);
      FormUtilities.getInstance().setWaitCursor(this);

      // execute query
      pdoSearch.execute((t) -> {

        if (pdoSearch.isResultValid(t)) {
          updateResult(t);
          if (t.isEmpty()) {
            FormInfo.show(RdcSwingRdcBundle.getString("NO SUCH OBJECTS FOUND"));
          }
          // if previous search was searchMore:
          if (morePattern != null) {
            morePattern = null;
            morePatternField.fireValueChanged();
          }

          // set the focus
          if (!t.isEmpty() && autoSelectFirstItem) {
            naviPanel.requestFocusForFirstItem();
          }
          else  {
            setInitialFocus();
          }
        }

        setButtonsEnabled(true);
        FormUtilities.getInstance().setDefaultCursor(PdoSearchDialog.this);
      });

    }
  }


  /**
   * Sets the focus to the initial or first component.
   */
  protected void setInitialFocus() {
    FocusTraversalPolicy policy = getFocusTraversalPolicy();
    if (policy != null) {
      Component c = policy.getInitialComponent(this);
      if (c == null) {
        c =  policy.getFirstComponent(this);
      }
      if (c != null) {
        c.requestFocusInWindow();
      }
    }
  }

  /**
   * Search in results.
   */
  protected void doMoreSearch ()  {
    if (naviList != null && morePattern != null && morePattern.length() > 0)  {
      String thMorePattern = StringHelper.normalize(morePattern);
      List<T> nlist = new ArrayList<>();
      for (T mo: naviList)  {
        if (mo != null && mo.containsPattern(thMorePattern))  {
          nlist.add(mo);
        }
      }
      updateResult(nlist);
    }
  }




  /**
   * Creates the navigation panel.
   *
   * @return the navi panel
   */
  public PdoNavigationPanel<T> createNavigationPanel() {
    PdoNavigationPanel<T> panel = Rdc.createPdoNavigationPanel(getObjects(), getSelectionFilter(),
                          (isModal() ?
                            (pdoSearch.isSearchPanelInvisible() ?
                              PdoNavigationPanel.SHOW_BUTTONS :
                              PdoNavigationPanel.SHOW_SELECT) :
                            (pdoSearch.isSearchPanelInvisible() ?
                              PdoNavigationPanel.SHOW_CLOSE :
                              PdoNavigationPanel.SHOW_NOBUTTON)),
                          pdoSearch.isTableViewInitiallyShown(), pdoSearch.getFormTableName());
    if (pdoSearch.isViewModeFixed()) {
      panel.setViewModeButtonsVisible(false);
    }
    return panel;
  }


  /**
   * Updates the list of objects in the navigation panel.
   *
   * @param list the list of objects
   */
  @SuppressWarnings("unchecked")
  protected void updateResult (List<T> list)  {

    clearResult();

    if (list != null)  {

      // remember for "search in results"
      naviList = list;

      // createPdo navigation panel
      if (naviPanel == null)  {

        naviPanel = createNavigationPanel();

        // show view
        getContentPane().add(naviPanel, BorderLayout.CENTER);

        naviPanel.addActionListener((ActionEvent e) -> {
          // some button pressed in naviPanel
          selectedObject = naviPanel.getSelectedObject();
          selectedObjects = naviPanel.getSelectedObjects();
          if (isModal() || selectedObject == null) {
            dispose();    // finish dialog
          }
          else  {
            // show selected object
            PdoEditDialogPool.getInstance().view((T) selectedObject);
          }
        });
      }
      else  {
        naviPanel.setObjects(list, pdoSearch.isViewRebuildNecessary());
      }

      naviPanel.setTableIntro(pdoSearch.getSearchCriteriaAsString());
      if (isModal()) {
        naviPanel.setDisposeKeyEnabled(true);
      }

      if (multiSelection)  {
        naviPanel.setTreeSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
      }
      else  {
        naviPanel.setTreeSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      }

      if (naviList.size() > 1)  {
        // enable "search in results"
        moreButton.setVisible(true);
        morePatternField.setVisible(true);
      }
    }

    // start timeout interval again
    setTimeOfLastValuesChanged(System.currentTimeMillis());

    if (!packed) {
      pack();
      packed = true;
    }
  }


  /**
   * Cancels the dialog.<br>
   * (clears the selection and the search parameter).
   */
  protected void doCancel() {
    selectedObject = null;
    selectedObjects = null;
  }



  /**
   * Creates the default pdo search from the GUI-provider according to given PDO class.
   *
   * @param context the database context
   * @param searchClass the allowed selections
   * @return the plugin
   */
  @SuppressWarnings("unchecked")
  protected PdoSearch<T> createPdoSearch(DomainContext context, Class<T> searchClass) {
    return Rdc.createGuiProvider(Pdo.create(searchClass, context)).createPdoSearch();
  }



  /**
   * Sets up the dialog by context and searchclass.
   *
   * @param context the database context
   * @param pdoClass the object class'es table to search in
   * @param selectionFilter the filter for allowed selections
   * @param allowCreate true if "new"-button for creation of a new object of searchClass
   */
  protected void setup (DomainContext context, Class<T> pdoClass,
                        SelectionFilter selectionFilter, boolean allowCreate) {
    setup(createPdoSearch(context, pdoClass), selectionFilter, allowCreate);
  }


  /**
   * Sets up the dialog by plugin.
   *
   * @param pdoSearch the search plugin
   * @param selectionFilter the filter for allowed selections
   * @param allowCreate true if "new"-button for creation of a new object of searchClass
   */
  protected void setup (PdoSearch<T> pdoSearch, SelectionFilter selectionFilter, boolean allowCreate)  {

    this.pdoSearch = pdoSearch;
    this.selectionFilter = selectionFilter;
    this.allowCreate = allowCreate;

    enableEvents(AWTEvent.WINDOW_EVENT_MASK); // for process.. below

    initComponents();

    setMultiSelection(true);    // this is default
    setAutoSelectFirstItem(true);
    setShowMessageIfNotFound(true);

    searchPanel = pdoSearch.getSearchPanel();
    searchCriteriaPanel.add(searchPanel, BorderLayout.CENTER);

    searchPanel.addActionListener((ActionEvent e) -> searchButton.doClick());

    String fmtClass   = null;
    String fmtContext = null;

    try {
      T pdo = pdoSearch.createPdo();
      fmtClass = pdo.getPlural();
      fmtContext = pdo.getBaseContext().toString();
    }
    catch (Exception ex) {
      // nothing we can do
    }

    String title;
    if (fmtClass == null) {
      title = RdcSwingRdcBundle.getString("SEARCH");
    }
    else if (fmtContext == null || fmtContext.length() == 0)  {
      title = MessageFormat.format(RdcSwingRdcBundle.getString("SEARCH {0}"), fmtClass);
    }
    else  {
      title = MessageFormat.format(RdcSwingRdcBundle.getString("SEARCH {0} IN {1}"), fmtClass, fmtContext);
    }

    if (isModal())  {
      title += RdcSwingRdcBundle.getString(" (MODAL)");
    }

    setTitle(title);

    newButton.setVisible(allowCreate);

    setFormValues();      // set all Values (also in search panel)

    clearResult();
  }


  /**
   * Clears the search result.
   */
  protected void clearResult()  {
    selectedObject = null;
    selectedObjects = null;
    naviList = null;
    if (naviPanel != null)  {
      naviPanel.setObjects(new ArrayList<>());
    }
    morePatternField.setVisible(false);
    moreButton.setVisible(false);
  }




  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    searchCriteriaPanel = new org.tentackle.swing.FormPanel();
    patternPanel = new org.tentackle.swing.FormPanel();
    buttonPanel = new org.tentackle.swing.FormPanel();
    searchButton = new org.tentackle.swing.FormButton();
    newButton = new org.tentackle.swing.FormButton();
    cancelButton = new org.tentackle.swing.FormButton();
    morePatternField = new org.tentackle.swing.StringFormField();
    moreButton = new org.tentackle.swing.FormButton();

    setAutoPosition(true);
    addFormWrapListener(new org.tentackle.swing.FormWrapListener() {
      public void formWrapped(org.tentackle.swing.FormWrapEvent evt) {
        formFormWrapped(evt);
      }
    });
    addWindowListener(new java.awt.event.WindowAdapter() {
      public void windowClosed(java.awt.event.WindowEvent evt) {
        formWindowClosed(evt);
      }
    });

    searchCriteriaPanel.setLayout(new java.awt.BorderLayout());

    patternPanel.setLayout(new java.awt.GridBagLayout());

    buttonPanel.setLayout(new java.awt.GridBagLayout());

    searchButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("search"));
    searchButton.setMnemonic('f');
    searchButton.setText(RdcSwingRdcBundle.getString("FIND")); // NOI18N
    searchButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    searchButton.setName("find"); // NOI18N
    searchButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        searchButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
    buttonPanel.add(searchButton, gridBagConstraints);

    newButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("new"));
    newButton.setMnemonic('n');
    newButton.setText(RdcSwingRdcBundle.getString("NEW")); // NOI18N
    newButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    newButton.setName("new"); // NOI18N
    newButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        newButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
    buttonPanel.add(newButton, gridBagConstraints);

    cancelButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("close"));
    cancelButton.setMnemonic('c');
    cancelButton.setText(RdcSwingRdcBundle.getString("CANCEL")); // NOI18N
    cancelButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    cancelButton.setName("cancel"); // NOI18N
    cancelButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cancelButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
    buttonPanel.add(cancelButton, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    patternPanel.add(buttonPanel, gridBagConstraints);

    morePatternField.setAutoSelect(true);
    morePatternField.setColumns(15);
    morePatternField.setConvert(org.tentackle.swing.FormFieldComponent.CONVERT_UC);
    morePatternField.setInvalidChars("=%"); // NOI18N
    morePatternField.setName("morePattern"); // NOI18N
    morePatternField.addValueListener(new org.tentackle.swing.ValueListener() {
      public void valueEntered(org.tentackle.swing.ValueEvent evt) {
        morePatternFieldValueEntered(evt);
      }
      public void valueChanged(org.tentackle.swing.ValueEvent evt) {
        morePatternFieldValueChanged(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
    patternPanel.add(morePatternField, gridBagConstraints);

    moreButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("search"));
    moreButton.setText(RdcSwingRdcBundle.getString("FIND IN RESULTS")); // NOI18N
    moreButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    moreButton.setName("findInResults"); // NOI18N
    moreButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        moreButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    patternPanel.add(moreButton, gridBagConstraints);

    searchCriteriaPanel.add(patternPanel, java.awt.BorderLayout.SOUTH);

    getContentPane().add(searchCriteriaPanel, java.awt.BorderLayout.NORTH);
  }// </editor-fold>//GEN-END:initComponents

  private void formFormWrapped(org.tentackle.swing.FormWrapEvent evt) {//GEN-FIRST:event_formFormWrapped
    if (moreButton.isVisible() && !moreButton.isSelected()) {
      moreButton.doClick();
    }
    else if (searchButton.isVisible())  {
      searchButton.doClick();
    }
  }//GEN-LAST:event_formFormWrapped

  private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
    if (pdoSearch != null)  {
      clearResult();
    }
  }//GEN-LAST:event_formWindowClosed

  private void morePatternFieldValueEntered(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_morePatternFieldValueEntered
    morePattern = morePatternField.getText();
  }//GEN-LAST:event_morePatternFieldValueEntered

  private void morePatternFieldValueChanged(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_morePatternFieldValueChanged
    morePatternField.setFormValue(morePattern);
  }//GEN-LAST:event_morePatternFieldValueChanged

  private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
    doCancel();
    dispose();
  }//GEN-LAST:event_cancelButtonActionPerformed

  @SuppressWarnings("unchecked")
  private void newButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newButtonActionPerformed
    // createPdo and edit new object
    T newObject = PdoEditDialogPool.getInstance().editModal(pdoSearch.createPdo());
    selectedObject = newObject;

    if (selectedObject != null) {
      if (selectionFilter != null && !selectionFilter.isSelectable(selectedObject))  {
        // special case: PDO class is not allowed for selection (e.g. obj is Person and sel is Adresse)
        List<T> list = new ArrayList<>();
        list.add(newObject);
        updateResult(list);     // show selected object only
        selectedObjects = new ArrayList<>();
        selectedObjects.add(selectedObject);
      }
      else if (isModal()) {
        dispose();
      }
    }
  }//GEN-LAST:event_newButtonActionPerformed

  private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
    runSearch();
  }//GEN-LAST:event_searchButtonActionPerformed

  private void moreButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moreButtonActionPerformed
    morePattern = morePatternField.getText();
    doMoreSearch();
    if (naviPanel != null)  {
      naviPanel.requestFocusForFirstItem();
    }
  }//GEN-LAST:event_moreButtonActionPerformed



  // Variables declaration - do not modify//GEN-BEGIN:variables
  private org.tentackle.swing.FormPanel buttonPanel;
  private org.tentackle.swing.FormButton cancelButton;
  private org.tentackle.swing.FormButton moreButton;
  private org.tentackle.swing.StringFormField morePatternField;
  private org.tentackle.swing.FormButton newButton;
  private org.tentackle.swing.FormPanel patternPanel;
  private org.tentackle.swing.FormButton searchButton;
  private org.tentackle.swing.FormPanel searchCriteriaPanel;
  // End of variables declaration//GEN-END:variables

}
