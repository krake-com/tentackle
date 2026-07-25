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

package org.tentackle.fx.rdc.search;

import org.tentackle.common.StringHelper;
import org.tentackle.common.StringNormalizer;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.CaseConversion;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.fx.component.FxTreeView;
import org.tentackle.fx.container.FxBorderPane;
import org.tentackle.fx.container.FxHBox;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.PdoController;
import org.tentackle.fx.rdc.PdoEvent;
import org.tentackle.fx.rdc.PdoFinder;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.fx.rdc.crud.PdoCrud;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.fx.table.TotalsTableView;
import org.tentackle.misc.Holder;
import org.tentackle.pdo.PersistentDomainObject;

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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Search controller for PDOs.
 *
 * @author harald
 * @param <T> the PDO type
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class PdoSearch<T extends PersistentDomainObject<T>> extends AbstractFxController implements PdoController<T> {

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

  @FXML
  private ResourceBundle resources;             // the resources

  /**
   * The items found.<br>
   * Initially empty, never null.
   */
  private ObservableList<T> items = new SortedList<>(FXCollections.observableArrayList());

  private InvalidationListener itemsListener;   // listener for items totals change
  private PdoFinder<T> finder;                  // the finder
  private boolean viewSelectionPending;         // true if initial tree- or table view selection pending
  private PdoCrud<T> pdoCrud;                   // the CRUD view
  private FxBorderPane tablePane;               // the border pane holding the table view
  private FxTableView<T> tableView;             // the table showing the results
  private TotalsTableView<T> totalsView;        // the (optional) table showing the totals
  private FxTreeView<T> treeView;               // the tree showing the results
  private boolean treeShown;                    // true if the tree shown, else table
  private TreeItem<T> rootItem;                 // root item to detect top level nodes
  private boolean tableViewInvalid;             // true if tableview is invalid
  private boolean treeViewInvalid;              // true if tree-view is invalid
  private boolean singleSelectMode;             // true if in (modal) single selection mode
  private BooleanProperty wrongTypeSelected;    // true if wrong type in the selected tree cell
  private T createdPdo;                         // != null if the new PDO has been created in modal mode
  private Consumer<T> doubleClickHandler;       // handler to be invoked on double-click in non-modal mode
  private String noDataMessage;                 // optional message to be displayed when there is no data in the table
  private Node searchIcon;                      // replacing search
  private Node searchPlusIcon;                  // adding search
  private boolean shiftDown;                    // true if the shift key is down
  private boolean mouseOverFindButton;          // true if mouse over findButton


  /**
   * Creates a search controller.
   */
  public PdoSearch() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the message displayed in tableview when there was no data found.
   *
   * @return the placeholder message, null if the default message, empty if no message at all
   */
  public String getNoDataMessage() {
    return noDataMessage;
  }

  /**
   * Sets the message displayed in tableview when there was no data found.
   *
   * @param noDataMessage the placeholder message, null if the default message, empty if no message at all
   */
  public void setNoDataMessage(String noDataMessage) {
    this.noDataMessage = noDataMessage;
  }

  /**
   * Gets the double click handler.
   *
   * @return the handler, null if default handler
   */
  public Consumer<T> getDoubleClickHandler() {
    return doubleClickHandler;
  }

  /**
   * Sets the double click handler.<br>
   * If the search dialog is non-modal and the user double-clicks on a row of the table view,
   * this handler will be invoked instead of the default (showing the PDO-editor if any provided).
   *
   * @param doubleClickHandler the handler, null to clear
   */
  public void setDoubleClickHandler(Consumer<T> doubleClickHandler) {
    this.doubleClickHandler = doubleClickHandler;
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
   * Sets the pdo finder.
   *
   * @param finder the finder
   */
  public void setFinder(PdoFinder<T> finder) {
    if (this.finder != null) {
      throw new RdcRuntimeException("finder already set");
    }
    this.finder = finder;
    viewSelectionPending = true;
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
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void setPdo(T pdo) {
    Objects.requireNonNull(pdo, "pdo");

    createdPdo = null;
    finder.setPdo(pdo);
    finder.getContainer().updateView();
    setFinderVisible(finder.isVisible());
    GuiProvider<T> guiProvider = Rdc.createGuiProvider(pdo);
    tableView = guiProvider.createTableView();
    tablePane.setCenter(tableView);
    tableView.setOnMousePressed(event -> {
      if (event.isPrimaryButtonDown() && event.getClickCount() == 2) {
        T firstPdo = tableView.getSelectionModel().getSelectedItem();
        if (firstPdo != null) {
          if (Fx.isModal(getStage())) {
            close();
          }
          else {
            if (doubleClickHandler != null) {
              doubleClickHandler.accept(firstPdo);
            }
            else if (Rdc.createGuiProvider(firstPdo).isEditorAvailable()) {
              if (isCrudShown()) {
                pdoCrud.releasePdo(released -> {
                  if (released) {
                    Stage stage = Rdc.getCrudStage(firstPdo, true);
                    if (stage != null) {
                      stage.toFront();
                    }
                    else {
                      pdoCrud.setPdo(firstPdo.reload());
                    }
                  }
                });
              }
              else {
                Rdc.displayCrudStage(firstPdo.reload(), items, true, Modality.NONE, getStage(), null, crud -> {
                  pdoCrud = crud;
                  // event filter will be removed when the stage is hidden (see RdcUtilities)
                  crud.addPdoEventFilter(PdoEvent.ANY, e -> {
                    if (e.getEventType() == PdoEvent.UPDATE) {    // INSERT or DELETE doesn't happen in this config
                      T updatedPdo = firstPdo.reload();
                      if (!treeViewInvalid) {
                        ObservableList<TreeItem<T>> treeItems = treeView.getRoot().getChildren();
                        int i = 0;
                        for (TreeItem<T> item : treeItems) {
                          if (updatedPdo.equals(item.getValue())) {
                            // need new TreeItem in case children have changed as well
                            TreeItem<T> updatedItem =
                                Rdc.createGuiProvider(updatedPdo).createTreeItem();
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
                        FxUtilities.getInstance().setSourceElement(items, ndx, updatedPdo);
                      }
                    }
                    else if (e.getEventType() == PdoEvent.READ) { // the up/down button was pressed
                      // do fast checks first (assumed the user did not change the selection)
                      int ndx = treeViewInvalid ? tableView.getSelectionModel().getSelectedIndex() : -1;  // the tree-view needs full search (may contain expanded items)
                      if (ndx > 0 && e.getPdo().equals(items.get(ndx - 1))) {
                        ndx--;
                      }
                      else if (ndx < items.size() - 1 && ndx >= 0 && e.getPdo().equals(items.get(ndx + 1))) {
                        ndx++;
                      }
                      else {
                        // full scan
                        ndx = items.indexOf(e.getPdo());
                      }
                      if (ndx >= 0) {
                        if (treeViewInvalid) {
                          tableView.getSelectionModel().clearSelection();
                          tableView.getSelectionModel().select(ndx);
                          tableView.scrollToCentered(ndx);
                        }
                        else {
                          treeView.getSelectionModel().clearSelection();
                          TreeItem<T> selectedItem = rootItem.getChildren().get(ndx);
                          treeView.getSelectionModel().select(selectedItem);
                          ndx = treeView.getSelectionModel().getSelectedIndex();    // index in tree lines (including expanded children!)
                          if (ndx >= 0) {
                            treeView.scrollToCentered(ndx);
                          }
                        }
                      }
                    }
                    e.consume();
                  });
                });
              }
            }
          }
        }
      }
    });
    TablePopup<T> tablePopup = Rdc.createTablePopup(tableView, null, pdo.getPlural());
    tablePopup.loadPreferences();

    treeView = Fx.create(TreeView.class);
    treeView.setCellFactory(guiProvider.getTreeCellFactory());
    wrongTypeSelected = new SimpleBooleanProperty();
    treeView.getSelectionModel().selectedItemProperty().addListener((_, _, nv) ->
            wrongTypeSelected.set(nv == null || nv.getValue() == null ||
                                  !pdo.getClass().isAssignableFrom(nv.getValue().getClass())));

    StackPane stackPane = Fx.create(StackPane.class);
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
      searchActionProperty.set(_ -> findButton.doClick());
    }

    if (viewSelectionPending) {
      viewSelectionPending = false;
      if (finder.isTreeShowingInitially()) {
        showTree();
      }
      else {
        showTable();
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
    updateSelectMode();
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
    if (finder.validateForm()) {
      filterField.clear();
      if (finder.isSearchRunningInBackground()) {
        Rdc.bg(getView(),
               finder::runSearch,
               result -> {
                 setItems(result);
                 presetScrolling();
               },
               ex -> Fx.error(getView(), ex.getLocalizedMessage(), ex));
      }
      else {
        Platform.runLater(() -> {
          setItems(finder.runSearch());
          presetScrolling();
        });
      }
      cancelButton.setText(resources.getString(Fx.isModal(getStage()) ? "cancel" : "close"));
    }
  }


  /**
   * Presets the scrolling according to the finder's configuration.<br>
   * Scrolls to the end of the table- or tree-view, if {@link PdoFinder#isScrollingToEnd()} == true,
   * otherwise scroll to the beginning.
   */
  protected void presetScrolling() {
    if (items != null && !items.isEmpty()) {
      if (isTreeShown()) {
        treeView.getSelectionModel().clearSelection();
        if (finder.isScrollingToEnd()) {
          // Unfortunately, there is no scrollToEnd which aligns the view to _fully_ display the last line.
          // Therefore, we must calculate the _first_ line shown and hope the last line is fully visible (which is
          // usually not the case and the user must scroll the missing pixels down manually).
          // Same applies to tableView down below.
          // @TODO find a better solution
          treeView.scrollToCentered(treeView.getItems().size() - 1);
        }
        else {
          treeView.scrollTo(0);
        }
      }
      else {
        tableView.getSelectionModel().clearSelection();
        if (finder.isScrollingToEnd()) {
          tableView.scrollToCentered(tableView.getItems().size() - 1);
        }
        else {
          tableView.scrollTo(0);
        }
      }
    }
  }


  /**
   * Filters the viewable PDOs from the list and converts to {@link ObservableList}.
   *
   * @param pdos list of PDOs, null is treated as empty
   * @return the PDOs with view permission
   */
  protected ObservableList<T> filterViewablePdos(List<T> pdos) {
    ObservableList<T> viewablePdos = FXCollections.observableArrayList();
    if (pdos != null) {
      for (T pdo : pdos) {
        if (pdo != null && pdo.isViewAllowed()) {
          viewablePdos.add(pdo);
        }
      }
    }
    return viewablePdos;
  }


  /**
   * Shows the search results.
   *
   * @param pdos the found PDO, null or empty to clear
   */
  public void setItems(List<T> pdos) {
    ObservableList<T> viewablePdos = filterViewablePdos(pdos);
    SortedList<T> sortedItems;
    if (shiftDown && items != null && !items.isEmpty() && !viewablePdos.isEmpty()) {
      // append to the item list if not already in the list
      Set<T> itemSet = new HashSet<>(items);
      ObservableList<T> unsortedItems = FXCollections.observableArrayList(FxUtilities.getInstance().getRootSource(items));
      for (T pdo : viewablePdos) {
        if (!itemSet.contains(pdo)) {
          unsortedItems.add(pdo);
        }
      }
      sortedItems = new SortedList<>(unsortedItems);
    }
    else {
      sortedItems = new SortedList<>(viewablePdos);
    }
    sortedItems.comparatorProperty().bind(tableView.comparatorProperty());
    items = sortedItems;
    countLabel.setText(Integer.toString(items.size()));

    if (treeShown) {
      rootItem = new TreeItem<>();
      for (T item: items) {
        rootItem.getChildren().add(Rdc.createGuiProvider(item).createTreeItem());
      }
      rootItem.setExpanded(true);
      treeView.setRoot(rootItem);
      treeView.setShowRoot(false);
      treeView.getSelectionModel().clearSelection();
      // index from table has worked since we initialized the tree, and nothing is expanded
      int firstIndex = tableView.getSelectionModel().getSelectedIndex();
      if (firstIndex >= 0) {
        tableView.getSelectionModel().getSelectedIndices().forEach(treeView.getSelectionModel()::select);
        treeView.scrollToCentered(firstIndex);
      }
      treeViewInvalid = false;
      tableViewInvalid = true;
    }
    else {
      tableView.setItems(items);
      // the table may wrap the items again, for example if column filters are enabled
      items = tableView.getItems();
      if (items.isEmpty()) {
        Node placeHolder = createPlaceHolder();
        if (placeHolder != null) {
          tableView.setPlaceholder(placeHolder);
        }
      }
      tableView.getSelectionModel().clearSelection();
      Holder<T> firstItemHolder = new Holder<>();
      treeView.getSelectionModel().getSelectedItems().forEach(item -> {
        if (item.getParent() == rootItem) {
          tableView.getSelectionModel().select(item.getValue());
          if (firstItemHolder.get() == null) {
            firstItemHolder.accept(item.getValue());
          }
        }
      });
      T firstItem = firstItemHolder.get();
      if (firstItem != null) {
        tableView.scrollToCentered(firstItem);
      }
      treeViewInvalid = true;
      tableViewInvalid = false;
    }

    if (getPdo().isNormTextProvided()) {
      filterField.setVisible(true);
      filterButton.setVisible(true);
    }

    itemsListener = null;   // to GC -> will remove WeakInvalidationListener eventually
    if (!items.isEmpty()) {
      // show totals only if the result is not empty
      itemsListener = _ -> setTotals(finder.createTotals(items));
      items.addListener(new WeakInvalidationListener(itemsListener));
      itemsListener.invalidated(items);    // show totals first time, if any totals at all
    }
    else {
      setTotals(null);
    }

    // TODO: check if still buggy in newer FX releases.
    // may be lost after dispose and when dialog is reused
    updateSelectMode();
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
    String text = filterField.getText();
    String filterText = StringNormalizer.getInstance().normalize(text);   // removes leading '!' and '-'
    if (!StringHelper.isAllWhitespace(filterText)) {
      boolean inverted = text.startsWith("!") || text.startsWith("-");
      List<T> filteredResult = getItems().stream().
                               filter(p -> p.getNormText() != null && p.getNormText().contains(filterText) != inverted).
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
        treeView.getSelectionModel().getSelectedItems().forEach(p -> {
          if (p.getParent() == rootItem) {
            selectedItems.add(p.getValue());
          }
        });
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
   * @return true, if the tree is shown, else the table is shown
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
      treeView.requestFocus();
      treeShown = true;
      treeButton.setGraphic(Fx.createGraphic("table"));
      treeButton.setTooltip(new Tooltip(resources.getString("table")));
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
    showTree();    // make sure the tree is shown and valid
    return treeView;
  }

  /**
   * Switches to the table view.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void showTable() {
    if (treeShown) {
      tablePane.toFront();
      tableView.requestFocus();
      treeShown = false;
      treeButton.setGraphic(Fx.createGraphic("tree"));
      treeButton.setTooltip(new Tooltip(resources.getString("tree")));
      if (tableViewInvalid && treeView.getRoot() != null) {   // root becomes null if stage closed
        int index = 0;
        for (TreeItem<T> item: treeView.getRoot().getChildren()) {
          T listPdo = items.get(index);
          T treePdo = item.getValue();
          if (listPdo.getSerial() != treePdo.getSerial()) {
            // item was modified in the tree
            FxUtilities.getInstance().setSourceElement(items, index, treePdo);
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
    showTable();    // make sure the table is shown and valid
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
    T nPdo = getPdo().on();
    if (getPdo().isUniqueDomainKeyProvided()) {
      nPdo.setUniqueDomainKey(getPdo().getUniqueDomainKey());
    }
    Rdc.displayCrudStage(nPdo, true, Modality.APPLICATION_MODAL, getStage(), updatedPdo -> {
      if (updatedPdo != null && !updatedPdo.isNew()) {
        createdPdo = updatedPdo;
        close();
      }
    });
  }


  @FXML
  private void initialize() {
    treeButton.setGraphic(Fx.createGraphic("tree"));
    treeButton.setTooltip(new Tooltip(resources.getString("tree")));
    treeButton.setOnAction(_ -> {
      if (treeShown) {
        showTable();
      }
      else {
        showTree();
      }
    });

    filterButton.setGraphic(Fx.createGraphic("filter"));
    filterButton.setOnAction(_ -> filterResult());
    filterField.setOnAction(_ -> filterButton.doClick());

    searchIcon = Fx.createGraphic("search");
    searchPlusIcon = Fx.createGraphic("add");
    findButton.setGraphic(searchIcon);
    findButton.setOnMouseEntered(_ -> {
      mouseOverFindButton = true;
      updateFindButton();
    });
    findButton.setOnMouseExited(_ -> {
      mouseOverFindButton = false;
      updateFindButton();
    });
    findButton.setOnAction(_ -> find());
    findButton.setOnMousePressed(_ -> {
      // onAction will not be triggered if the shift-key is down during mouse click
      if (shiftDown) {
        findButton.doClick();
      }
    });

    newButton.setGraphic(Fx.createGraphic("new"));
    newButton.setOnAction(_ -> createPdo());

    okButton.setGraphic(Fx.createGraphic("ok"));
    okButton.setOnAction(_ -> close());

    cancelButton.setGraphic(Fx.createGraphic("close"));
    cancelButton.setOnAction(_ -> cancel());
    cancelButton.setCancelButton(true);

    tablePane = Fx.create(BorderPane.class);
  }

  @Override
  public void configure() {
    getView().addEventFilter(KeyEvent.KEY_PRESSED, (KeyEvent event) -> {
      if (!event.isAltDown() && !event.isControlDown() && !event.isMetaDown() &&
          !event.isShiftDown() && !event.isShortcutDown() &&
          event.getCode() == KeyCode.ESCAPE && Fx.isModal(getStage())) {

        cancel();
      }
      toggleShiftKey(event.isShiftDown());
    });
    getView().addEventFilter(KeyEvent.KEY_RELEASED, event -> toggleShiftKey(event.isShiftDown()));
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

  /**
   * Creates the placeholder node for the empty search result in the table view.
   *
   * @return the node, null to use table-view's default placeholder
   */
  protected Node createPlaceHolder() {
    Label placeHolder = Fx.create(Label.class);   // default impl always returns a placeholder
    // create default according to PDO type
    String text = Objects.requireNonNullElseGet(noDataMessage, () -> MessageFormat.format(resources.getString("no {0} found"), getPdo().getPlural()));
    placeHolder.setText(text);
    return placeHolder;
  }


  private void updateSelectMode() {
    SelectionMode selectionMode = singleSelectMode ? SelectionMode.SINGLE : SelectionMode.MULTIPLE;
    tableView.getSelectionModel().setSelectionMode(selectionMode);
    treeView.getSelectionModel().setSelectionMode(selectionMode);
  }

  private void toggleShiftKey(boolean shiftDown) {
    if (this.shiftDown != shiftDown) {
      this.shiftDown = shiftDown;
      if (mouseOverFindButton || !shiftDown) {
        updateFindButton();
      }
    }
  }

  private void updateFindButton() {
    Node icon = shiftDown ? searchPlusIcon : searchIcon;
    if (icon != findButton.getGraphic()) {
      findButton.setGraphic(icon);
    }
  }

}
