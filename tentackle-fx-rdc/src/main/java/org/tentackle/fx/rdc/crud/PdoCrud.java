/*
 * Tentackle - http://www.tentackle.org.
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

import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
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
import org.tentackle.app.AbstractApplication;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxControl;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.container.FxBorderPane;
import org.tentackle.fx.container.FxHBox;
import org.tentackle.fx.rdc.EventListenerProxy;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.fx.rdc.InteractiveError;
import org.tentackle.fx.rdc.InteractiveErrorFactory;
import org.tentackle.fx.rdc.PdoEditor;
import org.tentackle.fx.rdc.PdoEvent;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.rdc.security.SecurityDialogFactory;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.LockException;
import org.tentackle.pdo.NotFoundException;
import org.tentackle.pdo.PdoHolder;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.validate.ValidationFailedException;
import org.tentackle.validate.ValidationResult;

/**
 * CRUD controller for PDOs.
 *
 * @author harald
 * @param <T> the PDO type
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class PdoCrud<T extends PersistentDomainObject<T>> extends AbstractFxController implements PdoHolder<T> {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(PdoCrud.class);

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


  private ResourceBundle resources;                 // the resources
  private PdoEditor<T> editor;                      // the editor
  private SimpleBooleanProperty editAllowed;        // is editing allowed effectively
  private List<T> pdoList;                          // optional list of PDOs
  private int pdoListIndex;                         // index in list
  private boolean editable;                         // editability requested by the application
  private EventListenerProxy<PdoEvent> eventProxy;  // event proxy to maintain PdoEvents registered on the View


  /**
   * Sets whether the user can change the editor's contents.
   * <p>
   * Notice: this does not set the changeability of the view!
   * This happens when {@link #setPdo} is invoked.
   *
   * @param editable true if editable
   */
  public void setEditable(boolean editable) {
    this.editable = editable;
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
   * Sets a list of PDOs to walk through via the up and next buttons.
   *
   * @param pdoList the pdos
   */
  public void setPdoList(List<T> pdoList) {
    this.pdoList = pdoList;
    pdoListIndex = 0;
    boolean prevNextVisible = pdoList != null && pdoList.size() > 1;
    previousButton.setManaged(prevNextVisible);
    previousButton.setVisible(prevNextVisible);
    nextButton.setManaged(prevNextVisible);
    nextButton.setVisible(prevNextVisible);
    if (pdoList != null) {
      T pdo = getPdo();
      if (pdo != null) {
        pdoListIndex = pdoList.indexOf(getPdo());
      }
      updatePrevNextButtons();
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
    if (pdo == null) {
      throw new NullPointerException("pdo must not be null");
    }
    editor.setPdo(pdo);
    editor.getContainer().clearErrors();          // clears any pending error messages
    editor.getContainer().invalidateSavedView();  // don't triggerViewModified via updateView
    editor.getContainer().updateView();           // map model to view
    editAllowed = new SimpleBooleanProperty();
    if (editor.isViewAllowed()) {
      noViewLabel.setVisible(false);
      editor.getView().setVisible(true);
      editAllowed.set(isEditable() && editor.isEditAllowed());
      // make contents editable if application requested it and the pdo is editable
      editor.setChangeable(editAllowed.get());
    }
    else {
      noViewLabel.setVisible(true);
      editor.getView().setVisible(false);
      editAllowed.set(false);    // no view -> no edit (no need to check again)
    }
    getContainer().saveView();                    // triggerViewModified when view has changed
    updateTitle();
    GuiProvider<T> guiProvider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
    treeButton.setDisable(pdo.isNew() || !guiProvider.providesTreeChildObjects());
    deleteButton.setDisable(!editAllowed.get() || !editor.isRemoveAllowed());
    newButton.setDisable(!editAllowed.get() || !editor.isNewAllowed());
    saveButton.disableProperty().bind(getContainer().viewModifiedProperty().and(editAllowed).not());
    securityButton.setDisable(!pdo.isRootEntity() ||
                              !SecurityDialogFactory.getInstance().isDialogAllowed(pdo.getDomainContext()));
    getBinder().putBindingProperty(DomainContext.class, pdo.getDomainContext());
    if (editAllowed.get() && !pdo.isNew() && pdo.isTokenLockProvided()) {
      try {
        pdo.requestTokenLock();
      }
      catch (LockException lx) {
        editAllowed.set(false);
        editor.setChangeable(false);
        deleteButton.setDisable(true);
        if (lx.getTokenLock() != null) {
          Fx.info(MessageFormat.format(RdcFxRdcBundle.getString("{0} {1} is being locked by {2} since {3}"),
                  pdo.getSingular(), pdo.toString(),
                  AbstractApplication.getRunningApplication().getUser(pdo.getDomainContext(), pdo.getEditedBy()),
                  pdo.getEditedSince()));
        }
        else {
          LOGGER.severe("locking the PDO failed", lx);
          Fx.error(MessageFormat.format(RdcFxRdcBundle.getString("{0} {1} could not be locked"),
                   pdo.getSingular(), pdo.toString()));
        }
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
              resources.getString("Edit {0} {1}"), getPdo().getSingular(), getPdo().toString()));
    }
  }


  @Override
  public void initialize(URL location, ResourceBundle resources) {

    this.resources = resources;

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
  }


  @Override
  public void configure() {
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
              !event.isShiftDown() && !event.isShortcutDown()) {
        if (event.getCode() == KeyCode.ESCAPE && Fx.isModal(getStage())) {
          cancel();
        }
      }
    });
  }


  /**
   * Shows the browser tree of the current PDO.
   */
  public void tree() {
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL);
    stage.initOwner(getStage());
    @SuppressWarnings("unchecked")
    TreeView<T> tree = Fx.createNode(TreeView.class);
    tree.setCellFactory(p -> Rdc.createTreeCell());
    TreeItem<T> item = GuiProviderFactory.getInstance().createGuiProvider(getPdo()).createTreeItem();
    item.setExpanded(true);
    tree.setRoot(item);
    Scene scene = new Scene(tree);
    stage.setScene(scene);
    stage.showAndWait();
  }

  /**
   * Navigates to the previous PDO in the list.
   */
  public void previous() {
    if (pdoListIndex > 0) {
      if (releasePdo()) {
        setPdo(pdoList.get(--pdoListIndex));
        updatePrevNextButtons();
        eventProxy.fireEvent(new PdoEvent(getPdo(), getView(), PdoEvent.READ));
      }
    }
  }

  /**
   * Navigates to the next PDO in the list.
   */
  public void next() {
    if (pdoListIndex < pdoList.size() - 1) {
      if (releasePdo()) {
        setPdo(pdoList.get(++pdoListIndex));
        updatePrevNextButtons();
        eventProxy.fireEvent(new PdoEvent(getPdo(), getView(), PdoEvent.READ));
      }
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
    if (releasePdo()) {
      setPdo(on());
      getEditor().requestInitialFocus();
    }
  }

  /**
   * Saves the current PDO.
   */
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
    pdo.delete();
    if (!closeIfModal()) {
      setPdo(on());
      getEditor().requestInitialFocus();
    }
    eventProxy.fireEvent(new PdoEvent(pdo, getView(), PdoEvent.DELETE));
  }

  /**
   * Closes this CRUD.
   */
  public void cancel() {
    if (releasePdo()) {
      Stage stage = Fx.getStage(getView());
      if (stage != null) {
        stage.close();
      }
    }
  }

  /**
   * Searches for a PDO.
   */
  public void find() {
    if (releasePdo()) {
      ObservableList<T> items = Rdc.displaySearchStage(
              getPdo(), Modality.APPLICATION_MODAL, Fx.getStage(getView()), false);
      if (!items.isEmpty()) {
        setPdo(items.get(0).reload());    // reload because may be cached
        getEditor().requestInitialFocus();
      }
    }
  }


  /**
   * Releases the current PDO.
   *
   * @return true if released
   */
  public boolean releasePdo() {
    T oldPdo = getPdo();
    if (oldPdo != null && editAllowed.get()) {
      if (getContainer().isViewModified()) {
        Boolean answer = Rdc.showSaveDiscardCancelDialog();
        if (Boolean.TRUE.equals(answer)) {
          save();   // this also removes the token lock
        }
        else if (Boolean.FALSE.equals(answer)) {
          if (!oldPdo.isNew() && oldPdo.isTokenLockProvided() && oldPdo.isTokenLockedByMe()) {
            oldPdo.releaseTokenLock();
          }
          setPdo(on());
        }
        else {
          return false;
        }
      }
      else {
        if (!oldPdo.isNew() && oldPdo.isTokenLockProvided() && oldPdo.isTokenLockedByMe()) {
          try {
            oldPdo.releaseTokenLock();
          }
          catch (LockException lex) {
            // probably locked by another user meanwhile.
            // just log it and don't bother the user
            LOGGER.info("token unlock failed (ignored): " + lex.getLocalizedMessage());
          }
        }
      }
    }
    return true;
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
   * Enables/Disables the previous and next buttons.
   */
  protected void updatePrevNextButtons() {
    previousButton.setDisable(pdoListIndex <= 0);
    nextButton.setDisable(pdoListIndex >= pdoList.size() - 1);
  }

  /**
   * Creates an interactive error from a validation result.
   *
   * @param validationResult the validation result
   * @return the interactive error
   */
  protected InteractiveError createInteractiveError(ValidationResult validationResult) {
    return InteractiveErrorFactory.getInstance().createInteractiveError(
            editor.getValidationMappers(), editor.getBinder(), validationResult);
  }

  /**
   * Creates interactive errors from validation results.
   *
   * @param validationResults the validation results
   * @return the interactive errors
   */
  protected List<InteractiveError> createInteractiveErrors(List<ValidationResult> validationResults) {
    List<InteractiveError> errors = new ArrayList<>();
    for (ValidationResult validationResult: validationResults) {
      errors.add(createInteractiveError(validationResult));
    }
    return errors;
  }


  /**
   * Shows the validation errors.
   *
   * @param ex the validation exception
   */
  protected void showValidationResults(ValidationFailedException ex) {
    StringBuilder warnings = new StringBuilder();
    StringBuilder errors = new StringBuilder();
    List<InteractiveError> errorList = createInteractiveErrors(ex.getResults());
    for (InteractiveError error : errorList) {
      if (error.isWarning()) {
        if (warnings.length() > 0) {
          warnings.append('\n');
        }
        warnings.append(error.getText());
      }
      else {
        if (errors.length() > 0) {
          errors.append('\n');
        }
        errors.append(error.getText());
      }
    }
    if (errors.length() > 0) {
      Fx.error(errors.toString());
    }
    if (warnings.length() > 0) {
      Fx.info(warnings.toString());
    }

    Platform.runLater(() -> {
      for (InteractiveError error : errorList) {
        if (!error.isWarning()) {
          FxControl control = error.getControl();
          if (control instanceof FxComponent) {
            ((FxComponent) control).setError(error.getText());
          }
        }
      }
    });

  }

}
