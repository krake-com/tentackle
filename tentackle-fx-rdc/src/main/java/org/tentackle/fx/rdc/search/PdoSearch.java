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

package org.tentackle.fx.rdc.search;

import java.net.URL;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.CaseConversion;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.fx.component.FxTreeView;
import org.tentackle.fx.container.FxBorderPane;
import org.tentackle.fx.container.FxHBox;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.fx.rdc.PdoEvent;
import org.tentackle.fx.rdc.PdoFinder;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.fx.table.TotalsTableView;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.rdc.RdcFactory;
import org.tentackle.fx.rdc.crud.PdoCrud;
import org.tentackle.pdo.PdoHolder;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.session.ModificationTracker;
import org.tentackle.task.AbstractTask;

/**
 * Search controller for PDOs.
 *
 * @author harald
 * @param <T> the PDO type
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class PdoSearch<T extends PersistentDomainObject<T>> extends AbstractFxController implements PdoHolder<T> {

  @FXML
  private FxBorderPane borderPane;

  @FXML
  private FxHBox buttonBox;

  @FXML
  private FxButton findButton;

  @FXML
  private FxButton okButton;

  @FXML
  private FxButton newButton;

  @FXML
  private FxButton cancelButton;

  @FXML
  private FxButton filterButton;

  @FXML
  private FxTextField filterField;

  @FXML
  private FxButton treeButton;

  @FXML
  private FxLabel countLabel;

  /**
   * The items found.<br>
   * Initially empty, never null.
   */
  private SortedList<T> items = new SortedList<>(FXCollections.observableArrayList());

  private InvalidationListener itemsListener;   // listener for items totals change
  private ResourceBundle resources;             // the resources
  private PdoFinder<T> finder;                  // the finder
  private PdoCrud<T> pdoCrud;                   // the CRUD view
  private FxBorderPane tablePane;               // the border pane holding the table view
  private FxTableView<T> tableView;             // the table showing the results
  private TotalsTableView<T> totalsView;        // the (optional) table showing the totals
  private FxTreeView<T> treeView;               // the tree showing the results
  private boolean treeShown;                    // true if tree shown, else table
  private boolean tableViewInvalid;             // true if tableview is invalid
  private boolean treeViewInvalid;              // true if treeview is invalid
  private boolean singleSelectMode;             // true if in (modal) single selection mode
  private BooleanProperty wrongTypeSelected;    // true if wrong type in selected tree cell
  private T createdPdo;                         // != null if new PDO has been created in modal mode


  /**
   * Gets the HBox containing all buttons.
   *
   * @return the button container
   */
  public FxHBox getButtonBox() {
    return buttonBox;
  }


  /**
   * Sets the pdo finder.
   *
   * @param finder the finder
   */
  public void setFinder(PdoFinder<T> finder) {
    if (this.finder != null) {
      throw new RdcRuntimeException("finder already set");
    }
    this.finder = finder;
    Parent finderView = finder.getView();
    borderPane.setTop(finderView);
    BorderPane.setMargin(finderView, new Insets(5));
  }

  /**
   * Gets the pdo finder.
   *
   * @return the finder
   */
  public PdoFinder<T> getFinder() {
    return finder;
  }


  /**
   * Sets the pdo as a template for the search.
   *
   * @param pdo the pdo
   */
  @Override
  @SuppressWarnings("unchecked")
  public void setPdo(T pdo) {
    if (pdo == null) {
      throw new NullPointerException("pdo must not be null");
    }
    createdPdo = null;
    finder.setPdo(pdo);
    finder.getContainer().updateView();
    setFinderVisible(finder.isVisible());
    tableView = GuiProviderFactory.getInstance().createGuiProvider(pdo).createTableView();
    tablePane.setCenter(tableView);
    tableView.setOnMousePressed(event -> {
      if (event.isPrimaryButtonDown() && event.getClickCount() == 2) {
        T firstPdo = tableView.getSelectionModel().getSelectedItem();
        if (firstPdo != null) {
          if (Fx.isModal(getStage())) {
            close();
          }
          else {
            if (GuiProviderFactory.getInstance().createGuiProvider(firstPdo).editorExists() && !isCrudShown()) {
              Rdc.displayCrudStage(firstPdo.reload(), items, true, Modality.NONE, getStage(), crud -> {
                pdoCrud = crud;
                // event filter will removed when stage is hidden (see RdcUtilities)
                crud.addPdoEventFilter(PdoEvent.ANY, e -> {
                  if (e.getEventType() == PdoEvent.UPDATE) {    // INSERT or DELETE doesn't happen in this config
                    T updatedPdo = firstPdo.reload();
                    if (!treeViewInvalid) {
                      ObservableList<TreeItem<T>> treeItems = treeView.getRoot().getChildren();
                      int i = 0;
                      for (TreeItem<T> item : treeItems) {
                        if (updatedPdo.equals(item.getValue())) {
                          // need new TreeItem in case childs have changed as well
                          TreeItem<T> updatedItem =
                                  GuiProviderFactory.getInstance().createGuiProvider(updatedPdo).createTreeItem();
                          treeItems.set(i, updatedItem);
                          updatedItem.setExpanded(item.isExpanded());
                          break;
                        }
                        i++;
                      }
                    }
                    // items where updated in PdoCrud.save()!
                    int ndx = items.indexOf(updatedPdo);
                    if (ndx >= 0) {
                      ((List) items.getSource()).set(items.getSourceIndex(ndx), updatedPdo);
                    }
                  }
                  else if (e.getEventType() == PdoEvent.READ) { // up/down button pressed
                    // do fast checks first (assumed the user did not change the selection)
                    int ndx = tableView.getSelectionModel().getSelectedIndex();
                    if (ndx > 0 && e.getPdo().equals(items.get(ndx - 1))) {
                      ndx--;
                    }
                    else if (ndx < items.size() - 1 && e.getPdo().equals(items.get(ndx + 1))) {
                      ndx++;
                    }
                    else {
                      // full scan
                      ndx = items.indexOf(e.getPdo());
                    }
                    if (ndx >= 0) {
                      tableView.getSelectionModel().clearSelection();
                      tableView.getSelectionModel().select(ndx);
                      tableView.scrollTo(ndx);
                    }
                  }
                  e.consume();
                });
              });
            }
          }
        }
      }
    });
    TablePopup<T> tablePopup = RdcFactory.getInstance().createTablePopup(tableView, null, false, pdo.getPlural());
    tablePopup.loadPreferences(PersistedPreferencesFactory.getInstance().isSystemOnly());

    treeView = Fx.createNode(TreeView.class);
    treeView.setCellFactory(p -> Rdc.createTreeCell());
    wrongTypeSelected = new SimpleBooleanProperty();
    treeView.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
      wrongTypeSelected.set(nv == null || nv.getValue() == null ||
                            !pdo.getClass().isAssignableFrom(nv.getValue().getClass()));
    });

    StackPane stackPane = Fx.createNode(StackPane.class);
    if (treeShown) {
      stackPane.getChildren().addAll(tablePane, treeView);
    }
    else {
      stackPane.getChildren().addAll(treeView, tablePane);
    }
    treeViewInvalid = true;
    tableViewInvalid = true;
    borderPane.setCenter(stackPane);
    filterField.setVisible(false);
    filterField.setCaseConversion(CaseConversion.UPPER_CASE);
    filterButton.setVisible(false);
    updateTitle();
    bindOkButton();
    if (finder.isSearchRunningImmediately()) {
      find();
    }
    ObjectProperty<EventHandler<ActionEvent>> searchActionProperty = finder.getSearchActionProperty();
    if (searchActionProperty != null) {
      searchActionProperty.set(e -> findButton.doClick());
    }
  }

  /**
   * Gets the pdo.
   *
   * @return the pdo
   */
  @Override
  public T getPdo() {
    return getFinder().getPdo();
  }


  /**
   * Sets the visibility of the finder.<br>
   * The method must be invoked after setPdo because the finder will be made visible
   * according to
   *
   * @param visible true if visible
   */
  public void setFinderVisible(boolean visible) {
    Parent view = finder.getView();
    view.setVisible(visible);
    view.setManaged(visible);
  }

  /**
   * Returns whether the finder is visible.
   *
   * @return true if visible
   */
  public boolean isFinderVisible() {
    return finder.getView().isVisible();
  }


  /**
   * Sets the single selection mode.<br>
   * This mode is usually used in a modal dialog to select exactly one PDO.
   *
   * @param singleSelectMode true if select a single PDO
   */
  public void setSingleSelectMode(boolean singleSelectMode) {
    this.singleSelectMode = singleSelectMode;
    SelectionMode selectionMode = singleSelectMode ? SelectionMode.SINGLE : SelectionMode.MULTIPLE;
    tableView.getSelectionModel().setSelectionMode(selectionMode);
    treeView.getSelectionModel().setSelectionMode(selectionMode);
    okButton.setVisible(singleSelectMode);
    okButton.setManaged(singleSelectMode);
    bindOkButton();
  }

  /**
   * Returns single selection mode.
   *
   * @return true if select a single PDO
   */
  public boolean isSingleSelectMode() {
    return singleSelectMode;
  }


  /**
   * Sets whether creating a new PDO is allowed.
   *
   * @param allowed true if allowed
   */
  public void setCreatePdoAllowed(boolean allowed) {
    newButton.setVisible(allowed);
    newButton.setManaged(allowed);
  }

  /**
   * Returns whether creating a new PDO is allowed.
   *
   * @return true if allowed
   */
  public boolean isCreatePdoAllowed() {
    return newButton.isVisible();
  }

  /**
   * Sets the totals.<br>
   * Shows the results from {@link PdoFinder#createTotals} or hides the totals if none.
   *
   * @see TotalsTableView
   * @param totals the totals (usually one item), null or empty if none
   */
  public void setTotals(ObservableList<T> totals) {
    if (totals != null && !totals.isEmpty()) {
      if (totalsView == null) {
        totalsView = finder.createTotalsTableView();
      }
      totalsView.setBoundTable(tableView);
      totalsView.setItems(totals);
      tablePane.setBottom(totalsView);
      totalsView.applyCss();  // necessary to see the value before being rendered
      totalsView.setPrefHeight(totals.size() * totalsView.getFixedCellSize() + 2);  // +2 due to bounds
    }
    else {
      if (totalsView != null) {
        tablePane.setBottom(null);
        totalsView.setBoundTable(null);
      }
    }
  }

  /**
   * Gets the totals if shown.
   *
   * @return the totals, null if no totals
   */
  public ObservableList<T> getTotalsItems() {
    return totalsView == null ? null : totalsView.getItems();
  }


  /**
   * Updates the window title if attached to its own stage.
   */
  public void updateTitle() {
    Stage stage = getStage();
    if (stage != null) {
      stage.setTitle(MessageFormat.format(
              resources.getString("Search {0}"), getPdo().getPlural()));
    }
  }


  /**
   * Runs the search and displays the results.
   */
  public void find() {
    filterField.clear();
    if (finder.isSearchRunningInBackground()) {
      getView().setCursor(Cursor.WAIT);
      ModificationTracker.getInstance().addTask(new AbstractTask() {
        @Override
        public void run() {
          ObservableList<T> result = finder.runSearch();
          Platform.runLater(() -> {
            setItems(result);
            getView().setCursor(Cursor.DEFAULT);
          });
        }
      });
    }
    else {
      setItems(finder.runSearch());
    }
    cancelButton.setText(resources.getString(Fx.isModal(getStage()) ? "cancel" : "close"));
  }


  /**
   * Shows the search results.
   *
   * @param items the found PDOs
   */
  public void setItems(ObservableList<T> items) {
    if (items == null) {
      items = FXCollections.observableArrayList();
    }
    SortedList<T> sortedItems;
    if (items instanceof SortedList) {
      sortedItems = (SortedList<T>) items;
    }
    else {
      sortedItems = new SortedList<>(items);
      sortedItems.comparatorProperty().bind(tableView.comparatorProperty());
    }
    this.items = sortedItems;
    countLabel.setText(Integer.toString(items.size()));

    if (treeShown) {
      TreeItem<T> root = new TreeItem<>();
      for (T item: items) {
        root.getChildren().add(GuiProviderFactory.getInstance().createGuiProvider(item).createTreeItem());
      }
      root.setExpanded(true);
      treeView.setRoot(root);
      treeView.setShowRoot(false);
      treeView.getSelectionModel().clearSelection();
      treeViewInvalid = false;
      tableViewInvalid = true;
    }
    else {
      tableView.setItems(this.items);
      tableView.getSelectionModel().clearSelection();
      tableView.refresh();
      treeViewInvalid = true;
      tableViewInvalid = false;
    }

    if (getPdo().isNormTextProvided()) {
      filterField.setVisible(true);
      filterButton.setVisible(true);
    }

    itemsListener = null;   // to GC -> will remove WeakInvalidationListener eventually
    if (!this.items.isEmpty()) {
      // show totals only if result is not empty
      itemsListener = (o) -> setTotals(finder.createTotals(this.items));
      this.items.addListener(new WeakInvalidationListener(itemsListener));
      itemsListener.invalidated(this.items);    // show totals first time, if any totals at all
    }
    else {
      setTotals(null);
    }
  }


  /**
   * Gets the items shown in the table or tree.
   *
   * @return the items
   */
  public ObservableList<T> getItems() {
    return items;
  }


  /**
   * Filters the result.
   */
  public void filterResult() {
    String filterText = StringHelper.normalize(filterField.getText());
    if (!StringHelper.isAllWhitespace(filterText)) {
      List<T> filteredResult = getItems().stream().
                               filter(p -> p.getNormText() != null && p.getNormText().contains(filterText)).
                               collect(Collectors.toList());
      setItems(FXCollections.observableArrayList(filteredResult));
    }
  }


  /**
   * Returns the selected PDOs.
   *
   * @return the selected PDOs, empty list, if nothing selected
   */
  public ObservableList<T> getSelectedItems() {
    ObservableList<T> selectedItems;
    if (createdPdo != null) {
      selectedItems = FXCollections.observableArrayList();
      selectedItems.add(createdPdo);
    }
    else {
      if (treeShown) {
        selectedItems = FXCollections.observableArrayList();
        treeView.getSelectionModel().getSelectedItems().stream().forEach(p -> selectedItems.add(p.getValue()));

      }
      else {
        selectedItems = tableView.getSelectionModel().getSelectedItems();
      }
    }
    return selectedItems;
  }


  /**
   * Clears the selection.
   */
  public void clearSelection() {
    tableView.getSelectionModel().clearSelection();
    treeView.getSelectionModel().clearSelection();
  }


  /**
   * Returns whether the tree is shown instead of the table.
   *
   * @return true if tree shown, else table shown
   */
  public boolean isTreeShown() {
    return treeShown;
  }

  /**
   * Switches to the tree view.
   */
  public void showTree() {
    if (!treeShown) {
      treeView.toFront();
      treeShown = true;
      treeButton.setGraphic(Fx.createImageView("table"));
      if (treeViewInvalid) {
        setItems(getItems());
      }
      bindOkButton();
    }
  }

  /**
   * Gets access to the tree view.<br>
   * If the table is shown, it will be switched to the tree view.
   *
   * @return the table view
   */
  public FxTreeView<T> getTreeView() {
    showTree();    // make sure tree is shown and valid
    return treeView;
  }

  /**
   * Switches to the table view.
   */
  @SuppressWarnings("unchecked")
  public void showTable() {
    if (treeShown) {
      tablePane.toFront();
      treeShown = false;
      treeButton.setGraphic(Fx.createImageView("tree"));
      if (tableViewInvalid && treeView.getRoot() != null) {   // root becomes null if stage closed
        int index = 0;
        for (TreeItem<T> item: treeView.getRoot().getChildren()) {
          T listPdo = items.get(index);
          T treePdo = item.getValue();
          if (listPdo.getSerial() != treePdo.getSerial()) {
            // item was modified in tree
            ((List) items.getSource()).set(items.getSourceIndex(index), treePdo);
          }
          index++;
        }
        setItems(items);
      }
      bindOkButton();
    }
  }

  /**
   * Gets access to the table view.<br>
   * If the tree is shown, it will be switched to the table view.
   *
   * @return the table view
   */
  public FxTableView<T> getTableView() {
    showTable();    // make sure table is shown and valid
    return tableView;
  }


  /**
   * Close and cancel.
   */
  public void cancel() {
    clearSelection();
    close();
  }

  /**
   * Close the search.
   */
  public void close() {
    Stage stage = Fx.getStage(getView());
    if (stage != null) {
      stage.close();
    }
  }


  /**
   * Creates a new PDO.
   */
  public void createPdo() {
    T nPdo = on();
    if (getPdo().isUniqueDomainKeyProvided()) {
      nPdo.setUniqueDomainKey(getPdo().getUniqueDomainKey());
    }
    nPdo = Rdc.displayCrudStage(nPdo, true, Modality.APPLICATION_MODAL, getStage());
    if (nPdo != null && !nPdo.isNew()) {
      createdPdo = nPdo;
      close();
    }
  }


  @Override
  public void initialize(URL location, ResourceBundle resources) {

    this.resources = resources;

    treeButton.setGraphic(Fx.createImageView("tree"));
    treeButton.setOnAction(e -> {
      if (treeShown) {
        showTable();
      }
      else {
        showTree();
      }
    });

    filterButton.setGraphic(Fx.createImageView("search"));
    filterButton.setOnAction(e -> filterResult());
    filterField.setOnAction(e -> filterButton.doClick());

    findButton.setGraphic(Fx.createImageView("search"));
    findButton.setOnAction(e -> find());

    newButton.setGraphic(Fx.createImageView("new"));
    newButton.setOnAction(e -> createPdo());

    okButton.setGraphic(Fx.createImageView("ok"));
    okButton.setOnAction(e -> close());

    cancelButton.setGraphic(Fx.createImageView("close"));
    cancelButton.setOnAction(e -> cancel());
    cancelButton.setCancelButton(true);

    tablePane = Fx.createNode(BorderPane.class);
  }

  @Override
  public void configure() {
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
   * Binds the ok button so that it is enabled only if a PDO is selected.
   */
  protected void bindOkButton() {
    okButton.disableProperty().unbind();
    if (okButton.isVisible()) {
      if (treeShown) {
        okButton.disableProperty().bind(treeView.getSelectionModel().selectedItemProperty().isNull()
                                  .or(wrongTypeSelected));
      }
      else {
        okButton.disableProperty().bind(tableView.getSelectionModel().selectedItemProperty().isNull());
      }
    }
  }

  /**
   * Returns whether a CRUD is currently shown for this search.
   *
   * @return true if shown
   */
  protected boolean isCrudShown() {
    if (pdoCrud != null) {
      Stage stage = pdoCrud.getStage();
      if (stage != null && stage.isShowing() && stage.getOwner() == getStage()) {
        return true;
      }
    }
    pdoCrud = null;
    return false;
  }

}
