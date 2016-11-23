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

import java.awt.AWTEvent;
import java.awt.CardLayout;
import java.awt.EventQueue;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import javax.swing.event.EventListenerList;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.FormatHelper;
import org.tentackle.common.StringHelper;
import org.tentackle.pdo.ConstraintException;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.NotFoundException;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.rdc.security.SecurityDialogFactory;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.SecurityManager;
import org.tentackle.security.SecurityResult;
import org.tentackle.security.permissions.EditPermission;
import org.tentackle.security.permissions.ViewPermission;
import org.tentackle.swing.FormContainer;
import org.tentackle.swing.FormDialog;
import org.tentackle.swing.FormError;
import org.tentackle.swing.FormInfo;
import org.tentackle.swing.FormPanel;
import org.tentackle.swing.FormQuestion;
import org.tentackle.swing.plaf.PlafUtilities;
import org.tentackle.validate.ValidationFailedException;
import org.tentackle.validate.ValidationResult;


/**
 * Edit dialog for {@link PersistentDomainObject}s.
 * <p>
 * The dialog provides the following generic features:
 * <ul>
 * <li>edit</li>
 * <li>view-only</li>
 * <li>search (with search preset)</li>
 * <li>print</li>
 * <li>new</li>
 * <li>delete</li>
 * <li>save</li>
 * <li>load by id</li>
 * <li>edit security rules</li>
 * <li>copy object</li>
 * <li>insert object</li>
 * <li>duplicate object (insert a copy)</li>
 * <li>drag and drop</li>
 * <li>and more...</li>
 * </ul>
 *
 *
 * @param <T> the pdo type
 * @author harald
 */
public class PdoEditDialog<T extends PersistentDomainObject<T>> extends FormDialog implements DropTargetListener, KeyEventDispatcher {

  private static final long serialVersionUID = 3026626637222875722L;

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(PdoEditDialog.class);

  /** global default: true if object should be cleared after save **/
  public static boolean defaultNewAfterSave = false;

  // transaction names
  /** tx name for save **/
  public static final String TX_SAVE    = "dialog save";
  /** tx name for delete **/
  public static final String TX_DELETE  = "dialog delete";

  // Action Strings
  /** action string for "new" **/
  public static final String ACTION_NEW    = "new";
  /** action string for "search" **/
  public static final String ACTION_SEARCH = "search";
  /** action string for "delete" **/
  public static final String ACTION_DELETE = "delete";
  /** action string for "save" **/
  public static final String ACTION_SAVE   = "save";
  /** action string for "cancel" **/
  public static final String ACTION_CANCEL = "cancel";
  /** action string for "previous" **/
  public static final String ACTION_PREVIOUS = "previous";
  /** action string for "next" **/
  public static final String ACTION_NEXT = "next";


  private static final String NO_ACCESS_PANEL = "noAccess";
  private static final String NO_INFO_PANEL   = "noInfo";
  private static final String DATA_PANEL      = "dataPanel";



  private DomainContext                           context;                  // current database application context
  private DomainContext                           baseContext;              // mininum context for this object
  private T                                       pdo;                      // PDO being edited/shown
  private Class<T>                                pdoClass;                 // class of PDO
  private T                                       lastPdo;                  // saved object
  private GuiProvider<T>                          guiProvider;              // the PDO's GUI provider
  private PdoPanel<T>                             pdoPanel;                 // object's panel
  private EventListenerList                       listenerList;             // for actionPerformed-Listeners.
  private DataFlavor                              dndFlavor;                // DnD Flavor
  private Boolean                                 disposeOnDeleteOrSave;    // true if close dialog if delete or save pressed
  private boolean                                 noPersistance;            // true = dont save!
  private boolean                                 newAfterSave;             // clearPdo() after save() ?
  private List<T>                                 linkedPdoList;            // List of linked PDOs connected to this dialog
  private int                                     linkedPdoIndex;           // index of current linked pdo in list (-1 if no list or not in list)
  private long                                    txVoucher;                // current transaction voucher

  // button availability
  private boolean previousButtonAvailable = true;
  private boolean nextButtonAvailable     = true;
  private boolean idButtonAvailable       = true;
  private boolean browserButtonAvailable  = true;
  private boolean newButtonAvailable      = true;
  private boolean saveButtonAvailable     = true;
  private boolean deleteButtonAvailable   = true;
  private boolean searchButtonAvailable   = true;
  private boolean printButtonAvailable    = false;

  // object permissions
  private boolean editable;                     // true if updating object is allowed
  private boolean viewable;                     // true if viewing object is allowed
  private boolean deletable;                    // true if deleting the object is allowed





