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

package org.tentackle.fx.rdc;

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.rdc.crud.PdoCrud;
import org.tentackle.fx.rdc.search.PdoSearch;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Collected factory methods for FX-RDC-related stuff.<br>
 * Enhances code readability.
 *
 * @author harald
 */
public class Rdc {

  /**
   * Creates a CRUD controller for a pdo.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @param editable true if user may edit the pdo, false if to view only
   * @param modal true if modal mode
   * @return the crud controller
   */
  public static <T extends PersistentDomainObject<T>> PdoCrud<T> createPdoCrud(T pdo, boolean editable, boolean modal) {
    return RdcFactory.getInstance().createPdoCrud(pdo, editable, modal);
  }

  /**
   * Creates a search controller for a pdo.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @return the search controller
   */
  public static <T extends PersistentDomainObject<T>> PdoSearch<T> createPdoSearch(T pdo) {
    return RdcFactory.getInstance().createPdoSearch(pdo);
  }


  /**
   * Creates a service object that provides methods related to the rdc gui.
   * <p>
   * Throws a {@link org.tentackle.pdo.PdoRuntimeException} if provider could not be created.
   *
   * @param <T> the PDO's class
   * @param pdo the PDO
   * @return the GUI provider, never null
   */
  public static <T extends PersistentDomainObject<T>> GuiProvider<T> createGuiProvider(T pdo) {
    return GuiProviderFactory.getInstance().createGuiProvider(pdo);
  }


  /**
   * Creates a table popup for a table view.
   *
   * @param <S> the row type
   * @param table the table
   * @param preferencesSuffix the optional preferences suffix to load/save table preferences
   * @param title the optional title of the printed table
   * @return the popup
   */
  public static <S> TablePopup<S> createTablePopup(FxTableView<S> table, String preferencesSuffix, String title) {
    return RdcFactory.getInstance().createTablePopup(table, preferencesSuffix, title);
  }

  /**
   * Creates a table popup for a tree table view.
   *
   * @param <S> the row type
   * @param treeTable the treetable
   * @param preferencesSuffix the optional preferences suffix to load/save table preferences
   * @param title the optional title of the printed table
   * @return the popup
   */
  public static <S> TablePopup<S> createTablePopup(FxTreeTableView<S> treeTable, String preferencesSuffix, String title) {
    return RdcFactory.getInstance().createTablePopup(treeTable, preferencesSuffix, title);
  }


  /**
   * Creates a tree item for a pdo.
   *
   * @param pdo the pdo
   * @param <T> the pdo type
   * @return the tree item
   */
  public static <T extends PersistentDomainObject<T>> TreeItem<T> createTreeItem(T pdo) {
    return RdcFactory.getInstance().createTreeItem(pdo);
  }

  /**
   * Creates a tree cell for a pdo type.<br>
   * Usage:
   * <pre>
   *   treeView.setCellFactory(Rdc::createTreeCell);
   * </pre>
   *
   * @param treeView the tree view
   * @param <T> the pdo type
   * @return the tree cell
   */
  public static <T extends PersistentDomainObject<T>> TreeCell<T> createTreeCell(TreeView<T> treeView) {
    return RdcFactory.getInstance().createTreeCell(treeView);
  }

  /**
   * Creates a table cell for a pdo type.
   * Usage:
   * <pre>
   *   tableColumn.setCellFactory(Rdc::createTableCell);
   * </pre>
   *
   * @param columnConfig the table column configuration
   * @param <S> the table row's type
   * @param <T> the table cell's pdo type
   * @return the tree table cell
   */
  public static <S, T extends PersistentDomainObject<T>> PdoTableCell<S, T> createTableCell(TableColumnConfiguration<S, T> columnConfig) {
    return RdcFactory.getInstance().createTableCell(columnConfig);
  }

  /**
   * Creates a tree table cell for a pdo type.
   * Usage:
   * <pre>
   *   treeTableColumn.setCellFactory(Rdc::createTreeTableCell);
   * </pre>
   *
   * @param columnConfig the table column configuration
   * @param <S> the table row's type
   * @param <T> the table cell's pdo type
   * @return the tree table cell
   */
  public static <S, T extends PersistentDomainObject<T>> PdoTreeTableCell<S, T> createTreeTableCell(TableColumnConfiguration<S, T> columnConfig) {
    return RdcFactory.getInstance().createTreeTableCell(columnConfig);
  }

