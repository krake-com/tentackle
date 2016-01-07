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


package org.tentackle.swing.rdc;


import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import org.tentackle.bind.Binding;
import org.tentackle.bind.BindingException;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.ShortLongText;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.swing.FormComponent;
import org.tentackle.swing.FormError;
import org.tentackle.swing.FormFieldComponentPanel;
import org.tentackle.swing.FormUtilities;
import org.tentackle.swing.StringFormField;
import org.tentackle.swing.plaf.PlafUtilities;



/**
 * A panel containing a FormField representing the key to select the data object
 * and buttons for editing/viewing/searching.
 *
 * @param <T> the pdo type
 * @see PdoLinkPanel
 */
@SuppressWarnings("serial")
public class PdoFieldPanel<T extends PersistentDomainObject<T>> extends FormFieldComponentPanel implements DropTargetListener {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(PdoFieldPanel.class);

  private PdoSearch<T>              pdoSearch;                  // search plugin
  protected long                    linkedId;                   // the original Id of the object
  protected T                       linkedObject;               // the linked Object, null = none
  private DataFlavor                dndFlavor;                  // DnD Flavor
  private DropTarget                dropTarget;                 // droptarget
  private boolean                   checkExistsEnabled = true;  // true if show error popup if object does not exist
  private boolean                   searchRunning;              // true if search is currently running



  /**
   * Creates an application database object field panel.
   * <p>
   * Pressing {@code F2} in the key field will open a search dialog.
   * {@code F3} will edit the object.
   * Drag and drop is supported as well.<br>
   * By default, the editing component is a {@link StringFormField}.
   */
  public PdoFieldPanel() {
    super();
    initComponents();
    setup();
  }