  /**
   * Creates a database object dialog.
   *
   * @param owner the owner window, null if no owner
   * @param pdo is the database object
   * @param modal is true if modal, else false
   */
  @SuppressWarnings("unchecked")
  protected PdoEditDialog(Window owner, T pdo, boolean modal) {

    super(owner, null, modal);

    enableEvents(AWTEvent.WINDOW_EVENT_MASK); // for process.. below

    newAfterSave = defaultNewAfterSave;
    linkedPdoIndex = -1;
    listenerList = new EventListenerList();

    initComponents();

    setTooltipDisplay(tipAndErrorPanel);

    buttonPanel.setBackground(PlafUtilities.getInstance().getDropFieldActiveColor());

    // createPdo object's panel
    guiProvider = Rdc.createGuiProvider(pdo);
    FormContainer panel = guiProvider.createPanel();

    if (panel != null)  {
      // override the title if panel brings its own
      String title = panel.getTitle();
      if (title != null)  {
        setTitle(title);
      }
    }

    if (panel instanceof PdoPanel)  {
      // standard case
      pdoPanel = (PdoPanel<T>) panel;
      dataPanel.add(pdoPanel, DATA_PANEL);
      ((CardLayout) dataPanel.getLayout()).show(dataPanel, DATA_PANEL);

      // tell that we are the parent
      pdoPanel.setPdoEditDialog(this);

      // set the object
      if (!setPdo(pdo) && pdo != null) {
        // Panel loeschen, falls was drin ist
        setPdo(Pdo.create(pdo));
      }

      // createPdo drop target. We use the button-panel because this is
      // seen on all types of dialogs.
      DropTarget target = new DropTarget (buttonPanel, this);
      target.setDefaultActions(DnDConstants.ACTION_COPY_OR_MOVE);

      // createPdo accepted data flavour
      dndFlavor = pdo == null ? null : new DataFlavor(pdo.getEffectiveClass(), pdo.getClassBaseName());

      buttonPanel.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          checkPopup(e);
        }
        @Override
        public void mouseReleased(MouseEvent e) {
          checkPopup(e);
        }
      });
    }

    else  {
      // no PdoPanel?
      if (panel != null) {
        // panel just shows the data, no editing possible
        dataPanel.add((FormPanel) panel, DATA_PANEL);
        ((CardLayout)dataPanel.getLayout()).show(dataPanel, DATA_PANEL);
        panel.setChangeable(false);
      }
      else  {
        // no suitable panel found
        ((CardLayout)dataPanel.getLayout()).show(dataPanel, NO_INFO_PANEL);
      }

      // disable all buttons
      previousButton.setVisible(false);
      nextButton.setVisible(false);
      idButton.setVisible(false);
      browserButton.setVisible(false);
      newButton.setVisible(false);
      saveButton.setVisible(false);
      deleteButton.setVisible(false);
      searchButton.setVisible(false);
      printButton.setVisible(false);
    }
  }




  /**
   * Gets the general availability of the browser button.
   *
   * @return true if available
   */
  public boolean isBrowserButtonAvailable() {
    return browserButtonAvailable;
  }

  /**
   * Sets the general availability of the browser button.
   *
   * @param browserButtonAvailable true if available as needed, false if never visible
   */
  public void setBrowserButtonAvailable(boolean browserButtonAvailable) {
    this.browserButtonAvailable = browserButtonAvailable;
  }


  /**
   * Gets the general availability of the delete button.
   *
   * @return true if available
   */
  public boolean isDeleteButtonAvailable() {
    return deleteButtonAvailable;
  }

  /**
   * Sets the general availability of the delete button.
   *
   * @param deleteButtonAvailable true if available as needed, false if never visible
   */
  public void setDeleteButtonAvailable(boolean deleteButtonAvailable) {
    this.deleteButtonAvailable = deleteButtonAvailable;
  }


  /**
   * Gets the general availability of the id button.
   *
   * @return true if available
   */
  public boolean isIdButtonAvailable() {
    return idButtonAvailable;
  }

  /**
   * Sets the general availability of the id button.
   *
   * @param idButtonAvailable true if available as needed, false if never visible
   */
  public void setIdButtonAvailable(boolean idButtonAvailable) {
    this.idButtonAvailable = idButtonAvailable;
  }


  /**
   * Gets the general availability of the new button.
   *
   * @return true if available
   */
  public boolean isNewButtonAvailable() {
    return newButtonAvailable;
  }

  /**
   * Sets the general availability of the new button.
   *
   * @param newButtonAvailable true if available as needed, false if never visible
   */
  public void setNewButtonAvailable(boolean newButtonAvailable) {
    this.newButtonAvailable = newButtonAvailable;
  }


  /**
   * Gets the general availability of the next button.
   *
   * @return true if available
   */
  public boolean isNextButtonAvailable() {
    return nextButtonAvailable;
  }

  /**
   * Sets the general availability of the next button.
   *
   * @param nextButtonAvailable true if available as needed, false if never visible
   */
  public void setNextButtonAvailable(boolean nextButtonAvailable) {
    this.nextButtonAvailable = nextButtonAvailable;
  }


  /**
   * Gets the general availability of the previous button.
   *
   * @return true if available
   */
  public boolean isPreviousButtonAvailable() {
    return previousButtonAvailable;
  }

  /**
   * Sets the general availability of the previous button.
   *
   * @param previousButtonAvailable true if available as needed, false if never visible
   */
  public void setPreviousButtonAvailable(boolean previousButtonAvailable) {
    this.previousButtonAvailable = previousButtonAvailable;
  }


  /**
   * Gets the general availability of the print button.
   *
   * @return true if available
   */
  public boolean isPrintButtonAvailable() {
    return printButtonAvailable;
  }

  /**
   * Sets the general availability of the print button.
   *
   * @param printButtonAvailable true if available as needed, false if never visible
   */
  public void setPrintButtonAvailable(boolean printButtonAvailable) {
    this.printButtonAvailable = printButtonAvailable;
  }


  /**
   * Gets the general availability of the save button.
   *
   * @return true if available
   */
  public boolean isSaveButtonAvailable() {
    return saveButtonAvailable;
  }

  /**
   * Sets the general availability of the save button.
   *
   * @param saveButtonAvailable true if available as needed, false if never visible
   */
  public void setSaveButtonAvailable(boolean saveButtonAvailable) {
    this.saveButtonAvailable = saveButtonAvailable;
  }


  /**
   * Gets the general availability of the search button.
   *
   * @return true if available
   */
  public boolean isSearchButtonAvailable() {
    return searchButtonAvailable;
  }

  /**
   * Sets the general availability of the search button.
   *
   * @param searchButtonAvailable true if available as needed, false if never visible
   */
  public void setSearchButtonAvailable(boolean searchButtonAvailable) {
    this.searchButtonAvailable = searchButtonAvailable;
  }




  /**
   * Sets a list of linked PDOs.<p>
   * If this dialog is linked to a list of pdos, two navigation
   * buttons appear to walk through the list.
   * @param linkedPdoList the list of pdos, null if none (default)
   */
  public void setLinkedPdoList(List<T> linkedPdoList) {
    this.linkedPdoList = linkedPdoList;
    updateObjectListIndex();
    setupButtons();
  }


  /**
   * Gets the linked pdo list.
   * @return the list of pdos, null if none (default)
   */
  public List<T> getLinkedPdoList() {
    return linkedPdoList;
  }


  /**
   * Gets the linked pdo list list index.
   * @return the index of the object being displayed in the linkedPdoList, -1 if not in list
   */
  public int getLinkedPdoIndex() {
    return linkedPdoIndex;
  }


  /**
   * Clears the pdo.<br>
   * Implemented by replacing the current pdo with a new pdo.
   * The current pdo is saved.
   *
   * @see #getLastPdo()
   */
  @SuppressWarnings("unchecked")
  public void clearPdo () {
    if (pdo != null) {
      // altes Object sichern
      lastPdo = pdo;
      // neues leeres Objekt erzeugen
      pdo = Pdo.create(lastPdo);
      // Objekt setzen
      setPdo (pdo);
    }
  }


  /**
   * Returns whether the pdo is viewable.
   *
   * @return true if viewable
   */
  public boolean isPdoViewable() {
    return viewable;
  }


  /**
   * Returns whether the pdo is editable.
   *
   * @return true if dialog consideres pdo as modifyable by the user
   */
  public boolean isPdoEditable() {
    return editable;
  }


  /**
   * Returns whether the pdo is deletable.
   *
   * @return true if dialog consideres pdo removeable by the user
   */
  public boolean isPdoDeletable() {
    return deletable;
  }


  /**
   * Sets the object.
   *
   * @param pdo the database object (must be the same class as current object!)
   * @param requestFocus true if initial focus should be requested
   * @return true if object accepted
   * @see PdoPanel#setInitialFocus
   */
  @SuppressWarnings("unchecked")
  public boolean setPdo (T pdo, boolean requestFocus)  {

    if (pdoPanel == null)  {
      return false;
    }

    if (this.pdo != null && !this.pdo.equals(pdo))  {
      pdoPanel.prepareCancel();  // do any cleanup if setobject called more than once!
      clearEditedBy();
    }

    // get the panel object to be displayed. Can be different (e.g. address translates to a company)
    if (pdo != null) {
      guiProvider = Rdc.createGuiProvider(pdo);
      pdo = guiProvider.getPanelObject();
      if (pdo != null && pdo.isCached())  {
        // may be from cache: reload for sure
        pdo = pdo.reload();
      }
    }

    if (pdo != null && (pdoClass == null || pdoClass == pdo.getEffectiveClass()))  {

      this.pdo          = pdo;
      this.context      = pdo.getDomainContext();
      this.pdoClass     = pdo.getEffectiveClass();
      this.baseContext  = pdo.getBaseContext();

      determinePermissions();

      // get token lock, if not only show item
      if (!pdo.isNew() && editable)  {

        if (PdoEditDialogPool.getInstance().isPdoEditedOnlyOnce()) {
          // check if object is being edited by another dialog.
          final PdoEditDialog<T> otherDialog = PdoEditDialogPool.getInstance().isObjectBeingEdited(pdo, this);
          if (otherDialog != null)  {  // writeallowed means possibly softlock-token set!
            FormInfo.show(RdcSwingRdcBundle.getString("OBJECT IS USED IN ANOTHER DIALOG. PLEASE FINISH THAT DIALOG FIRST!"));
            EventQueue.invokeLater(otherDialog::toFront);
            editable = false;
          }
        }

        if (editable) {
          if (pdo.getTokenLockTimeout() > 0 && !pdo.isTokenLockedByMe())  {
            String msg = applyLock(pdo);
            if (msg != null) {
              FormInfo.show(msg);
              // token refused: disable write and delete
              editable  = false;
              deletable = false;
            }
          }
        }
      }

      pdoPanel.setChangeable(editable);

      StringBuilder idText = new StringBuilder(Long.toString(pdo.getId()));
      while(idText.length() < 8)  {
        idText.insert(0, ' ');
      }
      idButton.setText(idText.toString());

      updateObjectListIndex();
      setupButtons();

      if (disposeOnDeleteOrSave == null) {
        if (isModal()) {
          setDisposeOnDeleteOrSave(true);  // need to setDisposeOnDeleteOrSave() AFTER setPdo() if otherwise
        }
        else  {
          setDisposeOnDeleteOrSave(false); // need to setDisposeOnDeleteOrSave() AFTER setPdo() if otherwise
        }
      }

      if (viewable) {
        ((CardLayout)dataPanel.getLayout()).show(dataPanel, DATA_PANEL);
      }
      else          {
        ((CardLayout)dataPanel.getLayout()).show(dataPanel, NO_ACCESS_PANEL);
      }

      if (!pdoPanel.setPdo(pdo))  {
        // the panel refused the object for whatever reason.
        return false;
      }

      // set the window title
      String title = pdoPanel.getTitle();
      if (title != null) {
        setTitle(title);
      }

      // set initial focus
      if (requestFocus) {
        pdoPanel.setInitialFocus();
      }

      EventQueue.invokeLater(this::saveValues);

      tipAndErrorPanel.clearErrors();   // clear any errors in tip-panel

      return true;  // object accepted
    }

    clearPdo();

    return false;   // object refused
  }


  /**
   * Sets the object and requests the initial focus.
   *
   * @param pdo the database object (must be the same class as current object!)
   * @return true if object accepted
   */
  public boolean setPdo (T pdo) {
    return setPdo(pdo, true);
  }


  /**
   * Gets the current object
   * @return the current object
   */
  public T getPdo()  {
    return pdo;
  }


  /**
   * Gets the last object (after save(), delete(), etc...).
   *
   * @return the previous (last) object
   */
  public T getLastPdo() {
    return lastPdo;
  }


  /**
   * Gets the object panel.
   *
   * @return the database object panel
   */
  public PdoPanel<T> getPdoPanel()  {
    return pdoPanel;
  }


  /**
   * Gets the PDO's class.
   *
   * @return the PDO class
   */
  public Class<T> getPdoClass() {
    return pdoClass;
  }


  /**
   * Shows the dialog.<br>
   *
   * The dialog can be either modal or non-modal. If modal the method
   * blocks until the dialog is closed.
   *
   * @param noPersistance true if NO changes must be made to persistance layer
   * @return the current object
   */
  public T showDialog (boolean noPersistance) {
    this.noPersistance = noPersistance;
    disableButtonsIfNoPersistance();
    pack();
    setVisible(true);
    return pdo;
  }


  /**
   * Shows the dialog.<br>
   *
   * The dialog can be either modal or non-modal. If modal the method
   * blocks until the dialog is closed.
   *
   * @return the current object
   */
  public T showDialog ()  {
    return showDialog(false);
  }


  /**
   * Returns whether the object is cleared after save.
   *
   * @return true if set new object after save
   */
  public boolean isNewAfterSave() {
    return newAfterSave;
  }

  /**
   * Sets whether the object is cleared after save.
   *
   * @param newAfterSave  true if set new object after save
   * @see #defaultNewAfterSave
   */
  public void setNewAfterSave(boolean newAfterSave) {
    this.newAfterSave = newAfterSave;
  }


  /**
   * Returns whether the dialog is disposed after delete or save.
   *
   * @return true if dispose after delete or save
   */
  public boolean isDisposeOnDeleteOrSave() {
    return disposeOnDeleteOrSave == null ? false : disposeOnDeleteOrSave;
  }

  /**
   * Sets whether the dialog will be disposed after delete or save.
   *
   * @param disposeOnDeleteOrSave  true if dispose after delete or save
   */
  public void setDisposeOnDeleteOrSave(boolean disposeOnDeleteOrSave) {
    this.disposeOnDeleteOrSave = disposeOnDeleteOrSave;
  }


  /**
   * Gets the tooltip- and error-panel.<br>
   * By default the panel is invisible.
   *
   * @return the tooltip and error panel
   */
  public TooltipAndErrorPanel getTooltipAndErrorPanel() {
    return tipAndErrorPanel;
  }


  /**
   * Adds an action listener that will be invoked as soon as
   * delete, save or cancel has been pressed.
   *
   * @param listener the listener to add
   */
  public void addActionListener (ActionListener listener)  {
    listenerList.add (ActionListener.class, listener);
  }


  /**
   * Removes an action Listener.
   *
   * @param listener the listener to remove
   */
  public void removeActionListener (ActionListener listener) {
     listenerList.remove (ActionListener.class, listener);
  }


  /**
   * Removes all Listeners.
   */
  public void removeAllListeners ()  {
    listenerList = new EventListenerList(); // this will move all to GC
  }


  /**
   * Notifies all Listeners that some button was pressed.
   * @param e the action event
   */
  public void fireActionPerformed (ActionEvent e) {
    ActionEvent evt = null;
    Object[] listeners = listenerList.getListenerList();
    if (listeners != null)  {
      for (int i = listeners.length-2; i >= 0; i -= 2)  {
        if (listeners[i] == ActionListener.class)  {
          if (evt == null)  {
            evt = new ActionEvent (this, e.getID(), e.getActionCommand());
          }
          ((ActionListener)listeners[i+1]).actionPerformed(evt);
        }
      }
    }
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

          case KeyEvent.VK_F:
            if (searchButton.isVisible() && searchButton.isEnabled()) {
              searchButton.doClick();
            }
            break;

          case KeyEvent.VK_S:
            if (saveButton.isVisible() && saveButton.isEnabled()) {
              saveButton.doClick();
            }
            break;

          case KeyEvent.VK_N:
            if (newButton.isVisible() && newButton.isEnabled()) {
              newButton.doClick();
            }
            break;

          case KeyEvent.VK_W:
            if (closeButton.isVisible() && closeButton.isEnabled()) {
              closeButton.doClick();
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
          if (closeButton.isVisible() && closeButton.isEnabled()) {
            closeButton.doClick();
          }
          e.consume();
          return true;
        }
      }
    }

    // else proceed with standard dispatching
    return false;
  }



  // ----------------------------- implements DropTargetListener ---------------

  @Override
  public void dragEnter (DropTargetDragEvent event)  {
    if (!isDragAcceptable(event)) {
      event.rejectDrag();
    }
    else  {
      /**
       * is that bug in Win32-JVM is really fixed? see:
       * http://developer.java.sun.com/developer/bugParade/bugs/4217416.html
       */
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
    else  {
      event.acceptDrag(DnDConstants.ACTION_COPY);
    }
  }

  @Override
  public void dropActionChanged (DropTargetDragEvent event)  {
  }

  @Override
  @SuppressWarnings("unchecked")
  public void drop (DropTargetDropEvent event)  {
    if (isDropAcceptable(event)) {
      event.acceptDrop(DnDConstants.ACTION_COPY);
      Transferable trans = event.getTransferable();
      try {
        Object transferData = trans.getTransferData(dndFlavor);
        if (transferData instanceof PdoTransferData) {
          PersistentDomainObject<?> droppedObject = ((PdoTransferData) transferData).getPdo(context.getSession());
          if (droppedObject != null)  {
            setPdo((T) droppedObject);
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









  /**
   * {@inheritDoc}
   * <p>
   * Adds " (modal)" to the title if dialog is modal
   */
  @Override
  public void setTitle(String title) {
    if (isModal())  {
      if (title == null) {
        title = RdcSwingRdcBundle.getString("(MODAL)");
      }
      else {
        title += RdcSwingRdcBundle.getString(" (MODAL)");
      }
    }
    super.setTitle(title);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to update the buttons.
   */
  @Override
  public void setChangeable(boolean changeable)  {
    super.setChangeable(changeable);
    setupButtons();
  }


  @Override
  public boolean areValuesChanged() {
    // pasted objects are always changed
    return pdo != null && pdo.getCopiedObject() != null || super.areValuesChanged();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to enable/disable the save-button and the {@link TooltipAndErrorPanel}.
   */
  @Override
  public void triggerValuesChanged()  {
    super.triggerValuesChanged();
    if (saveButton.isVisible()) {
      saveButton.setEnabled(noPersistance || (editable && areValuesChanged()));
      if (!saveButton.isEnabled())  {
        /**
         * remove pending errors from tooltippanel.
         * This code gets called if saveButton is disabled due to a change
         * in a field that previously caused an error an now is set back
         * to the old (non-error) value. In this case prepareSave() never
         * gets called (actionPerformed of saveButton is not invoked), so
         * the errors would remain displayed, which confuses the user.
         */
        TooltipAndErrorPanel ttPanel = getTooltipAndErrorPanel();
        if (ttPanel != null) {
          ttPanel.clearErrors();
        }
      }
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
    saveButton.setEnabled(noPersistance || pdo.getCopiedObject() != null);
    // always enabled if noPersistance to enable prepareSave-check
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to remove all Listeners.
   */
  @Override
  public void dispose() {
    super.dispose();
    removeAllListeners();
    linkedPdoList = null;
  }




  /**
   * {@inheritDoc}
   * <p>
   * Overridden to catch WINDOW_CLOSING event.
   */
  @Override
  protected void processWindowEvent(WindowEvent e) {
    if (e.getID() == WindowEvent.WINDOW_CLOSING)  {
      // force focus lost to get valueEntered fired on current component
      closeButton.requestFocusInWindow();
      closeButton.doClick();
    }
    else {
      if (e.getID() == WindowEvent.WINDOW_ACTIVATED) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
      }
      if (e.getID() == WindowEvent.WINDOW_DEACTIVATED) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
      }
      super.processWindowEvent(e);
    }
  }



  /**
   * Determines the permissions viewable, writable, deletable and loads the SecurityManager.<br>
   * They will be verified again in the TX.
   */
  protected void determinePermissions() {
    if (pdo == null) {
      // no object, no permissions.
      viewable = false;
      editable = false;
      deletable = false;
    }
    else {
      SecurityManager manager = SecurityFactory.getInstance().getSecurityManager();
      SecurityResult sr = manager.evaluate(context, SecurityFactory.getInstance().getPermission(ViewPermission.class), pdo.getClassId(), pdo.getId());
      viewable = sr.isAccepted();
      if (!viewable) {
        noAccessLabel.setText(StringHelper.toHTML(sr.explain(RdcSwingRdcBundle.getString("YOU DON'T HAVE ENOUGH PERMISSIONS TO VIEW THE DATA"))));
      }
      sr = manager.evaluate(context, SecurityFactory.getInstance().getPermission(EditPermission.class), pdo.getClassId(), pdo.getId());
      editable = viewable && isChangeable() && sr.isAccepted();
      deletable = editable && pdo.isRemovable();
    }
  }


  /**
   * Applies a token lock to the object.
   * This method is invoked from setPdo() and is only
 provided to be overridden (see poolkeeper.replication).
   *
   * @param pdo the object to apply the lock on
   * @return null if applied, else an errormessage.
   */
  protected String applyLock(T pdo) {
    try {
      pdo.requestTokenLock();
      return null;
    }
    catch (RuntimeException e) {
      // createPdo the error message
      Date editedSince = pdo.getEditedSince();
      if (editedSince == null) {
        editedSince = new Date();
      }
      return MessageFormat.format(RdcSwingRdcBundle.getString("OBJECT {0} IS BEING EDITED SINCE {1} BY {2}"),
                                  pdo.getSingular(), FormatHelper.formatShortTimestamp(editedSince), pdo.getTokenLockObject());
    }
  }


  /**
   * Revokes a token lock to the object that has not been saved.<br>
   * This method is is only provided to be overridden (see poolkeeper.replication).
   */
  protected void revokeLock() {
    pdo.releaseTokenLock();
  }


  /**
   * Saves the object.
   * Default implementation simply updates the write permission
   * and invokes object.save().
   */
  protected void saveObject() {
    determinePermissions();
    if (editable) {
      pdo = isDisposeOnDeleteOrSave() ? pdo.persist() : pdo.persistTokenLocked();
    }
    else  {
      throw new PdoRuntimeException(pdo, RdcSwingRdcBundle.getString("NO WRITE PERMISSION"));
    }
  }


  /**
   * Saves the current object.
   * <p>
   *
   * Will announce, start a transaction, check for prepare, apply the editedby token,
   * save and remove the token, commit -- or rollback.
   * If the update fails, the user will be told so in a message window.
   * The update may fail due to the following reasons:
   * <ol>
   * <li>unique violation</li>
   * <li>serial-no has changed</li>
   * <li>write permission revoked</li>
   * <li>the lock timed out and another user grabbed the object</li>
   * <li>object modified in the meantime (if no lock)</li>
   * </ol>
   *
   * @return true if saved
   */
  public boolean save()  {

    if (pdoPanel.announceSave()) {

      // we are starting the transaction here to allow prepareSave() for saving and
      // updating related objects
      begin(TX_SAVE);
      long editedBy = 0;

      try {
        if (pdoPanel.prepareSave() && !noPersistance)  {
          saveObject();
          lastPdo = pdo;          // save bec. could be cleared by clearPdo below
          commit();
          return true;
        }
        else  {
          rollback();
        }
      }
      catch (RuntimeException ex) {

        /**
         * Update or insert failed.
         *
         * a) unique violation
         * b) serial-no has changed
         * c) write permission revoked
         * d) the lock timed out and another user grabbed the object
         * e) object modified in the meantime (if no lock)
         * f) validation failed
         */

        try {
          rollback();   // rollback before user-message
        }
        catch (PersistenceException dbx) {
          // may be already rolled back, but log that for sure
          LOGGER.warning("rollback failed", dbx);
        }

        if (showErrorOnSave(determineRealCause(ex), editedBy)) {
          // reload
          Object transData = pdo.getTransientData();
          @SuppressWarnings("unchecked")
          T obj = pdo.reload();
          if (obj != null) {
            obj.setTransientData(transData);
            setPdo(obj);
          }
        }
      }
    }

    return false;
  }



  /**
   * Determines the real cause of an exception.
   *
   * @param ex the thrown exception
   * @return the real exception
   */
  protected Throwable determineRealCause(Throwable ex) {
    RemoteException remEx = ExceptionHelper.extractException(RemoteException.class, true, ex);
    if (remEx != null && remEx.getCause() instanceof Exception) {
      ex = remEx.getCause();
    }
    @SuppressWarnings("unchecked")
    Throwable t = ExceptionHelper.extractException(true, ex, ValidationFailedException.class, PersistenceException.class);
    if (t != null) {
      ex = t;
    }
    return ex;
  }


  /**
   * Shows the validation results.
   *
   * @param validationResults the validation results
   */
  protected void showValidationResults(List<ValidationResult> validationResults) {
    getTooltipAndErrorPanel().setErrors(pdoPanel.createInteractiveErrors(validationResults));
  }

  /**
   * Shows an error message to the user if saving the PDO has failed.
   *
   * @param ex the exception
   * @param editedBy the editedBy value before save
   * @return true if reloading the PDO is necessary
   */
  protected boolean showErrorOnSave(Throwable ex, long editedBy) {

    if (!editable) {
      FormError.show(MessageFormat.format(RdcSwingRdcBundle.getString("{0} LOST ITS WRITE PERMISSION"), pdo.getSingular()));
    }
    else if (ex instanceof ValidationFailedException) {
      showValidationResults(((ValidationFailedException) ex).getResults());
    }
    else if (ex instanceof ConstraintException)  {
      LOGGER.logStacktrace(Level.WARNING, ex);
      FormError.show(MessageFormat.format(RdcSwingRdcBundle.getString("{0} ALREADY EXISTS"), pdo.getSingular()));
    }
    else  {
      if (ex instanceof NotFoundException) {
        LOGGER.logStacktrace(Level.WARNING, ex);
        FormError.show(MessageFormat.format(RdcSwingRdcBundle.getString("{0} MODIFIED BY_ANOTHER USER MEANWHILE"), pdo.getSingular()));
      }
      else  {
        if (pdo.getEditedBy() != editedBy) {
          if (pdo.getEditedBy() != 0) {
            FormError.show(MessageFormat.format(RdcSwingRdcBundle.getString("{0} LOCKED BY {1} MEANWHILE. YOUR LOCK TIMED OUT."),
                                                pdo.getSingular(), pdo.getTokenLockObject()));
          }
          else  {
            // some other error: usually db error
            FormError.showException(MessageFormat.format(RdcSwingRdcBundle.getString("{0} COULD NOT BE SAVED"), pdo.getSingular()),
                    ex, false, LOGGER);
          }
        }
        else  {
          if (editedBy != 0) {
            // our token timed out and after that another user edited the object
            FormError.show(MessageFormat.format(
                    RdcSwingRdcBundle.getString("{0} MODIFIED BY ANOTHER USER MEANWHILE. YOUR LOCK TIMED OUT."), pdo.getSingular()));
          }
          else  {
            // some technical error
            FormError.showException(MessageFormat.format(RdcSwingRdcBundle.getString("UNSPECIFIED ERROR WHILE SAVING {0}"),
                                                         pdo.getSingular()), ex, false, LOGGER);
          }
        }
        return true;    // reload
      }
    }

    return false;   // no reload
  }



  /**
   * Deletes the current object.
   * The default implementation updates the delete permission and then deletes.
   */
  protected void deleteObject() {
    determinePermissions();
    if (deletable) {
      pdo.delete();
    }
    else  {
      throw new PdoRuntimeException(RdcSwingRdcBundle.getString("NO DELETE PERMISSION"));
    }
  }


  /**
   * Deletes the current database object.
   * <p>
   * Will announce, start a transaction, prepares thre delete and deletes the object.
   * @return true if object deleted
   */
  protected boolean delete()  {
    if (pdoPanel.announceDelete()) {
      begin(TX_DELETE);
      try {
        if (!pdoPanel.prepareDelete()) {
          rollback();
        }
        else  {
          deleteObject();
          // DONE!
          commit();
          return true;
        }
      }
      catch (RuntimeException e) {
        rollback();
      }
    }
    return false;
  }



  /**
   * Begin a transaction.
   *
   * @param txName the transaction name
   */
  protected void begin(String txName) {
    txVoucher = pdo.getSession().begin(txName);
  }

  /**
   * Commit the current transaction
   */
  protected void commit() {
    pdo.getSession().commit(txVoucher);
    txVoucher = 0;
  }

  /**
   * Rollback the current transaction
   */
  protected void rollback() {
    pdo.getSession().rollback(txVoucher);
    txVoucher = 0;
  }




  private void updateObjectListIndex() {
    if (linkedPdoList != null && pdo != null) {
      linkedPdoIndex = linkedPdoList.indexOf(pdo);
    }
    else  {
      linkedPdoIndex = -1;
    }
  }



  /**
   * disable buttons if noPersistance is enabled
   */
  private void disableButtonsIfNoPersistance() {
    if (noPersistance)  {
      previousButton.setVisible(false);
      nextButton.setVisible(false);
      browserButton.setVisible(false);
      deleteButton.setVisible(false);
      idButton.setVisible(false);
      newButton.setVisible(false);
      printButton.setVisible(false);
      searchButton.setVisible(false);
      securityButton.setVisible(false);
    }
  }


  /**
   * sets visibility of buttons
   */
  public void setupButtons()  {

    if (pdo != null) {

      if (linkedPdoList != null && linkedPdoList.size() > 1) {
        previousButton.setVisible(previousButtonAvailable);
        nextButton.setVisible(nextButtonAvailable);
        previousButton.setEnabled(linkedPdoIndex > 0);
        nextButton.setEnabled(linkedPdoIndex < linkedPdoList.size() - 1);
      }
      else  {
        previousButton.setVisible(false);
        nextButton.setVisible(false);
      }

      deleteButton.setEnabled(deletable && !pdo.isNew());
      browserButton.setEnabled(viewable && !pdo.isNew());
      saveButton.setEnabled(editable);
      printButton.setEnabled(viewable);
      printButton.setVisible(printButtonAvailable);

      boolean visible = false;
      try {
        visible = SecurityDialogFactory.getInstance().isDialogAllowed(context);
      }
      catch (Exception ex) {}   // missing is ok, i.e. no security

      securityButton.setVisible(visible);

      boolean changeable = isChangeable();
      if (isModal()) {
        newButton.setVisible(changeable && newButtonAvailable);
        deleteButton.setVisible(changeable && deleteButtonAvailable);
        searchButton.setVisible(changeable && searchButtonAvailable);
        idButton.setVisible(changeable && idButtonAvailable);
      }
      else  {
        newButton.setVisible(newButtonAvailable);
        deleteButton.setVisible(deleteButtonAvailable);
        searchButton.setVisible(searchButtonAvailable);
        idButton.setVisible(idButtonAvailable);

        newButton.setEnabled(changeable);
        searchButton.setEnabled(true);
      }
      saveButton.setVisible(changeable && saveButtonAvailable);
      browserButton.setVisible(browserButtonAvailable);

      disableButtonsIfNoPersistance();
    }

    else  {
      // no buttons except cancel
      previousButton.setVisible(false);
      nextButton.setVisible(false);
      securityButton.setVisible(false);
      deleteButton.setVisible(false);
      browserButton.setVisible(false);
      saveButton.setVisible(false);
      printButton.setVisible(false);
      searchButton.setVisible(false);
      idButton.setVisible(false);
      newButton.setVisible(false);
    }

    closeButton.setEnabled(true);  // always visible and enabled
  }


  /**
   * asks user whether cancel is okay or not if data has been modified.
   *
   * @return SAVE, DISCARD or CANCEL
   */
  private int discardOk() {
    if (!editable || !areValuesChanged()) {
      // discard always ok if not modified
      return CancelSaveDiscardDialog.DISCARD;
    }
    // ask user
    return CancelSaveDiscardDialog.getAnswer();
  }


  /**
   * Closes the dialog.
   *
   * @param evt the closing event
   */
  protected void doClose (ActionEvent evt) {
    if (pdoPanel != null)  {
      int answer = discardOk();
      if (answer == CancelSaveDiscardDialog.SAVE) {
        doSaveAndDispose(evt);
      }
      else if (answer == CancelSaveDiscardDialog.DISCARD && pdoPanel.prepareCancel())  {
        clearEditedBy();  // Bearbeitungsinfo löschen, falls Option eingeschaltet
        lastPdo = pdo;
        pdo   = null;
        fireActionPerformed (evt);
        dispose();
      }
      // else: answer was CANCEL, i.e. stay in form!
    }
    else  {
      dispose();
    }
  }


  /**
   * Runs the save operation.
   *
   * @param evt the closing event
   * @return true if saved
   */
  protected boolean doSave(ActionEvent evt)  {
    if (save()) {
      fireActionPerformed (evt);
      if (isDisposeOnDeleteOrSave())  {
        dispose();
      }
      else  {
        if (newAfterSave) {
          clearPdo();
        }
        else  {
          setPdo(getPdo());
        }
      }
      return true;
    }
    return false;
  }


  /**
   * Runs the save operation and disposes the dialog.
   *
   * @param evt the closing event
   * @return true if saved, else still shown
   */
  protected boolean doSaveAndDispose(ActionEvent evt)  {
    if (doSave(evt)) {
      dispose();
      return true;
    }
    return false;
  }


  /**
   * Runs the print operation.
   *
   * @param evt the action event
   */
  protected void doPrint(ActionEvent evt) {
    // the default does nothing.
    // by default, the print button is disabled anyway.
    LOGGER.warning("printing not implementated");
  }


  /**
   * Runs the delete operation.<br>
   *
   * @param evt the action event that triggered this delete operation
   * @return true if delete operation was performed; otherwise false
   */
  @SuppressWarnings("nls")
  protected boolean doDelete(ActionEvent evt) {
    boolean result = false;
    if (FormQuestion.yesNo(RdcSwingRdcBundle.getString("ARE YOU SURE TO DELETE THIS OBJECT?"))) {
      if (delete()) {
        fireActionPerformed (evt);
        if (isDisposeOnDeleteOrSave())  {
          dispose();
        }
        else  {
          clearPdo();
        }
        result = true;
      }
      else  {
        FormError.show(RdcSwingRdcBundle.getString("DELETING OBJECT FAILED!"));
      }
    }
    return result;
  }


  /**
   * Runs the new object operation.
   *
   * @param evt the action event
   */
  protected void doNew(ActionEvent evt) {
    int answer = discardOk();
    if (answer == CancelSaveDiscardDialog.SAVE) {
      saveButton.doClick();
    }
    else if (answer == CancelSaveDiscardDialog.DISCARD && pdoPanel.prepareNew())  {
      clearEditedBy();
      clearPdo();
      fireActionPerformed (evt);
    }
  }


  /**
   * Runs the new search operation.
   *
   * @param evt the action event
   */
  protected void doSearch(ActionEvent evt) {
    /**
     * search.
     * Notice: we don't check for discardOk because data will always be modified when
     * user enters search criteria *before* pressing the search button (see preset... in PersistentDomainObject).
     * If you don't like that, override prepareSearch()!)
     */
    if (pdoPanel.prepareSearch())  {
      try {
        PdoSearchDialog<T> sd = Rdc.createPdoSearchDialog(this, context, pdoClass,
                                                          (o) -> pdoClass.isAssignableFrom(o.getClass()),
                                                          false, true);
        sd.setMultiSelection(false);
        T oldObject = pdo;
        if (pdo != null) {
          if (!pdo.isNew())  {
            // mask with new Object keeping old settings
            setKeepChangedValues(true);
            setPdo(Pdo.create(pdo), false);
            setKeepChangedValues(false);
            getFormValues();    // get kept values back to new object
          }
          // preset the searchdialog with parameters entered so far
          sd.getPdoSearch().presetSearchCriteria(pdo);
        }

        // open the searchdialog
        @SuppressWarnings("unchecked")
        T newPdo = (T) sd.showDialog();   // is of type T!

        if (newPdo != null)  {
          // show new Object, discarding old one
          if (!setPdo(newPdo))  {
            clearPdo();    // object refused
          }
          fireActionPerformed (evt);
        }
        else  {
          // no selection, keep object
          setPdo(oldObject);
        }
      }
      catch (Exception ex)  {
        FormError.showException(RdcSwingRdcBundle.getString("COULD NOT LAUNCH SEARCH DIALOG"), ex);
      }
    }
  }



  /**
   * clears the editedby token.
   */
  protected void clearEditedBy()  {
    if (pdo != null &&
        !pdo.isNew() &&
        pdo.isTokenLockedByMe())  {
      revokeLock();
    }
  }




  /**
   * check for Popup
   */

  private void checkPopup(InputEvent e) {
    if (e instanceof MouseEvent && ((MouseEvent)e).isPopupTrigger())  {
      Point p = ((MouseEvent)e).getPoint();
      copyItem.setEnabled(pdo != null && !pdo.isNew());
      pasteAsCopyItem.setEnabled(newButton.isEnabled() && newButton.isVisible());
      clipboardMenu.show(buttonPanel, p.x, p.y);
    }
  }


  private boolean isDragAcceptable(DropTargetDragEvent event) {
    return (event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE) != 0 &&
            event.isDataFlavorSupported(dndFlavor);
  }

  private boolean isDropAcceptable(DropTargetDropEvent event) {
    return (event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE) != 0 &&
            event.isDataFlavorSupported(dndFlavor);
  }




  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    idDialog = new FormDialog(this);
    jLabel1 = new org.tentackle.swing.FormLabel();
    idNumberField = new org.tentackle.swing.LongFormField();
    idSearchButton = new org.tentackle.swing.FormButton();
    idCancelButton = new org.tentackle.swing.FormButton();
    clipboardMenu = new javax.swing.JPopupMenu();
    copyItem = new javax.swing.JMenuItem();
    pasteItem = new javax.swing.JMenuItem();
    pasteAsCopyItem = new javax.swing.JMenuItem();
    dataPanel = new org.tentackle.swing.FormPanel();
    noAccessPanel = new javax.swing.JPanel();
    noAccessLabel = new org.tentackle.swing.FormLabel();
    noInfoPanel = new javax.swing.JPanel();
    jLabel3 = new org.tentackle.swing.FormLabel();
    buttonPanel = new org.tentackle.swing.FormPanel();
    searchButton = new org.tentackle.swing.FormButton();
    newButton = new org.tentackle.swing.FormButton();
    saveButton = new org.tentackle.swing.FormButton();
    deleteButton = new org.tentackle.swing.FormButton();
    printButton = new org.tentackle.swing.FormButton();
    closeButton = new org.tentackle.swing.FormButton();
    idButton = new org.tentackle.swing.FormButton();
    jSeparator1 = new javax.swing.JSeparator();
    securityButton = new org.tentackle.swing.FormButton();
    browserButton = new org.tentackle.swing.FormButton();
    tipAndErrorPanel = new org.tentackle.swing.rdc.TooltipAndErrorPanel();
    previousButton = new org.tentackle.swing.FormButton();
    nextButton = new org.tentackle.swing.FormButton();

    idDialog.setAutoPosition(true);
    idDialog.setTitle(RdcSwingRdcBundle.getString("READ OBJECT BY ID")); // NOI18N
    idDialog.setModal(true);
    idDialog.getContentPane().setLayout(new java.awt.GridBagLayout());

    jLabel1.setText(RdcSwingRdcBundle.getString("ID-NO:")); // NOI18N
    idDialog.getContentPane().add(jLabel1, new java.awt.GridBagConstraints());

    idNumberField.setAutoSelect(true);
    idNumberField.setColumns(10);
    idNumberField.setUnsigned(true);
    idNumberField.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        idNumberFieldActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
    idDialog.getContentPane().add(idNumberField, gridBagConstraints);

    idSearchButton.setText(RdcSwingRdcBundle.getString("READ")); // NOI18N
    idSearchButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        idSearchButtonActionPerformed(evt);
      }
    });
    idDialog.getContentPane().add(idSearchButton, new java.awt.GridBagConstraints());

    idCancelButton.setText(RdcSwingRdcBundle.getString("CANCEL")); // NOI18N
    idCancelButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        idCancelButtonActionPerformed(evt);
      }
    });
    idDialog.getContentPane().add(idCancelButton, new java.awt.GridBagConstraints());

    copyItem.setText(RdcSwingRdcBundle.getString("COPY")); // NOI18N
    copyItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        copyItemActionPerformed(evt);
      }
    });
    clipboardMenu.add(copyItem);

    pasteItem.setText(RdcSwingRdcBundle.getString("PASTE")); // NOI18N
    pasteItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        pasteItemActionPerformed(evt);
      }
    });
    clipboardMenu.add(pasteItem);

    pasteAsCopyItem.setText(RdcSwingRdcBundle.getString("PASTE AS NEW")); // NOI18N
    pasteAsCopyItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        pasteAsCopyItemActionPerformed(evt);
      }
    });
    clipboardMenu.add(pasteAsCopyItem);

    setAutoPosition(true);

    dataPanel.setLayout(new java.awt.CardLayout());

    noAccessPanel.setLayout(new java.awt.BorderLayout());

    noAccessLabel.setForeground(java.awt.Color.red);
    noAccessLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    noAccessLabel.setText(RdcSwingRdcBundle.getString("YOU DON'T HAVE PERMISSION TO VIEW THIS KIND OF DATA!")); // NOI18N
    noAccessPanel.add(noAccessLabel, java.awt.BorderLayout.CENTER);

    dataPanel.add(noAccessPanel, "noAccess");

    noInfoPanel.setLayout(new java.awt.BorderLayout());

    jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    jLabel3.setText(RdcSwingRdcBundle.getString("NO FURTHER INFORMATION")); // NOI18N
    noInfoPanel.add(jLabel3, java.awt.BorderLayout.CENTER);

    dataPanel.add(noInfoPanel, "noInfo");

    getContentPane().add(dataPanel, java.awt.BorderLayout.CENTER);

    buttonPanel.setLayout(new java.awt.GridBagLayout());

    searchButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("search"));
    searchButton.setMnemonic('f');
    searchButton.setText(RdcSwingRdcBundle.getString("FIND")); // NOI18N
    searchButton.setActionCommand(ACTION_SEARCH);
    searchButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    searchButton.setName("find"); // NOI18N
    searchButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        searchButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 6;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
    buttonPanel.add(searchButton, gridBagConstraints);

    newButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("new"));
    newButton.setMnemonic('n');
    newButton.setText(RdcSwingRdcBundle.getString("NEW")); // NOI18N
    newButton.setActionCommand(ACTION_NEW);
    newButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    newButton.setName("new"); // NOI18N
    newButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        newButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 7;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
    buttonPanel.add(newButton, gridBagConstraints);

    saveButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("save"));
    saveButton.setMnemonic('s');
    saveButton.setText(RdcSwingRdcBundle.getString("SAVE")); // NOI18N
    saveButton.setActionCommand(ACTION_SAVE);
    saveButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    saveButton.setName("save"); // NOI18N
    saveButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        saveButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 8;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
    buttonPanel.add(saveButton, gridBagConstraints);

    deleteButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("delete"));
    deleteButton.setMnemonic('d');
    deleteButton.setText(RdcSwingRdcBundle.getString("DELETE")); // NOI18N
    deleteButton.setActionCommand(ACTION_DELETE);
    deleteButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    deleteButton.setName("delete"); // NOI18N
    deleteButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 9;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
    buttonPanel.add(deleteButton, gridBagConstraints);

    printButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("print"));
    printButton.setMnemonic('p');
    printButton.setText(RdcSwingRdcBundle.getString("PRINT")); // NOI18N
    printButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    printButton.setName("print"); // NOI18N
    printButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        printButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 10;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
    buttonPanel.add(printButton, gridBagConstraints);

    closeButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("close"));
    closeButton.setMnemonic('c');
    closeButton.setText(RdcSwingRdcBundle.getString("CANCEL")); // NOI18N
    closeButton.setActionCommand(ACTION_CANCEL);
    closeButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    closeButton.setName("cancel"); // NOI18N
    closeButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        closeButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 11;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 2);
    buttonPanel.add(closeButton, gridBagConstraints);

    idButton.setText("0");
    idButton.setFont(new java.awt.Font("DialogInput", 1, 12)); // NOI18N
    idButton.setMargin(new java.awt.Insets(1, 0, 1, 0));
    idButton.setName("objectId"); // NOI18N
    idButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        idButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 0);
    buttonPanel.add(idButton, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 50, 0, 50);
    buttonPanel.add(jSeparator1, gridBagConstraints);

    securityButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("security"));
    securityButton.setMargin(new java.awt.Insets(1, 0, 1, 0));
    securityButton.setName("security"); // NOI18N
    securityButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        securityButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
    buttonPanel.add(securityButton, gridBagConstraints);

    browserButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("browser"));
    browserButton.setMnemonic('t');
    browserButton.setText(RdcSwingRdcBundle.getString("TREE")); // NOI18N
    browserButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    browserButton.setName("tree"); // NOI18N
    browserButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        browserButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
    buttonPanel.add(browserButton, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 2);
    buttonPanel.add(tipAndErrorPanel, gridBagConstraints);

    previousButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("up"));
    previousButton.setActionCommand(ACTION_PREVIOUS);
    previousButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    previousButton.setName("previous"); // NOI18N
    previousButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        previousButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
    buttonPanel.add(previousButton, gridBagConstraints);

    nextButton.setIcon(org.tentackle.swing.plaf.PlafUtilities.getInstance().getIcon("down"));
    nextButton.setActionCommand(ACTION_NEXT);
    nextButton.setMargin(new java.awt.Insets(1, 3, 1, 3));
    nextButton.setName("next"); // NOI18N
    nextButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        nextButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
    buttonPanel.add(nextButton, gridBagConstraints);

    getContentPane().add(buttonPanel, java.awt.BorderLayout.SOUTH);
  }// </editor-fold>//GEN-END:initComponents

  @SuppressWarnings("unchecked")
  private void pasteAsCopyItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteAsCopyItemActionPerformed
    try {
      Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
      Transferable trans = clip.getContents(this);
      Object transferData = trans.getTransferData(dndFlavor);
      if (transferData instanceof PdoTransferData) {
        PersistentDomainObject<?> droppedObject = ((PdoTransferData) transferData).getPdo(context.getSession());
        if (droppedObject != null)  {
          // even if same context, need a full copy with propably more complicated procedure
          setPdo((T) droppedObject.createCopyInContext(pdo.getDomainContext()));
        }
      }
    }
    catch (Exception e) {
      FormError.showException (RdcSwingRdcBundle.getString("COULDN'T INSERT NEW COPY"), e);
    }
  }//GEN-LAST:event_pasteAsCopyItemActionPerformed

  private void copyItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyItemActionPerformed
    if (pdo != null) {
      try {
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        PdoTransferable<T> trans = new PdoTransferable<>(pdo);
        clip.setContents(trans, trans);
      }
      catch (Exception e) {
        LOGGER.logStacktrace(Level.WARNING, e);
        FormInfo.show(RdcSwingRdcBundle.getString("COULDN'T COPY"));
      }
    }
  }//GEN-LAST:event_copyItemActionPerformed

  @SuppressWarnings("unchecked")
  private void pasteItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteItemActionPerformed
    try {
      Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
      Transferable trans = clip.getContents(this);
      Object transferData = trans.getTransferData(dndFlavor);
      if (transferData instanceof PdoTransferData) {
        PersistentDomainObject<?> droppedObject = ((PdoTransferData) transferData).getPdo(context.getSession());
        if (droppedObject != null)  {
          setPdo((T) droppedObject);
        }
      }
    }
    catch (Exception e) {
      LOGGER.logStacktrace(Level.WARNING, e);
      FormInfo.show(RdcSwingRdcBundle.getString("COULDN'T INSERT"));
    }
  }//GEN-LAST:event_pasteItemActionPerformed

  private void browserButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browserButtonActionPerformed
    PdoNavigationDialog<T> d = Rdc.createPdoNavigationDialog(this, pdo, null);
    d.setTitle(MessageFormat.format(RdcSwingRdcBundle.getString("BROWSER FOR {0} IN {1}"), pdo, pdo.getDomainContext()));
    d.getNaviPanel().getNaviTree().expandTree(2);   // max. 2 Levels
    d.pack();
    d.setVisible(true);
  }//GEN-LAST:event_browserButtonActionPerformed

  private void securityButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_securityButtonActionPerformed
    SecurityDialogFactory.getInstance().createDialog(baseContext, pdo.getEffectiveClass(), pdo.getId()).showDialog();
  }//GEN-LAST:event_securityButtonActionPerformed

  private void idCancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_idCancelButtonActionPerformed
    idDialog.dispose();
  }//GEN-LAST:event_idCancelButtonActionPerformed

  @SuppressWarnings("unchecked")
  private void idSearchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_idSearchButtonActionPerformed
    long newId = idNumberField.getLongValue();
    if (newId > 0)  {
      T reloadedPdo = pdo.select(newId);
      if (reloadedPdo != null)  {
        setPdo(reloadedPdo);
      }
      else  {
        FormError.show(RdcSwingRdcBundle.getString("NO OBJECT WITH SUCH AN ID"));
      }
    }
    idDialog.dispose();
  }//GEN-LAST:event_idSearchButtonActionPerformed

  private void idNumberFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_idNumberFieldActionPerformed
    idSearchButton.doClick();
  }//GEN-LAST:event_idNumberFieldActionPerformed

  private void idButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_idButtonActionPerformed
    // Suchen nach ID-Nummern
    if (pdo != null && !isModal()) {
      idDialog.pack();
      idNumberField.setLongValue(pdo.getId());
      idNumberField.requestFocusLater();
      idDialog.setVisible(true);    // modal!
    }
  }//GEN-LAST:event_idButtonActionPerformed

  private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
    doClose(evt);
  }//GEN-LAST:event_closeButtonActionPerformed

  private void printButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_printButtonActionPerformed
    doPrint(evt);
  }//GEN-LAST:event_printButtonActionPerformed

  private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
    doDelete(evt);
  }//GEN-LAST:event_deleteButtonActionPerformed

  private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
    doSave(evt);
  }//GEN-LAST:event_saveButtonActionPerformed

  private void newButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newButtonActionPerformed
    doNew(evt);
  }//GEN-LAST:event_newButtonActionPerformed

  private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
    doSearch(evt);
  }//GEN-LAST:event_searchButtonActionPerformed


  @SuppressWarnings("unchecked")
  private void prevNextHandler(java.awt.event.ActionEvent evt, int offset) {
    int answer = discardOk();
    if (answer == CancelSaveDiscardDialog.SAVE) {
      saveButton.doClick();
    }
    else if (answer == CancelSaveDiscardDialog.DISCARD && pdoPanel.prepareCancel())  {
      int oldIndex = linkedPdoIndex;
      clearEditedBy();
      clearEditedBy();
      clearPdo();
      if (linkedPdoList != null) {
        linkedPdoIndex = oldIndex;
        for (int ndx = linkedPdoIndex + offset; ndx >= 0 && ndx < linkedPdoList.size(); ndx += offset) {
          T obj = linkedPdoList.get(ndx);
          if (obj != null && setPdo(obj)) {
            break;  // accepted
          }
        }
      }
      fireActionPerformed (evt);
    }
  }


  private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
    prevNextHandler(evt, 1);
}//GEN-LAST:event_nextButtonActionPerformed

  private void previousButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_previousButtonActionPerformed
    prevNextHandler(evt, -1);
}//GEN-LAST:event_previousButtonActionPerformed




  // Variables declaration - do not modify//GEN-BEGIN:variables
  private org.tentackle.swing.FormButton browserButton;
  private org.tentackle.swing.FormPanel buttonPanel;
  private javax.swing.JPopupMenu clipboardMenu;
  private org.tentackle.swing.FormButton closeButton;
  private javax.swing.JMenuItem copyItem;
  private org.tentackle.swing.FormPanel dataPanel;
  private org.tentackle.swing.FormButton deleteButton;
  private org.tentackle.swing.FormButton idButton;
  private org.tentackle.swing.FormButton idCancelButton;
  private org.tentackle.swing.FormDialog idDialog;
  private org.tentackle.swing.LongFormField idNumberField;
  private org.tentackle.swing.FormButton idSearchButton;
  private org.tentackle.swing.FormLabel jLabel1;
  private org.tentackle.swing.FormLabel jLabel3;
  private javax.swing.JSeparator jSeparator1;
  private org.tentackle.swing.FormButton newButton;
  private org.tentackle.swing.FormButton nextButton;
  private org.tentackle.swing.FormLabel noAccessLabel;
  private javax.swing.JPanel noAccessPanel;
  private javax.swing.JPanel noInfoPanel;
  private javax.swing.JMenuItem pasteAsCopyItem;
  private javax.swing.JMenuItem pasteItem;
  private org.tentackle.swing.FormButton previousButton;
  private org.tentackle.swing.FormButton printButton;
  private org.tentackle.swing.FormButton saveButton;
  private org.tentackle.swing.FormButton searchButton;
  private org.tentackle.swing.FormButton securityButton;
  private org.tentackle.swing.rdc.TooltipAndErrorPanel tipAndErrorPanel;
  // End of variables declaration//GEN-END:variables

}