  /**
   * CRUD of a PDO in a non-modal separate window.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @param editable true if user may edit the pdo, false if to view only
   * @param owner the owner, null if none
   */
  public static <T extends PersistentDomainObject<T>> void displayCrudStage(T pdo, boolean editable, Window owner) {
    displayCrudStage(pdo, null, editable, Modality.NONE, owner, null, null);
  }

  /**
   * CRUD of a PDO in a separate window.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @param editable true if user may edit the pdo, false if to view only
   * @param modality the modality
   * @param owner the owner, null if none
   * @param updatedPdo the consumer for the possibly changed pdo if modal
   */
  public static <T extends PersistentDomainObject<T>> void displayCrudStage(
          T pdo, boolean editable, Modality modality, Window owner, Consumer<T> updatedPdo) {
    displayCrudStage(pdo, null, editable, modality, owner, updatedPdo, null);
  }

  /**
   * CRUD of a PDO in a separate window.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @param pdoList the optional list of PDOs to navigate in the list
   * @param editable true if user may edit the pdo, false if to view only
   * @param modality the modality
   * @param owner the owner, null if none
   * @param updatedPdo the consumer for the possibly changed pdo if modal
   * @param configurator optional crud configurator
   */
  public static <T extends PersistentDomainObject<T>> void displayCrudStage(
          T pdo, ObservableList<T> pdoList, boolean editable, Modality modality, Window owner, Consumer<T> updatedPdo, Consumer<PdoCrud<T>> configurator) {
    RdcUtilities.getInstance().displayCrudStage(pdo, pdoList, editable, modality, owner, updatedPdo, configurator);
  }

  /**
   * Gets a CRUD for a PDO.<br>
   * If the PDO is already being edited the corresponding stage will be brought to front and null is returned.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @param pdoList the optional list of PDOs to navigate in the list
   * @param editable true if user may edit the pdo, false if to view only
   * @param modality the modality
   * @param owner the owner, null if none
   * @return the CRUD, null if there is already a CRUD editing this PDO.
   */
  public static <T extends PersistentDomainObject<T>> PdoCrud<T> getCrud(
          T pdo, ObservableList<T> pdoList, boolean editable, Modality modality, Window owner) {
    return RdcUtilities.getInstance().getCrud(pdo, pdoList, editable, modality, owner);
  }

  /**
   * Gets a CRUD stage for a PDO.<br>
   * Useful to check whether a PDO is being edited or shown currently.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @param editable true if user may edit the pdo, false if to view only
   * @return the CRUD showing stage, null if there is no such stage
   */
  @SuppressWarnings("unchecked")
  public static <T extends PersistentDomainObject<T>> Stage getCrud(T pdo, boolean editable) {
    return RdcUtilities.getInstance().getCrud(pdo, editable);
  }

  /**
   * Searches for PDOs in a non-modal separate window.
   *
   * @param <T> the pdo type
   * @param pdo the pdo as a template
   * @param owner the owner, null if none
   * @param createPdoAllowed true if allow to create a new PDO from within the search dialog
   */
  public static <T extends PersistentDomainObject<T>> void displaySearchStage(
      T pdo, Window owner, boolean createPdoAllowed) {
    displaySearchStage(pdo, Modality.NONE, owner, createPdoAllowed, null, null);
  }

  /**
   * Searches for PDOs in a separate window.
   *
   * @param <T> the pdo type
   * @param pdo the pdo as a template
   * @param modality the modality
   * @param owner the owner, null if none
   * @param createPdoAllowed true if allow to create a new PDO from within the search dialog
   * @param selectedItems the consumer for the selected PDOs if modal, null if not modal
   */
  public static <T extends PersistentDomainObject<T>> void displaySearchStage(
                    T pdo, Modality modality, Window owner, boolean createPdoAllowed, Consumer<ObservableList<T>> selectedItems) {
    displaySearchStage(pdo, modality, owner, createPdoAllowed, selectedItems, null);
  }

