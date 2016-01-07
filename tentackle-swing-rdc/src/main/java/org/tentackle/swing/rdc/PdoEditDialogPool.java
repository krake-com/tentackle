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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.SecurityResult;
import org.tentackle.swing.FormInfo;
import org.tentackle.swing.FormQuestion;
import org.tentackle.swing.FormUtilities;


interface PdoEditDialogPool$Singleton {
  static PdoEditDialogPool INSTANCE = ServiceFactory.createService(PdoEditDialogPool.class, PdoEditDialogPool.class);
}


/**
 * Pool for {@link PdoEditDialog}s.
 * <p>
 * The pool reduces memory consumption and dialog startup time. Furthermore,
 * it makes sure that each object is being edited only once.
 * <p>
 * Notice that the pool is not mt-safe (as it is the case with Swing in general).
 *
 * @author harald
 */
public class PdoEditDialogPool {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static PdoEditDialogPool getInstance() {
    return PdoEditDialogPool$Singleton.INSTANCE;
  }


  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(PdoEditDialogPool.class);


  private final List<PdoEditDialog<?>> dialogList;  // pool of dialogs
  private boolean pdoEditedOnlyOnce;                 // true if same object can be edited only once at a time (true = default)
  private boolean enabled = true;                   // true if pool is enabled, false if no caching


  /**
   * Creates a dialog pool
   */
  public PdoEditDialogPool() {
    dialogList = new ArrayList<>();
    pdoEditedOnlyOnce = true;
  }


  /**
   * Gets the pool's enabled state.
   *
   * @return true if pool is enabled (default)
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets the pool's enabled state.
   *
   * @param enabled true if pool is enabled
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }


  /**
   * Returns whether an object is allowed to be edited only once at a time.
   *
   * @return true if objects must not be edited more than once (default)
   */
  public boolean isPdoEditedOnlyOnce() {
    return pdoEditedOnlyOnce;
  }

  /**
   * Sets whether an object is allowed to be edited only once at a time.
   *
   * @param pdoEditedOnlyOnce  true if objects must not be edited more than once (default)
   */
  public void setPdoEditedOnlyOnce(boolean pdoEditedOnlyOnce) {
    this.pdoEditedOnlyOnce = pdoEditedOnlyOnce;
  }


