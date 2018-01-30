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

// Created on September 1, 2002, 4:28 PM

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
import java.text.MessageFormat;
import org.tentackle.bind.Binding;
import org.tentackle.bind.BindingException;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.swing.FormComponent;
import org.tentackle.swing.FormError;
import org.tentackle.swing.FormFieldComponentPanel;
import org.tentackle.swing.FormQuestion;
import org.tentackle.swing.FormUtilities;
import org.tentackle.swing.StringFormField;
import org.tentackle.swing.plaf.PlafUtilities;



/**
 * A panel containing a non-editable FormField representing showing the short text (unique key)
 * of the data object, an optional info field (long text) and buttons for editing, search (link)
 * and clear (unlink).
 *
 * @param <T> the pdo type
 * @see PdoFieldPanel
 */
@SuppressWarnings("serial")
public class PdoLinkPanel<T extends PersistentDomainObject<T>> extends FormFieldComponentPanel implements DropTargetListener {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(PdoLinkPanel.class);

  private PdoSearch<T>            pdoSearch;              // search search plugin
  private long                    linkedId;               // the original Id of the object
  private T                       linkedObject;           // the linked Object, null = none
  private DataFlavor              dndFlavor;              // DnD Flavor
  private DropTarget              dropTarget;             // droptarget
  private boolean                 dropEnabled;            // true if drop enabled
  private boolean                 changeable;             // true if field is changeable