  /**
   * Searches for PDOs in a separate window.
   *
   * @param <T> the pdo type
   * @param pdo the pdo as a template
   * @param modality the modality
   * @param owner the owner, null if none
   * @param createPdoAllowed true if allow to create a new PDO from within the search dialog
   * @param selectedItems the consumer for the selected PDOs if modal, null if not modal
   * @param configurator the optional configurator for the PdoSearch
   */
  public static <T extends PersistentDomainObject<T>> void displaySearchStage(
                    T pdo, Modality modality, Window owner, boolean createPdoAllowed,
                    Consumer<ObservableList<T>> selectedItems,
                    Consumer<PdoSearch<T>> configurator) {
    RdcUtilities.getInstance().displaySearchStage(pdo, modality, owner, createPdoAllowed, selectedItems, configurator);
  }

  /**
   * Gets a search controller for a PDO.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @param modality the modality
   * @param owner the owner, null if none
   * @return the search controller, never null
   */
  public static <T extends PersistentDomainObject<T>> PdoSearch<T> getSearch(T pdo, Modality modality, Window owner) {
    return RdcUtilities.getInstance().getSearch(pdo, modality, owner);
  }

  /**
   * Shows the tree of a PDO in a modal scene.
   *
   * @param pdo the PDO
   * @param <T> the PDO type
   */
  public static <T extends PersistentDomainObject<T>> void showTree(T pdo) {
    RdcUtilities.getInstance().showTree(pdo);
  }

  /**
   * Shows a question dialog whether to save, discard or cancel editing of a PDO.
   *
   * @param doIt consumer invoked with true to save, false to discard changes, null to cancel and do nothing
   */
  public static void showSaveDiscardCancelDialog(Consumer<Boolean> doIt) {
    RdcUtilities.getInstance().showSaveDiscardCancelDialog(doIt);
  }

  /**
   * Closes the stage hierarchy of given node, except the main stage.
   *
   * @param node the node
   */
  public static void closeStageHierarchy(Node node) {
    FxUtilities.getInstance().closeStageHierarchy(node, RdcUtilities.getInstance().getMainStage());
  }


  /**
   * Runs some code in another thread and optionally updates the UI.<br>
   * The method is provided to hide the concrete implementation and make it replaceable.<br>
   * Use this method whenever some lengthy operation (database I/O, for example) would otherwise
   * block the event queue.
   * <p>
   * Example:
   * <pre>
   *   Rdc.bg(() -&gt; {
   *              on(MyPdo.class).findWhateverItTakes()...
   *              ...
   *              return "Done";
   *          },
   *          s -&gt; Fx.info(s));
   * </pre>
   *
   * @param runner the code running in another thread in parallel to the FX thread
   * @param updateUI optional UI updater invoked from within the FX event queue
   * @param failedUI optional UI updater invoked if execution failed
   * @param <V> the type returned by the runner
   * @see RdcUtilities#runInBackground(Supplier, Consumer, Consumer)
   */
  public static <V> void bg(Supplier<V> runner, Consumer<V> updateUI, Consumer<RuntimeException> failedUI) {
    RdcUtilities.getInstance().runInBackground(runner, updateUI, failedUI);
  }

  /**
   * Same as {@link #bg(Supplier, Consumer, Consumer)} but with a predefined exception consumer.<br>
   * The exception is shown as an error dialog.
   *
   * @param runner the code running in another thread in parallel to the FX thread
   * @param updateUI optional UI updater invoked from within the FX event queue
   * @param <V> the type returned by the runner
   */
  public static <V> void bg(Supplier<V> runner, Consumer<V> updateUI) {
    bg(runner, updateUI, rx -> Fx.error(rx.getLocalizedMessage(), rx));
  }

  /**
   * Runs some code in another thread.
   *
   * @param runner the code running in another thread in parallel to the FX thread
   * @see #bg(Supplier, Consumer)
   */
  public static void bg(Runnable runner) {
    bg(() -> {
      runner.run();
      return null;
    }, null);
  }


  private Rdc() {}
}
