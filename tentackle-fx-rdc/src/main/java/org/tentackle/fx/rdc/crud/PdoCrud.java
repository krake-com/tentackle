/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx.rdc.crud;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;

import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.container.FxBorderPane;
import org.tentackle.fx.container.FxHBox;
import org.tentackle.fx.rdc.EventListenerProxy;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.PdoController;
import org.tentackle.fx.rdc.PdoEditor;
import org.tentackle.fx.rdc.PdoEvent;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.rdc.security.SecurityDialogFactory;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.LockException;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.NotFoundException;
import org.tentackle.validate.ValidationFailedException;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * CRUD controller for PDOs.
 *
 * @author harald
 * @param <T> the PDO type
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class PdoCrud<T extends PersistentDomainObject<T>> extends AbstractFxController implements PdoController<T> {

  private static final Logger LOGGER = Logger.get(PdoCrud.class);

  @FXML
  private FxLabel noViewLabel;

  @FXML
  private FxBorderPane borderPane;

  @FXML
  private FxHBox buttonBox;

  @FXML
  private FxButton securityButton;

  @FXML
  private FxButton previousButton;

  @FXML
  private FxButton nextButton;

  @FXML
  private FxButton treeButton;

  @FXML
  private FxButton findButton;

  @FXML
  private FxButton newButton;

  @FXML
  private FxButton saveButton;

  @FXML
  private FxButton deleteButton;

  @FXML
  private FxButton printButton;

  @FXML
  private FxButton cancelButton;

  @FXML
  private ResourceBundle resources;                 // the resources

  private PdoEditor<T> editor;                      // the editor
  private ObservableList<T> pdoList;                // optional list of PDOs
  private int pdoListIndex;                         // index in list
  private boolean editable;                         // editability requested by the application
  private boolean modal;                            // true if dialog is modal
  private EventListenerProxy<PdoEvent> eventProxy;  // event proxy to maintain PdoEvents registered on the View

  private final SimpleBooleanProperty editing  = new SimpleBooleanProperty();   // is editing allowed effectively?
  private final SimpleBooleanProperty pdoIsNew = new SimpleBooleanProperty();   // pdo is new?


  /**
   * Sets whether the user can change the editor's contents.
   *
   * @param editable true if editable
   */
  public void setEditable(boolean editable) {
    this.editable = editable;
    updateButtons();
  }

  /**
   * Gets whether the user can change the editor's contents.
   *
   * @return true if editable
   */
  public boolean isEditable() {
    return editable;
  }

  /**
   * Returns whether the PDO is being edited effectively.
   *
   * @return true if isEditable() and editing is allowed according to the security rules
   */
  public boolean isEditing() {
    return editing.get();
  }

  /**
   * Returns whether CRUD should be treated as modal.
   *
   * @return true if modal
   */
  public boolean isModal() {
    return modal;
  }

  /**
   * Sets this CRUD should be treated as modal.<br>
   * Modality cannot be retrieved from the stage because there may be no stage yet.
   *
   * @param modal true if modal
   */
  public void setModal(boolean modal) {
    this.modal = modal;
    updateButtons();
  }


  /**
   * Sets a list of PDOs to walk through via the up and next buttons.
   *
   * @param pdoList the pdos, null to disable list navigation
   */
  public void setPdoList(ObservableList<T> pdoList) {
    this.pdoList = pdoList;
    updateButtons();
  }

  /**
   * Gets the list of pdos to walk through.
   *
   * @return the pdos
   */
  public List<T> getPdoList() {
    return pdoList;
  }



  /**
   * Sets the pdo editor.
   *
   * @param editor the editor
   */
  public void setEditor(PdoEditor<T> editor) {
    this.editor = editor;
    borderPane.setCenter(editor.getView());
    BorderPane.setMargin(editor.getView(), new Insets(5));
  }

  /**
   * Gets the pdo editor.
   *
   * @return editor
   */
  public PdoEditor<T> getEditor() {
    return editor;
  }

  /**
   * Sets the pdo to edit.
   *
   * @param pdo the pdo
   */
  @Override
  public void setPdo(T pdo) {
    Objects.requireNonNull(pdo, "pdo");

    getBinder().putBindingProperty(DomainContext.class, pdo.getDomainContext());
    editor.setPdo(pdo);   // set pdo before editor.isEditAllowed() is invoked below

    if (editor.isViewAllowed()) {
      noViewLabel.setVisible(false);
      editor.getView().setVisible(true);
      editing.set(isEditable() && editor.isEditAllowed());
      // make contents editable if application requested it and the pdo is editable
      editor.setChangeable(isEditing());
    }
    else {
      noViewLabel.setVisible(true);
      editor.getView().setVisible(false);
      editing.set(false);    // no view -> no edit (no need to check again)
    }
    pdoIsNew.set(pdo.isNew());

    editor.getContainer().clearErrors();          // clears any pending error messages
    editor.getContainer().invalidateSavedView();  // don't triggerViewModified via updateView
    editor.getContainer().updateView();           // map model to view

    getContainer().saveView();                    // triggerViewModified when view has changed

    updateTitle();
    updateButtons();

    if (isEditing() && !pdo.isNew() && pdo.isTokenLockProvided()) {
      try {
        pdo.requestTokenLock();
      }
      catch (LockException lx) {
        editing.set(false);
        editor.setChangeable(false);
        deleteButton.setDisable(true);
        Fx.info(PdoUtilities.getInstance().lockExceptionToString(lx, pdo));
      }
    }
  }

  /**
   * Gets the pdo.
   *
   * @return the pdo
   */
  @Override
  public T getPdo() {
    return editor == null ? null : editor.getPdo();
  }


  /**
   * Updates the window title if attached to its own stage.
   */
  public void updateTitle() {
    Stage stage = getStage();
    if (stage != null) {
      stage.setTitle(MessageFormat.format(
              isModal() ?
                (isEditing() ?
                        resources.getString("Edit {0} {1} (modal)") : resources.getString("View {0} {1} (modal)")) :
                (isEditing() ?
                        resources.getString("Edit {0} {1}") : resources.getString("View {0} {1}")),
              getPdo().getSingular(), getPdo().toString()));
    }
  }


  @FXML
  private void initialize() {
    securityButton.setGraphic(Fx.createImageView("security"));
    securityButton.setOnAction(e -> security());

    previousButton.setGraphic(Fx.createImageView("up"));
    previousButton.setOnAction(e -> previous());

    nextButton.setGraphic(Fx.createImageView("down"));
    nextButton.setOnAction(e -> next());
    setPdoList(null);

    treeButton.setGraphic(Fx.createImageView("browser"));
    treeButton.setOnAction(e -> tree());

    findButton.setGraphic(Fx.createImageView("search"));
    findButton.setOnAction(e -> find());

    newButton.setGraphic(Fx.createImageView("new"));
    newButton.setOnAction(e -> newPdo());

    saveButton.setGraphic(Fx.createImageView("save"));
    saveButton.setOnAction(e -> save());

    deleteButton.setGraphic(Fx.createImageView("delete"));
    deleteButton.setOnAction(e -> delete());

    printButton.setGraphic(Fx.createImageView("print"));
    printButton.setOnAction(e -> print());

    cancelButton.setGraphic(Fx.createImageView("close"));
    cancelButton.setOnAction(e -> cancel());
    cancelButton.setCancelButton(true);
  }


  @Override
  public void configure() {

    saveButton.disableProperty().bind(getContainer().viewModifiedProperty().or(pdoIsNew).and(editing).not());

    eventProxy = new EventListenerProxy<>(getView());

    treeButton.addEventHandler(MouseEvent.ANY, (MouseEvent event) -> {
      if (event.isPopupTrigger()) {
        T pdo = getPdo();
        if (pdo != null && !pdo.isNew()) {
          // show object ID in a popup
          Popup popup = new Popup();
          TextField field = new TextField(Long.toString(pdo.getId()));
          field.setPrefColumnCount(field.getText().length());
          field.setEditable(false);
          popup.getContent().add(field);
          popup.setAutoHide(true);
          Bounds bounds = treeButton.localToScreen(treeButton.getBoundsInLocal());
          popup.show(treeButton, bounds.getMinX() + bounds.getWidth()/2, bounds.getMinY() + bounds.getHeight() / 2);
        }
        event.consume();
      }
    });

    getView().addEventFilter(KeyEvent.KEY_PRESSED, (KeyEvent event) -> {
      if (!event.isAltDown() && !event.isControlDown() && !event.isMetaDown() &&
          !event.isShiftDown() && !event.isShortcutDown() &&
          event.getCode() == KeyCode.ESCAPE && Fx.isModal(getStage())) {

        cancel();
      }
    });
  }


  /**
   * Shows the browser tree of the current PDO.
   */
  public void tree() {
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL);
    stage.initOwner(getStage());
    TreeView<T> tree = Fx.createNode(TreeView.class);
    GuiProvider<T> guiProvider = Rdc.createGuiProvider(getPdo());
    tree.setCellFactory(guiProvider.getTreeCellFactory());
    TreeItem<T> item = guiProvider.createTreeItem();
    item.setExpanded(true);
    tree.setRoot(item);
    // apply PdoCrud.css
    tree.getStylesheets().addAll(getView().getStylesheets());
    Scene scene = Fx.createScene(tree);
    stage.setScene(scene);
    stage.setTitle(MessageFormat.format(resources.getString("{0} {1} (modal)"),
                   getPdo().getSingular(), getPdo().toString()));
    stage.show();
  }

  /**
   * Navigates to the previous PDO in the list.
   */
  public void previous() {
    if (pdoListIndex > 0) {
      releasePdo(released -> {
        if (released) {
          T prevPdo = pdoList.get(--pdoListIndex);
          if (prevPdo.isImmutable() && editable && prevPdo.isEditAllowed()) {
            prevPdo = prevPdo.reload();
          }
          setPdo(prevPdo);
          updatePrevNextButtons();
          eventProxy.fireEvent(new PdoEvent(getPdo(), getView(), PdoEvent.READ));
        }
      });
    }
  }

  /**
   * Navigates to the next PDO in the list.
   */
  public void next() {
    if (pdoListIndex < pdoList.size() - 1) {
      releasePdo(released -> {
        if (released) {
          T nextPdo = pdoList.get(++pdoListIndex);
          if (nextPdo.isImmutable() && editable && nextPdo.isEditAllowed()) {
            nextPdo = nextPdo.reload();
          }
          setPdo(nextPdo);
          updatePrevNextButtons();
          eventProxy.fireEvent(new PdoEvent(getPdo(), getView(), PdoEvent.READ));
        }
      });
    }
  }


  /**
   * Shows the security dialog.
   */
  public void security() {
    SecurityDialogFactory.getInstance().showDialog(getPdo());
  }


  /**
   * Discards the current PDO and edits a new one.
   */
  public void newPdo() {
    releasePdo(released -> {
      if (released) {
        setPdo(getPdo().on());
        getEditor().requestInitialFocus();
      }
    });
  }

  /**
   * Saves the current PDO.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void save() {
    try {
      getPdo().getSession().transaction(() -> {   // validate and save within transaction!
        if (getEditor().validateForm()) {
          T dup = getPdo().isUniqueDomainKeyProvided() ? getPdo().findDuplicate() : null;
          if (dup != null) {
            Fx.error(MessageFormat.format(RdcFxRdcBundle.getString("{0} ALREADY EXISTS"), dup));
          }
          else {
            T pdo = getPdo().persist();
            if (pdoList != null && pdoListIndex >= 0 && pdoListIndex < pdoList.size()) {
              // set does not work for SortedList (-> UnsupportedOperationException).
              if (pdoList instanceof SortedList) {
                ((SortedList) pdoList).getSource().set(((SortedList) pdoList).getSourceIndex(pdoListIndex), pdo);
                pdoListIndex = pdoList.indexOf(pdo);    // may be changed!
                updatePrevNextButtons();
              }
              else {
                pdoList.set(pdoListIndex, pdo);
              }
            }
            editor.setPdo(pdo);     // make getPdo() deliver the updated reference (before closing the stage (onHidden))
            if (!closeIfModal()) {
              setPdo(pdo);
              getEditor().requestInitialFocus();
            }
            eventProxy.fireEvent(new PdoEvent(pdo, getView(), pdo.getSerial() == 1 ? PdoEvent.CREATE : PdoEvent.UPDATE));
          }
        }
        return null;
      });
    }
    catch (ValidationFailedException ex) {
      showValidationResults(ex);
    }
    catch (NotFoundException nfe) {
      // lock timed out and another user updated the PDO or lock was transferred to another user or removed
      LOGGER.info("saving " + getPdo().toGenericString() + " failed: " + nfe.getLocalizedMessage());
      Fx.error(MessageFormat.format(RdcFxRdcBundle.getString("{0} MODIFIED BY_ANOTHER USER MEANWHILE"), getPdo()));
      T pdo = getPdo().reload();
      if (pdo != null) {
        setPdo(pdo);
      }
    }
  }

  /**
   * Deletes the current PDO.
   */
  public void delete() {
    T pdo = getPdo();
    getPdo().getSession().transaction(() -> {
      pdo.delete();
      return null;
    });
    if (!closeIfModal()) {
      setPdo(pdo.on());
      getEditor().requestInitialFocus();
    }
    eventProxy.fireEvent(new PdoEvent(pdo, getView(), PdoEvent.DELETE));
  }

  /**
   * Closes this CRUD.
   */
  public void cancel() {
    releasePdo(released -> {
      if (released) {
        Stage stage = Fx.getStage(getView());
        if (stage != null) {
          stage.close();
        }
      }
    });
  }

  /**
   * Searches for a PDO.
   */
  public void find() {
    releasePdo(released -> {
      if (released) {
        Rdc.displaySearchStage(getPdo(), Modality.APPLICATION_MODAL, Fx.getStage(getView()), false, items -> {
          if (!items.isEmpty()) {
            setPdo(items.get(0).reload());    // reload because may be cached
            getEditor().requestInitialFocus();
          }
        });
      }
    });
  }


  /**
   * Releases the current PDO.
   *
   * @param released invoked with true if released, false if not (user answered CANCEL)
   */
  public void releasePdo(Consumer<Boolean> released) {
    T oldPdo = getPdo();
    if (oldPdo != null && isEditing()) {
      if (getContainer().isViewModified()) {
        Rdc.showSaveDiscardCancelDialog(answer -> {
          if (Boolean.TRUE.equals(answer)) {    // save
            save();   // this also removes the token lock
            released.accept(Boolean.TRUE);
          }
          else if (Boolean.FALSE.equals(answer)) {    // discard changes
            if (!oldPdo.isNew() && oldPdo.isTokenLockedByMe()) {
              oldPdo.releaseTokenLock();
            }
            setPdo(oldPdo.on());
            released.accept(Boolean.TRUE);
          }
          else {  // cancel
            released.accept(Boolean.FALSE);
          }
        });
      }
      else {
        if (!oldPdo.isNew() && oldPdo.isTokenLockedByMe()) {
          try {
            oldPdo.releaseTokenLock();
          }
          catch (LockException lx) {
            // probably locked by another user meanwhile.
            // just log it and don't bother the user
            LOGGER.info("token unlock failed (ignored): " + lx.getMessage());
          }
        }
        released.accept(Boolean.TRUE);
      }
    }
    else {
      released.accept(Boolean.TRUE);
    }
  }


  /**
   * Prints the PDO.
   */
  public void print() {
    getEditor().print();
  }


  /**
   * Gets the HBox containing all buttons.
   *
   * @return the button container
   */
  public FxHBox getButtonBox() {
    return buttonBox;
  }


  /**
   * Removes all PDO-event handlers and filters.<br>
   * The method is provided to avoid memleaks.
   */
  public void removeAllPdoEventListeners() {
    eventProxy.removeAllEventFilters();
    eventProxy.removeAllEventHandlers();
  }


  /**
   * Adds a PDO-event filter.
   *
   * @param eventType   the event type
   * @param eventFilter the filter
   */
  public void addPdoEventFilter(EventType<PdoEvent> eventType, EventHandler<PdoEvent> eventFilter) {
    eventProxy.addEventFilter(eventType, eventFilter);
  }

  /**
   * Removes a PDO-event filter.
   *
   * @param eventType   the event type
   * @param eventFilter the filter
   */
  public void removePdoEventFilter(EventType<PdoEvent> eventType, EventHandler<PdoEvent> eventFilter) {
    eventProxy.removeEventFilter(eventType, eventFilter);
  }


  /**
   * Adds a PDO-event handler.
   *
   * @param eventType   the event type
   * @param eventHandler the handler
   */
  public void addPdoEventHandler(EventType<PdoEvent> eventType, EventHandler<PdoEvent> eventHandler) {
    eventProxy.addEventHandler(eventType, eventHandler);
  }

  /**
   * Removes a PDO-event handler.
   *
   * @param eventType   the event type
   * @param eventHandler the handler
   */
  public void removePdoEventHandler(EventType<PdoEvent> eventType, EventHandler<PdoEvent> eventHandler) {
    eventProxy.removeEventHandler(eventType, eventHandler);
  }


  /**
   * Updates the visibility and disabled state of the buttons.
   */
  protected void updateButtons() {
    T pdo = getPdo();
    treeButton.setDisable(pdo == null || pdo.isNew() ||
                          !Rdc.createGuiProvider(pdo).providesTreeChildObjects());
    deleteButton.setDisable(!isEditing() || !editor.isRemoveAllowed());
    newButton.setDisable(!isEditing() || !editor.isNewAllowed());
    findButton.setDisable(!isEditable());

    securityButton.setDisable(pdo == null || !pdo.isRootEntity() ||
                              !SecurityDialogFactory.getInstance().isDialogAllowed(pdo.getDomainContext()));

    pdoListIndex = 0;
    boolean prevNextVisible = pdoList != null && !pdoList.isEmpty();
    previousButton.setManaged(prevNextVisible);
    previousButton.setVisible(prevNextVisible);
    nextButton.setManaged(prevNextVisible);
    nextButton.setVisible(prevNextVisible);

    if (prevNextVisible) {
      if (pdo != null) {
        pdoListIndex = pdoList.indexOf(getPdo());
      }
      updatePrevNextButtons();
    }

    if (isModal() || prevNextVisible) {
      findButton.setVisible(false);
      findButton.setManaged(false);
      newButton.setVisible(false);
      newButton.setManaged(false);
      deleteButton.setVisible(false);
      deleteButton.setManaged(false);
    }
    else {
      findButton.setVisible(true);
      findButton.setManaged(true);
      newButton.setVisible(true);
      newButton.setManaged(true);
      deleteButton.setVisible(true);
      deleteButton.setManaged(true);
    }
  }

  /**
   * Enables/Disables the previous and next buttons.
   */
  protected void updatePrevNextButtons() {
    previousButton.setDisable(pdoListIndex <= 0);
    nextButton.setDisable(pdoListIndex >= pdoList.size() - 1);
  }

  /**
   * Closes the stage if modal.
   *
   * @return true if closed
   */
  protected boolean closeIfModal() {
    Stage stage = Fx.getStage(getView());
    if (stage != null && Fx.isModal(stage)) {
      stage.close();
      return true;
    }
    return false;
  }

  /**
   * Shows the validation errors.
   *
   * @param ex the validation exception
   */
  protected void showValidationResults(ValidationFailedException ex) {
    FxUtilities.getInstance().showValidationResults(ex, editor.getValidationMappers(), editor.getBinder());
  }

}