  /**
   * Creates an application database object link panel.
   */
  public PdoLinkPanel() {
    initComponents();
    dropEnabled = true;
    changeable = true;
    setFormComponent(new StringFormField());
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
      editButton.setName(name + "/edit");
      linkButton.setName(name + "/link");
    }
    else  {
      ((Component) getFormComponent()).setName("key");
      editButton.setName("edit");
      linkButton.setName("link");
    }
  }


  @Override
  public void setFormComponent(FormComponent comp) {
    FormComponent oldComponent = getFormComponent();
    if (oldComponent != null) {
      remove((Component)oldComponent);
    }
    super.setFormComponent(comp);
    comp.setChangeable(false);

    GridBagConstraints gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 1);
    add((Component)comp, gridBagConstraints);

    // make objectField a drop-target
    setDropEnabled(dropEnabled);
  }




  /**
   * Sets the component of this linkpanel to be a drop zone.
   * <p>
   * The default is true.
   *
   * @param dropEnabled true if this is a drop zone, false if not
   */
  public void setDropEnabled(boolean dropEnabled) {
    // make infoField a drop-target
    this.dropEnabled = dropEnabled;
    if (dropEnabled) {
      dropTarget = new DropTarget ((Component) getFormComponent(), this);
      dropTarget.setDefaultActions(DnDConstants.ACTION_COPY_OR_MOVE);
    }
    else  {
      dropTarget = null;
    }
    updateFormComponentDropAndColor();
  }


  /**
   * Returns whether the component of this linkpanel is a dropzone.
   *
   * @return true if this is a drop zone, false if not
   */
  public boolean isDropEnabled() {
    return dropEnabled;
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
      try {
        // check if setter bound and accepts an PersistentDomainObject as a single argument
        Class<?> clazz = binding.getMember().getType();
        if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
          T pdo = getLink();
          FormUtilities.getInstance().doValidate(this);
          return pdo;
        }
      }
      catch (Exception ex) {
        throw new BindingException("could not determine type for " + binding, ex);
      }
    }
    // else default: just text
    return super.getFormValue();
  }



  /**
   * Sets the link.
   *
   * @param pdoSearch the PdoSearch to be used
   * @param linkedId the original, i.e. current ID of the linked object
   */
  @SuppressWarnings("unchecked")
  public void setLink(PdoSearch<T> pdoSearch, long linkedId)  {

    this.pdoSearch   = pdoSearch;
    this.linkedId = linkedId;

    if (linkedId == 0 || pdoSearch == null)  {
      linkedObject  = null;
      this.linkedId = 0;
    }
    else  {
      try {
        linkedObject = pdoSearch.createPdo().selectCached(linkedId);
      }
      catch (Exception ex) {
        // treated as "object not found"
        LOGGER.warning("loading PDO failed", ex);
      }
      if (linkedObject == null) {
        this.linkedId = 0;
        fireValueEntered();     // cut link!
      }
    }
    loadObject();
  }


  /**
   * Sets the link object (if plugin matches).
   *
   * @param object the database object
   */
  public void setLink(T object)  {
    if (object != null && pdoSearch != null && pdoSearch.getPdoClass().equals(object.getEffectiveClass()))  {
      setLink(pdoSearch, object.getId());
    }
    else  {
      setLink(pdoSearch, 0);
    }
  }


  /**
   * Set the link with default plugin.
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
    catch (RuntimeException ex) {
      // treated as "clear"
      LOGGER.warning("installing search plugin failed", ex);
    }

    // else clear link
    setLink (null, 0);
  }

  /**
   * Set the link with default plugin.
   *
   * @param clazz the class of the linked object, e.g. Konto.class
   * @param context is the db-connection with context
   * @param linkedId  the original, i.e. current Id of the linked object
   */
  public void setLink(Class<T> clazz, DomainContext context, long linkedId)  {
    setLink(clazz, context, linkedId, false);
  }


  /**
   * Gets the object ID of the link.
   *
   * @return the object ID, 0 if none
   */
  public long getLinkId() {
    return linkedId;
  }


  /**
   * Gets the linked object.
   *
   * @return the object, null if none
   */
  public T getLink() {
    return linkedObject;
  }


  @Override
  public void setChangeable(boolean flag) {
    if (isHonourChangeable()) {
      this.changeable = flag;
      loadObject();   // load again
    }
  }

  @Override
  public boolean isChangeable() {
    return changeable;
  }


  @Override
  public boolean requestFocusInWindow() {
    if (isCellEditorUsage()) {
      return super.requestFocusInWindow();
    }
    else  {
      if (linkedObject == null) {
        return linkButton.requestFocusInWindow();
      }
      else  {
        return editButton.isVisible() && editButton.requestFocusInWindow();
      }
    }
  }


  @Override
  public void setCellEditorUsage(boolean flag) {
    super.setCellEditorUsage(flag);
    /**
     * disable focus lost on datefield when used as a celleditor
     */
    editButton.setFocusable(!flag);
    linkButton.setFocusable(!flag);
  }



  // -------------------- implements DropTargetListener ------------------------

  @Override
  public void dragEnter (DropTargetDragEvent event)  {
    if (!isDragAcceptable(event)) {
      event.rejectDrag();
    }
    /**
     * we can't do this because of a bug in Win32-JVM.
     * see: http://developer.java.sun.com/developer/bugParade/bugs/4217416.html
    else  {
      event.acceptDrag(DnDConstants.ACTION_COPY);
    }
    */
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



  /**
   * loads the object
   */
  private void loadObject() {
    linkButton.setEnabled(isChangeable());
    if (linkedObject == null) {
      getFormComponent().clearText();
      linkedId = 0;
      linkButton.setIcon(PlafUtilities.getInstance().getIcon("link"));
      linkButton.setToolTipText(RdcSwingRdcBundle.getString("LINK"));
      editButton.setEnabled(false);
    }
    else  {
      linkedId = linkedObject.getId();
      getFormComponent().setText(linkedObject.toString());
      linkButton.setIcon(PlafUtilities.getInstance().getIcon("unlink"));
      linkButton.setToolTipText(RdcSwingRdcBundle.getString("UNLINK"));
      editButton.setEnabled(isChangeable());
    }

    updateFormComponentDropAndColor();
  }


  private void updateFormComponentDropAndColor() {
    if (dropTarget != null) {
      if (isChangeable() && linkedId == 0 && pdoSearch != null) {
        // createPdo accepted data flavour
        dndFlavor = new DataFlavor(pdoSearch.getPdoClass(), ReflectionHelper.getClassBaseName(pdoSearch.getPdoClass()));
        dropTarget.setActive(true);     // allow drop here
        ((Component)getFormComponent()).setBackground(PlafUtilities.getInstance().getDropFieldActiveColor());
      }
      else  {
        dropTarget.setActive(false);    // no plugin or object already set: no drop-target
        ((Component)getFormComponent()).setBackground(PlafUtilities.getInstance().getDropFieldInactiveColor());
      }
    }
    else  {
      ((Component)getFormComponent()).setBackground(PlafUtilities.getInstance().getTextFieldInactiveBackgroundColor());
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
  @SuppressWarnings("unchecked")
  public void runSearch() {
    // search and link to new object
    if (pdoSearch != null) {
      try {
        linkedObject = (T) createSearchDialog().showDialog();
      }
      catch (Exception ex) {
        FormError.showException(RdcSwingRdcBundle.getString("SEARCH FAILED"), ex);
      }
      loadObject();
      if (isAutoUpdate()) {
        fireValueEntered();
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
        if (isAutoUpdate()) {
          fireValueEntered();   // could be changed somehow
        }
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
    java.awt.GridBagConstraints gridBagConstraints;

    linkButton = new org.tentackle.swing.FormButton();
    editButton = new org.tentackle.swing.FormButton();

    setToolTipText("");
    setLayout(new java.awt.GridBagLayout());

    linkButton.setFormTraversable(true);
    linkButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("link"));
    linkButton.setToolTipText(RdcSwingRdcBundle.getString("LINK")); // NOI18N
    linkButton.setName("link"); // NOI18N
    linkButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        linkButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    add(linkButton, gridBagConstraints);

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
  }// </editor-fold>//GEN-END:initComponents

  private void editButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editButtonActionPerformed
    runEdit();
  }//GEN-LAST:event_editButtonActionPerformed

  private void linkButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linkButtonActionPerformed
    if (linkedObject == null) {
      runSearch();
    }
    else  {
      // unlink object
      if (FormQuestion.yesNo(MessageFormat.format(RdcSwingRdcBundle.getString("REMOVE LINK TO {0} {1}?"),
                linkedObject.getSingular(), linkedObject))) {
        linkedObject = null;
        loadObject();
        if (isAutoUpdate()) {
          fireValueEntered();
        }
      }
    }
  }//GEN-LAST:event_linkButtonActionPerformed



  // Variables declaration - do not modify//GEN-BEGIN:variables
  private org.tentackle.swing.FormButton editButton;
  private org.tentackle.swing.FormButton linkButton;
  // End of variables declaration//GEN-END:variables

}