  /**
   * Sets up the component, drop enabled and clears the object
   */
  protected void setup() {
    setFormComponent(new StringFormField());
    setDropEnabled(true);
    loadObject();
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to set the names in subcomponents.
   */
  @Override
  public void setName(String name) {
    super.setName(name);
    if (name != null) {
      ((Component) getFormComponent()).setName(name + "/key");
      infoField.setName(name + "/info");
      editButton.setName(name + "/edit");
      searchButton.setName(name + "/search");
    }
    else  {
      ((Component) getFormComponent()).setName("key");
      infoField.setName("info");
      editButton.setName("edit");
      searchButton.setName("search");
    }
  }

  /**
   * Sets whether the object exists for given domain key.<br>
   * If there is no object for given key an error popup will be shown.
   *
   * @param checkExistsEnabled true to enable check, false to disable
   */
  public void setCheckExistsEnabled(boolean checkExistsEnabled) {
    this.checkExistsEnabled = checkExistsEnabled;
  }

  /**
   * Gets the check exists feature.
   *
   * @return true if check is enabled (default)
   */
  public boolean isCheckExistsEnabled() {
    return checkExistsEnabled;
  }



  @Override
  public void setFormComponent(FormComponent comp) {
    FormComponent oldComponent = getFormComponent();
    if (oldComponent != null) {
      remove((Component)oldComponent);
    }
    super.setFormComponent(comp);
    setName(getName());

    ((Component)comp).addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_F3) {
          if (searchButton.isEnabled()) {
            searchButton.doClick();
          }
        }
        else if (e.getKeyCode() == KeyEvent.VK_F2) {
          if (editButton.isEnabled() && editButton.isVisible()) {
            runEdit();
          }
        }
        else if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
          // any valid input deletes the longtext
          infoField.clearText();
        }
      }
    });

    GridBagConstraints gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 1);
    add((Component)comp, gridBagConstraints);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden due to binding.<br>
   * Notice that obj may be null. In such a case the domain context will
   * be retrieved from the bindingProperty DomainContext.class from the formcontainer.
   */
  @Override
  @SuppressWarnings("unchecked")
  public void setFormValue(Object obj) {
    Binding binding = getBinding();
    if (binding != null) {
      try {
        // check if getter bound and returns an PersistentDomainObject
        Class<?> clazz = binding.getMember().getType();
        if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
          if (obj instanceof PersistentDomainObject) {
            // obj is set and valid
            setLink((Class<T>) clazz, ((T) obj).getDomainContext(), ((T) obj).getId());
          }
          else  {
            setLink((Class<T>) clazz, binding.getBinder().getBindingProperty(DomainContext.class), 0);
          }
          return;
        }
      }
      catch (Exception ex) {
        throw new BindingException("could not determine type for " + binding, ex);
      }
    }
    // else default: just text
    super.setFormValue(obj);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden due to binding.<br>
   */
  @Override
  @SuppressWarnings("unchecked")
  public Object getFormValue() {
    Binding binding = getBinding();
    if (binding != null) {
      // check if setter bound and accepts an PersistentDomainObject as a single argument
      Class<T> clazz;
      try {
        clazz = (Class<T>) binding.getMember().getType();
      }
      catch (Exception ex) {
        throw new BindingException("could not determine type for " + binding, ex);
      }

      if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
        try {
          searchRunning = true;
          T pdo = findByKey(clazz, binding.getBinder().getBindingProperty(DomainContext.class),
                            super.getFormValue());  // key to the object, usually key is a String
          FormUtilities.getInstance().doValidate(this);
          return checkObject(pdo, clazz);
        }
        catch (Exception ex) {
          throw new BindingException("cannot find object by domain key", ex);
        }
        finally {
          searchRunning = false;
        }
      }
    }
    else if (pdoSearch != null) {
      Class<T> clazz = pdoSearch.getPdoClass();
      DomainContext context = pdoSearch.getDomainContext();
      if (clazz != null && context != null) {
        try {
          searchRunning = true;
          return checkObject(findByKey(clazz, context, super.getFormValue()), clazz);
        }
        catch (Exception ex) {
          throw new PdoRuntimeException("cannot find object by domain key", ex);
        }
        finally {
          searchRunning = false;
        }
      }
    }

    // else default: just text
    return super.getFormValue();
  }


  /**
   * Checks the returned object against null and creates the error popup if enabled.
   *
   * @param object the returned object
   * @param clazz the PDO-class
   * @return the object
   */
  @SuppressWarnings("unchecked")
  protected Object checkObject(Object object, Class<T> clazz) {
    if (object == null && checkExistsEnabled) {
      String text = getFormComponent().getText();
      if (text != null && !text.isEmpty()) {
        if (getFormComponent().wasTransferFocusByEnter()) {
          try {
            setErrorMessage(MessageFormat.format(RdcSwingRdcBundle.getString("NO SUCH {0} FOUND"), Pdo.create(clazz).getPlural()));
          }
          catch (Exception ex) {
            setErrorMessage(RdcSwingRdcBundle.getString("NO SUCH DATA"));
          }
          setErrorOffset(0);
        }
      }
      // else: clear (returned null will clear the field)
    }
    return object;
  }


  /**
   * Sets the infofield of this linkpanel to be a drop zone.
   * <p>
   * The default is true.
   *
   * @param dropEnabled true if this is a drop zone, false if not
   */
  public void setDropEnabled(boolean dropEnabled) {
    // make infoField a drop-target
    if (dropEnabled) {
      dropTarget = new DropTarget (infoField, this);
      dropTarget.setDefaultActions(DnDConstants.ACTION_COPY_OR_MOVE);
    }
    else  {
      dropTarget = null;
    }
    updateInfoFieldDropAndColor();
  }

  /**
   * Returns whether the infofield of this linkpanel is a dropzone.
   *
   * @return true if this is a drop zone, false if not
   */
  public boolean isDropEnabled() {
    return dropTarget != null;
  }



  /**
   * Finds an PersistentDomainObject of given class by the key entered in the formfield component.
   * <p>
   * The default implementation just invokes {@link PersistentDomainObject#findByUniqueDomainKey(java.lang.Object)}.
   *
   * @param clazz the object class
   * @param contextDb the domain context
   * @param key the domain key
   * @return the object, null if not found
   *
   * @throws java.lang.InstantiationException
   * @throws java.lang.IllegalAccessException
   */
  @SuppressWarnings("unchecked")
  public T findByKey(Class<T> clazz, DomainContext contextDb, Object key)
         throws InstantiationException, IllegalAccessException {

    return key == null ? null : Pdo.create(clazz, contextDb).findByUniqueDomainKey(key);
  }




  /**
   * Sets the link.
   *
   * @param pdoSearch is the PdoSearch to be used
   * @param linkedId  the original, i.e. current Id of the linked object
   */
  @SuppressWarnings("unchecked")
  public void setLink(PdoSearch<T> pdoSearch, long linkedId)  {

    this.pdoSearch = pdoSearch;
    this.linkedId = 0;

    linkedObject = null;

    if (linkedId != 0 && pdoSearch != null)  {
      try {
        linkedObject = pdoSearch.createPdo().selectCached(linkedId);
        if (linkedObject != null) {
          this.linkedId = linkedObject.getId();
        }
      }
      catch (Exception ex) {
        // treated as "object not found"
        LOGGER.logStacktrace(ex);
      }
    }

    loadObject();
  }


  /**
   * sets the link object (if plugin matches)
   *
   * @param pdo the data object
   */
  @SuppressWarnings("unchecked")
  public void setLink(T pdo)  {
    if (pdo != null && pdoSearch != null && pdoSearch.getPdoClass().isAssignableFrom(pdo.getEffectiveClass()))  {
      setLink(pdoSearch, pdo.getId());
    }
    else  {
      setLink(pdoSearch, 0);
    }
  }


  /**
   * Sets the link with default plugin.
   *
   * @param clazz the class of the linked object, e.g. Konto.class
   * @param context is the db-connection with context
   * @param linkedId  the original, i.e. current Id of the linked object
   * @param keepPlugin is true if keep plugin if already initialized
   */
  public void setLink(Class<T> clazz, DomainContext context, long linkedId, boolean keepPlugin)  {
    try {
      if (context != null && clazz != null)  {
        if (keepPlugin && pdoSearch != null) {
          setLink(pdoSearch, linkedId);
        }
        else  {
          setLink (Rdc.createGuiProvider(Pdo.create(clazz, context)).createPdoSearch(), linkedId);
        }
        return;
      }
    }
    catch (Exception ex) {} // treated as "clear"

    // else clear link
    setLink (null, 0);
  }


  /**
   * Sets the link with default plugin.
   *
   * @param clazz the class of the linked object, e.g. Konto.class
   * @param context is the db-connection with context
   * @param linkedId  the original, i.e. current Id of the linked object
   */
  public void setLink(Class<T> clazz, DomainContext context, long linkedId)  {
    setLink(clazz, context, linkedId, false);
  }


  /**
   * Gets the object Id of the link.
   *
   * @return the object id, 0 = no object linked
   */
  public long getLinkId() {
    if (isFireRunning() && getBinding() == null) {
      // we're in fireValueEntered and no binding
      T obj = getLink();
      linkedId = obj == null ? 0 : obj.getId();
    }
    return linkedId;
  }


  /**
   * Gets the object.
   *
   * @return the data object, null = no object linked
   */
  @SuppressWarnings("unchecked")
  public T getLink() {
    if (isFireRunning() && getBinding() == null) {
      // we're in fireValueEntered and no binding
      Object obj = getFormValue();
      if (obj instanceof PersistentDomainObject) {
        setLink((T) obj);
      }
      else {
        linkedId = 0;
        linkedObject = null;
      }
    }
    return linkedObject;
  }


  /**
   * Gets the search plugin.
   *
   * @return the search plugin
   */
  public PdoSearch<T> getSearchPlugin() {
    return pdoSearch;
  }


  /**
   * Sets columns of the info field.
   *
   * @param col the columns
   */
  public void setInfoColumns(int col) {
    infoField.setColumns(col);
  }

  /**
   * Get columns of info field
   *
   * @return the columns
   */
  public int getInfoColumns() {
    return infoField.getColumns();
  }


  /**
   * Updates both the code- and the info field.
   * Override this if the default does not match your objects behaviour!
   *
   * @param linkedObject the data object
   */
  public void updateCodeAndInfoField(T linkedObject) {
    if (linkedObject instanceof ShortLongText) {
      getFormComponent().setText(((ShortLongText)linkedObject).getShortText());
      infoField.setText(((ShortLongText)linkedObject).getLongText());
    }
    else {
      getFormComponent().setText(linkedObject.toString());
      infoField.setText(Rdc.createGuiProvider(linkedObject).getTreeText());
    }
    getFormComponent().clearValueShownModified();
  }


  /**
   * Sets the infofield's visibility.
   * Sometimes useful if getTreeText() is not appropriate for non-ShortLongText objects.
   *
   * @param visible true if info field is visible (default)
   */
  public void setInfoFieldVisible(boolean visible) {
    infoField.setVisible(visible);
  }

  /**
   * Gets the infofield's visibility.
   *
   * @return true if visible
   */
  public boolean isInfoFieldVisible() {
    return infoField.isVisible();
  }




  @Override
  public void setChangeable(boolean changeable) {
    super.setChangeable(changeable);
    loadObject();   // load again
  }


  @Override
  public void setCellEditorUsage(boolean flag) {
    super.setCellEditorUsage(flag);
    /**
     * disable focus lost on field when used as a celleditor
     */
    editButton.setFocusable(!flag);
    searchButton.setFocusable(!flag);
  }


  /**
   * Loads the object
   */
  protected void loadObject() {
    if (linkedObject == null) {
      infoField.clearText();
      getFormComponent().clearText();
      linkedId = 0;
      editButton.setEnabled(false);
    }
    else  {
      linkedId = linkedObject.getId();
      updateCodeAndInfoField(linkedObject);
      editButton.setEnabled(isChangeable());
    }
    searchButton.setEnabled(isChangeable());

    updateInfoFieldDropAndColor();
  }


  /**
   * Updates the background color of the infofield.
   */
  protected void updateInfoFieldDropAndColor() {
    if (dropTarget != null) {
      if (isChangeable() && linkedId == 0 && pdoSearch != null) {
        // createPdo accepted data flavour
        dndFlavor = new DataFlavor(pdoSearch.getPdoClass(), ReflectionHelper.getClassBaseName(pdoSearch.getPdoClass()));
        dropTarget.setActive(true);     // allow drop here
        infoField.setBackground(PlafUtilities.getInstance().getDropFieldActiveColor());
      }
      else  {
        dropTarget.setActive(false);    // no plugin or object already set: no drop-target
        infoField.setBackground(PlafUtilities.getInstance().getDropFieldInactiveColor());
      }
    }
    else  {
      infoField.setBackground(PlafUtilities.getInstance().getTextFieldInactiveBackgroundColor());
    }
  }


  /**
   * Creates the search dialog.<p>
   * Invoked from {@link #runSearch()}.
   * @return the dialog
   */
  public PdoSearchDialog<T> createSearchDialog() {
    return Rdc.createPdoSearchDialog(this, pdoSearch, (o) -> pdoSearch.getPdoClass().isAssignableFrom(o.getClass()), true, true);
  }



  /**
   * Runs the search
   */
  public void runSearch() {
    if (pdoSearch != null && !searchRunning) {
      pdoSearch.resetSearchCriteria();
      try {
        searchRunning = true;
        @SuppressWarnings("unchecked")
        T obj = (T) createSearchDialog().showDialog();
        if (obj != null) {
          setLink(obj);
          if (!isCellEditorUsage()) {
            fireValueEntered();
            searchButton.transferFocus();
          }
        }
      }
      catch (Exception ex) {
        FormError.showException(RdcSwingRdcBundle.getString("SEARCH FAILED"), ex);
      }
      finally {
        searchRunning = false;
      }
    }
  }


  /**
   * Edits the object
   */
  @SuppressWarnings("unchecked")
  public void runEdit() {
    // modal dialog
    if (linkedObject != null)  {
      if (PdoEditDialogPool.getInstance().editModal(linkedObject) != null) {
        // object was updated, display new text
        loadObject();
        if (!isCellEditorUsage() && isAutoUpdate()) {
          fireValueEntered();   // could be changed somehow
        }
      }
    }
  }


  /**
   * Sets the visibility of the edit button.<br>
   * Some apps don't want the user to edit the object.
   * The default is visible.
   *
   * @param visible true if editbutton is visible
   */
  public void setEditButtonVisible(boolean visible) {
    editButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the edit button.
   *
   * @return true if editbutton is visible
   */
  public boolean isEditButtonVisible() {
    return editButton.isVisible();
  }


  /**
   * Sets the visibility of the search button.<br>
   * The default is visible.
   *
   * @param visible true if searchbutton is visible
   */
  public void setSearchButtonVisible(boolean visible) {
    searchButton.setVisible(visible);
  }

  /**
   * Gets the visibility of the search button.
   *
   * @return true if searchbutton is visible
   */
  public boolean isSearchButtonVisible() {
    return searchButton.isVisible();
  }


  /**
   * Returns the editButton.
   * @return    The editButton
   */
  public org.tentackle.swing.FormButton getEditButton() {
    return editButton;
  }

  /**
   * Returns the searchButton.
   * @return    The searchButton
   */
  public org.tentackle.swing.FormButton getSearchButton() {
    return searchButton;
  }



  // --------------- implements DropTargetListener ----------------------------

  @Override
  public void dragEnter (DropTargetDragEvent event)  {
    if (!isDragAcceptable(event)) {
      event.rejectDrag();
    }
    else  {
      event.acceptDrag(DnDConstants.ACTION_COPY);
    }
  }

  @Override
  public void dragExit (DropTargetEvent event)  {
  }

  @Override
  public void dragOver (DropTargetDragEvent event)  {
    if (!isDragAcceptable(event)) {
      event.rejectDrag();
    }
    // see comment above!
  }

  @Override
  public void dropActionChanged (DropTargetDragEvent event)  {
  }

  @Override
  public void drop (DropTargetDropEvent event)  {
    if (isDropAcceptable(event)) {
      event.acceptDrop(DnDConstants.ACTION_COPY);
      Transferable trans = event.getTransferable();
      try {
        Object transferData = trans.getTransferData(dndFlavor);
        if (transferData instanceof PdoTransferData) {
          @SuppressWarnings("unchecked")
          T object = pdoSearch.createPdo().selectCached(((PdoTransferData) transferData).getId());
          setLink(object);
          if (isAutoUpdate()) {
            fireValueEntered();
          }
        }
      }
      catch (Exception e) {
        FormError.showException(RdcSwingRdcBundle.getString("DROP ERROR:"), e);
      }
      event.dropComplete(true);
    }
    else  {
      event.rejectDrop();
    }
  }


  private boolean isDragAcceptable(DropTargetDragEvent event) {
    return ((event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE) != 0 &&
            event.isDataFlavorSupported(dndFlavor));
  }

  private boolean isDropAcceptable(DropTargetDropEvent event) {
    return ((event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE) != 0 &&
            event.isDataFlavorSupported(dndFlavor));
  }




  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    editButton = new org.tentackle.swing.FormButton();
    infoField = new org.tentackle.swing.StringFormField();
    searchButton = new org.tentackle.swing.FormButton();

    setToolTipText("");
    setLayout(new java.awt.GridBagLayout());

    editButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("edit"));
    editButton.setToolTipText(RdcSwingRdcBundle.getString("EDIT")); // NOI18N
    editButton.setName("edit"); // NOI18N
    editButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        editButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    add(editButton, gridBagConstraints);

    infoField.setEditable(false);
    infoField.setName("info"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
    add(infoField, gridBagConstraints);

    searchButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("search"));
    searchButton.setToolTipText(RdcSwingRdcBundle.getString("SEARCH")); // NOI18N
    searchButton.setName("search"); // NOI18N
    searchButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        searchButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    add(searchButton, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
    runSearch();
  }//GEN-LAST:event_searchButtonActionPerformed

  private void editButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editButtonActionPerformed
    runEdit();
  }//GEN-LAST:event_editButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private org.tentackle.swing.FormButton editButton;
  private org.tentackle.swing.StringFormField infoField;
  private org.tentackle.swing.FormButton searchButton;
  // End of variables declaration//GEN-END:variables

}