  /**
   * Determines whether a given object is currently being edited
   * by some other dialog.
   *
   * @param <T> the pdo type
   * @param object the database object
   * @param exceptMe the dialog to exclude from the check, null = none
   * @param comp the optional comparator, null if "equals"
   * @return the dialog that is already editing given object
   */
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> PdoEditDialog<T> isObjectBeingEdited(T object, PdoEditDialog<T> exceptMe,
                                                                                    Comparator<? super PersistentDomainObject<?>> comp) {
    if (object != null && !object.isNew()) {
      for (PdoEditDialog<?> d : dialogList) {
        if (exceptMe != d && d.isVisible() && d.isChangeable() &&
            d.getPdoClass() == object.getEffectiveClass()) {
          PersistentDomainObject<?> editedObject = d.getPdo();
          if (editedObject != null) {
            if (comp != null) {
              if (comp.compare(editedObject, object) == 0) {
                return (PdoEditDialog<T>) d;
              }
            }
            else {
              if (editedObject.equals(object)) {
                return (PdoEditDialog<T>) d;
              }
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Determines whether a given object is currently being edited
   * by some other dialog.
   *
   * @param <T> the pdo type
   * @param object the database object
   * @param exceptMe the dialog to exclude from the check, null = none
   * @return the dialog that is already editing given object
   */
  public <T extends PersistentDomainObject<T>> PdoEditDialog<T> isObjectBeingEdited(T object, PdoEditDialog<T> exceptMe)  {
    return isObjectBeingEdited(object, exceptMe, null);
  }

  /**
   * Determines whether a given object is currently being edited
   * by some other dialog.
   *
   * @param <T> the pdo type
   * @param object the database object
   * @return the dialog that is already editing given object
   */
  public <T extends PersistentDomainObject<T>> PdoEditDialog<T> isObjectBeingEdited(T object) {
    return isObjectBeingEdited(object, null, null);
  }


  /**
   * Checks whether an unused dialog can be used from the pool.
   *
   * @param <T> the pdo type
   * @param objectClass the data object class
   * @param modal true if dialog must be modal
   * @param changeable true if dialog must be changeable, false if view-only dialog
   * @return the dialog, null if no such dialog in pool
   */
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> PdoEditDialog<T> getDialog (Class<T> objectClass, boolean modal, boolean changeable)  {
    if (enabled) {
      // check if dialog already created, not visible and of correct modal-type
      for (PdoEditDialog<?> d: dialogList) {
        if (d != null && d.isModal() == modal && d.isVisible() == false &&
            d.isChangeable() == changeable && d.getPdoClass().equals(objectClass))  {
          return (PdoEditDialog<T>) d;
        }
      }
    }
    // keiner verfügbar
    return null;
  }


  /**
   * Creates a new PdoEditDialog.<br>
   *
   * @param <T> the pdo type
   * @param comp the component to determine the window owner, null if none
   * @param pdo the object template to create a dialog for
   * @param modal true if dialog should be modal
   * @return the created dialog
   */
  public <T extends PersistentDomainObject<T>> PdoEditDialog<T> createPdoEditDialog(Component comp, T pdo, boolean modal) {
    PdoEditDialog<T> dialog = null;
    if (pdo != null) {
      dialog = Rdc.createGuiProvider(pdo).createDialog(comp, modal);
    }
    if (dialog == null) {
      dialog = Rdc.createPdoEditDialog(FormUtilities.getInstance().getParentWindow(comp), pdo, modal);
    }
    return dialog;
  }


  /**
   * Adds a dialog to the pool.
   *
   * @param d the dialog to add
   */
  public void addDialog (PdoEditDialog<?> d) {
    if (d != null && d.getPdoClass() != null && d.getPdoPanel() != null)  {
      dialogList.add(d);
    }
  }


  /**
   * Removes a dialog from the pool.
   *
   * @param d the dialog to remove
   */
  public void removeDialog (PdoEditDialog<?> d)  {
    d.setVisible(false);              // this will remove all listeners too
    d.getContentPane().removeAll();   // alle Components entfernen
    dialogList.remove(d);             // remove from dialogList
  }


  /**
   * Clears the pool.
   */
  public void clear() {
    for (PdoEditDialog<?> d: dialogList) {
      removeDialog(d);
    }
    dialogList.clear();
  }


  /**
   * Disposes all dialogs.
   */
  public void disposeAllDialogs()  {
    for (PdoEditDialog<?> d: dialogList) {
      if (d.isVisible())  {
        d.dispose();
      }
    }
  }


  /**
   * Gets a modal dialog ready for use.<br>
   * Will re-use a pooled dialog if possible, otherwise creates a new one.
   *
   * @param <T>           the pdo type
   * @param comp          the component to determine the owner window for
   * @param object        the database object
   * @param changeable    true if dialog must be changeable, false if view-only dialog
   * @param noPersistence true if NO changes must be made to persistance layer
   * <p>
   * @return the object
   */
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> T useModalDialog(Component comp,
                                                                T object,
                                                                boolean changeable,
                                                                boolean noPersistence) {

    if (object != null) {

      PdoEditDialog<T> d = null;

      if (pdoEditedOnlyOnce && changeable) {
        // if only one dialog at a time for editing the object
        d = isObjectBeingEdited(object);
        if (d != null)  {
          if (d.isModal() == false) {
            d.dispose();        // close it first.
            d.setModal(true);   // make it modal.
          }
          else  {
            d.toFront();  // bring to front and set object below (could be changed)
          }
        }
      }

      if (d == null) {
        // no object-related dialog, try to find some for objects class
        d = getDialog(object.getEffectiveClass(), true, changeable);
      }

      if (d == null)  {
        // no such dialog: create new one
        d = createPdoEditDialog(comp, object, true);
        if (changeable == false) {
          d.setChangeable(false);
        }
        addDialog(d);
      }
      else  {
        // dialog exists already
        d.setPdo(object);
      }

      // show Dialog and wait for dispose
      object = d.showDialog(noPersistence);
    }
    return object;
  }


  /**
   * Gets a modal dialog ready for use. Will re-use a pooled dialog if possible, otherwise creates a new one.
   * <p>
   * @param <T>        the pdo type
   * @param comp       the component to determine the owner window for
   * @param object     the database object
   * @param changeable true if dialog must be changeable, false if view-only dialog
   * <p>
   * @return the object
   */
  public <T extends PersistentDomainObject<T>> T useModalDialog(Component comp, T object, boolean changeable) {
    return useModalDialog(comp, object, changeable, false);
  }


  /**
   * Gets a modal dialog ready for use. Will re-use a pooled dialog if possible, otherwise creates a new one.
   * <p>
   * @param <T>        the pdo type
   * @param object     the database object
   * @param changeable true if dialog must be changeable, false if view-only dialog
   * <p>
   * @return the dialog, never null
   */
  public <T extends PersistentDomainObject<T>> T useModalDialog(T object, boolean changeable) {
    return useModalDialog(null, object, changeable, false);
  }


  /**
   * Gets a non-modal dialog ready for use. Will re-use a pooled dialog if possible, otherwise creates a new one.
   * <p>
   * @param <T>                   the pdo type
   * @param comp                  the component to determine the owner window for
   * @param pdo                   the PDO
   * @param changeable            true if dialog must be changeable, false if view-only dialog
   * @param noPersistence         true if NO changes must be made to persistance layer
   * @param disposeOnDeleteOrSave true if dispose dialog after delete or save
   * <p>
   * @return the dialog, never null
   */
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> PdoEditDialog<T> useNonModalDialog(Component comp,
                                                                                  T pdo,
                                                                                  boolean changeable,
                                                                                  boolean noPersistence,
                                                                                  boolean disposeOnDeleteOrSave) {

    if (pdo != null) {

      PdoEditDialog<T> d = null;

      if (pdoEditedOnlyOnce && changeable) {
        // if only one dialog at a time for editing the object
        d = isObjectBeingEdited(pdo);
        if (d != null)  {
          if (d.isModal()) {
            // if d.isModal() something is wrong in the app-logic!
            throw new IllegalStateException("dialog is modal?");
          }
          d.toFront();                    // bring to front and set object below (could be changed)
        }
      }

      if (d == null) {
        // no object-related dialog, try to find some for objects class
        d = getDialog(pdo.getEffectiveClass(), false, changeable);
      }

      if (d == null)  {
        // no such dialog: create new one
        d = createPdoEditDialog(comp, pdo, false);
        if (changeable == false) {
          d.setChangeable(false);
        }
        addDialog(d);
      }
      else  {
        // dialog already exists, reuse it
        d.setPdo(pdo);
      }

      // show Dialog
      d.setDisposeOnDeleteOrSave(disposeOnDeleteOrSave);
      if (d.isVisible() == false) {
        d.showDialog(noPersistence); // non-modal
      }
      return d;
    }
    return null;
  }

  /**
   * Gets a non-modal dialog ready for use. Will re-use a pooled dialog if possible, otherwise creates a new one.
   * <p>
   * @param <T>                   the pdo type
   * @param object                the database object
   * @param changeable            true if dialog must be changeable, false if view-only dialog
   * @param disposeOnDeleteOrSave true if dispose dialog after delete or save
   * <p>
   * @return the dialog, never null
   */
  public <T extends PersistentDomainObject<T>> PdoEditDialog<T> useNonModalDialog(T object, boolean changeable,
                                                                                  boolean disposeOnDeleteOrSave) {
    return useNonModalDialog(null, object, changeable, false, disposeOnDeleteOrSave);
  }

  /**
   * Gets a non-modal dialog ready for use. Will re-use a pooled dialog if possible, otherwise creates a new one.
   * <p>
   * @param <T>        the pdo type
   * @param comp       the component to determine the owner window for
   * @param object     the database object
   * @param changeable true if dialog must be changeable, false if view-only dialog
   * <p>
   * @return the dialog, never null
   */
  public <T extends PersistentDomainObject<T>> PdoEditDialog<T> useNonModalDialog(Component comp, T object,
                                                                                  boolean changeable) {
    return useNonModalDialog(comp, object, changeable, false, false);
  }

  /**
   * Gets a non-modal dialog ready for use.
   * Will re-use a pooled dialog if possible, otherwise creates a new one.
   *
   * @param <T> the pdo type
   * @param object the database object
   * @param changeable true if dialog must be changeable, false if view-only dialog
   * @return the dialog, never null
   */
  public <T extends PersistentDomainObject<T>> PdoEditDialog<T> useNonModalDialog(T object, boolean changeable)  {
    return useNonModalDialog(null, object, changeable, false, false);
  }




  /**
   * Shows an {@code Pdo} in a non-modal dialog.
   *
   * @param object the Pdo to view-only
   * @param comp optional component to determine the window owner, null = no owner
   */
  public <T extends PersistentDomainObject<T>> void view(T object, Component comp) {
    useNonModalDialog(comp, object, false);
  }

  /**
   * Shows an {@code Pdo} in a non-modal dialog.
   *
   * @param object the Pdo to view-only
   */
  public <T extends PersistentDomainObject<T>>void view(T object) {
    useNonModalDialog(null, object, false);
  }

  /**
   * Shows an {@code Pdo} in a modal dialog.
   *
   * @param object the Pdo to view-only
   * @param comp optional component to determine the window owner, null = no owner
   */
  public <T extends PersistentDomainObject<T>>void viewModal(T object, Component comp) {
    useModalDialog(comp, object, false);
  }

  /**
   * Shows an {@code Pdo} in a modal dialog.
   *
   * @param object the Pdo to view-only
   */
  public <T extends PersistentDomainObject<T>>void viewModal(T object) {
    useModalDialog(null, object, false);
  }


  /**
   * Edits an {@code Pdo} in a non-modal dialog.
   *
   * @param object the Pdo to edit
   * @param comp optional component to determine the window owner, null = no owner
   * @param disposeOnDeleteOrSave true if dispose dialog after delete or save
   */
  public <T extends PersistentDomainObject<T>> void edit(T object, Component comp, boolean disposeOnDeleteOrSave) {
    useNonModalDialog(comp, object, true, false, disposeOnDeleteOrSave);
  }

  /**
   * Edits an {@code Pdo} in a non-modal dialog.
   *
   * @param object the Pdo to edit
   * @param disposeOnDeleteOrSave true if dispose dialog after delete or save
   */
  public <T extends PersistentDomainObject<T>> void edit(T object, boolean disposeOnDeleteOrSave) {
    edit(object, null, disposeOnDeleteOrSave);
  }

  /**
   * Edits an {@code Pdo} in a non-modal dialog.
   *
   * @param object the Pdo to edit
   */
  public <T extends PersistentDomainObject<T>> void edit(T object) {
    edit(object, false);
  }


  /**
   * Edits an {@code Pdo} in a modal dialog.
   *
   * @param <T>
   * @param object the Pdo to edit
   * @param comp optional component to determine the window owner, null = no owner
   *
   * @return the (possibly reloaded) object, null if cancel.
   */
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> T editModal(T object, Component comp) {
    return useModalDialog(comp, object, true);
  }

  /**
   * Edits an {@code Pdo} in a modal dialog.
   *
   * @param <T>
   * @param object the Pdo to edit
   *
   * @return the (possibly reloaded) object, null if cancel.
   */
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> T editModal(T object) {
    return useModalDialog(null, object, true);
  }


  /**
   * Deletes an {@code Pdo}.<br>
   * The user will be prompted for confirmation.
   * If the delete fails, an error message will be displayed.
   *
   * @param object the Pdo to delete
   * @return true if deleted, false if user aborted or delete error.
   */
  public boolean delete(PersistentDomainObject<?> object) {

    if (FormQuestion.yesNo(MessageFormat.format(
            "Are you sure to delete {0} {1}?",
            object.getSingular(), object))) {

      try {
        if (!object.isRemovable()) {
          throw new PdoRuntimeException("object is not allowed to be removed");
        }
        SecurityResult sr = SecurityFactory.getInstance().getSecurityManager().evaluate(
                object.getBaseContext(), SecurityFactory.getInstance().getWritePermission(), object.getClassId(), object.getId());
        if (!sr.isAccepted()) {
          throw new PdoRuntimeException(sr.explain("you are not allowed to remove this object"));
        }
        try {
          object.delete();
        }
        catch (RuntimeException e) {
          throw new PdoRuntimeException("couldn't delete", e);
        }
        return true;
      }
      catch (Exception e) {
        LOGGER.logStacktrace(Logger.Level.WARNING, e);
        FormInfo.show(e.getMessage());
      }
    }
    return false;
  }

}