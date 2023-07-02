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

import org.tentackle.app.Application;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.rdc.app.DesktopApplication;
import org.tentackle.fx.rdc.crud.PdoCrud;
import org.tentackle.fx.rdc.search.PdoSearch;
import org.tentackle.log.Logger;
import org.tentackle.misc.IdentifiableKey;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.AbstractSessionTask;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.SessionDependable;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

interface RdcUtilitiesHolder {
  RdcUtilities INSTANCE = ServiceFactory.createService(RdcUtilities.class);
}

/**
 * RDC-related utility methods.
 *
 * @author harald
 */
@Service(RdcUtilities.class)    // defaults to self
public class RdcUtilities {

  private static final Logger LOGGER = Logger.get(RdcUtilities.class);

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static RdcUtilities getInstance() {
    return RdcUtilitiesHolder.INSTANCE;
  }


  /**
   * Map of CRUD controllers.
   */
  private final Map<Class<?>, Set<PdoCrud<?>>> crudCache = new HashMap<>();

  /**
   * Map of search controllers.
   */
  private final Map<Class<?>, Set<PdoSearch<?>>> searchCache = new HashMap<>();


  /**
   * Creates the RDC utilities.
   */
  public RdcUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
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
   * @param updatedPdo the consumer for the possibly changed pdo if modal, null if no consumer
   * @param configurator optional crud configurator
   */
  public <T extends PersistentDomainObject<T>> void displayCrudStage(
          T pdo, ObservableList<T> pdoList, boolean editable, Modality modality, Window owner,
          Consumer<T> updatedPdo,
          Consumer<PdoCrud<T>> configurator) {

    PdoCrud<T> crud = getCrud(pdo, pdoList, editable, modality, owner);
    if (crud != null) {   // if not already being edited
      Stage stage = crud.getStage();
      if (stage == null && Fx.getStage(crud.getView()) == null) {
        // not bound to its own stage so far and not part of some other stage: create new stage
        stage = Fx.createStage(modality);
        if (owner != null) {
          stage.initOwner(owner);
        }
        Scene scene = Fx.createScene(crud.getView());
        stage.setScene(scene);
        crud.updateTitle();
        final Stage s = stage;
        stage.addEventFilter(WindowEvent.WINDOW_SHOWN, e -> Platform.runLater(() -> {
          Point2D location = FxUtilities.getInstance().determinePreferredStageLocation(s);
          s.setX(location.getX());
          s.setY(location.getY());
          crud.getEditor().requestInitialFocus();
        }));
        // explicit close (pressing cancel or closing the window by a mouse click)
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, e -> crud.releasePdo(released -> {
          if (!released) {
            e.consume();    // don't close
          }
        }));
        stage.addEventFilter(WindowEvent.WINDOW_HIDING, e -> {
          T oldPdo = crud.getPdo();
          if (oldPdo != null && oldPdo.getEditedBy() != 0 && oldPdo.isTokenLockableByMe()) { // my token lock still present (even if timed out)
            // there was no WINDOW_CLOSE_REQUEST, the windows is forced to close by its owner.
            // just unlock and don't ask the user (hiding event cannot be consumed)
            crud.releaseTokenLock(oldPdo);
          }
        });
        stage.addEventFilter(WindowEvent.WINDOW_HIDDEN, e -> {
          // clear all references to avoid memory leaks and side effects
          crud.setPdoList(null);
          crud.removeAllPdoEventListeners();
        });
      } // else: re-use stage

      if (configurator != null) {
        configurator.accept(crud);
      }

      if (stage == null) {
        throw new FxRuntimeException("misconfigured crud stage");
      }

      if (modality != Modality.NONE && updatedPdo != null) {
        stage.setOnHidden(event -> updatedPdo.accept(crud.getPdo()));
      }
      else {
        stage.setOnHidden(null);
      }
      stage.show();
    }
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
  @SuppressWarnings("unchecked")
  public synchronized <T extends PersistentDomainObject<T>> PdoCrud<T> getCrud(
                T pdo, ObservableList<T> pdoList, boolean editable, Modality modality, Window owner) {

    Class<T> pdoClass = pdo.getEffectiveClass();
    Set<PdoCrud<?>> cruds = crudCache.computeIfAbsent(pdoClass, k -> new HashSet<>());

    for (Iterator<PdoCrud<?>> iter = cruds.iterator(); iter.hasNext(); ) {
      PdoCrud<T> crud = (PdoCrud<T>) iter.next();
      Stage stage = Fx.getStage(crud.getView());
      if (stage == null || (!stage.isShowing() && stage.getModality() == modality && stage.getOwner() == owner)) {
        // not showing (i.e. unused), never used or same modality and owner
        crud.setEditable(editable);
        crud.setModal(modality != Modality.NONE);
        crud.setPdo(pdo);
        crud.setPdoList(pdoList);
        return crud;
      }
      // is showing or other modality or different owner, i.e. part of some window which is showing
      if (Objects.equals(pdo, crud.getPdo()) && stage.isShowing() &&    // same PDO
          (crud.isEditing() || !editable)) {    // already being edited or just shown and view-only requested
        stage.toFront();
        return null;
        // else: being edited, being shown or requested editable does not match: get a fresh one
      }
      if (!stage.isShowing() && stage.getModality() == modality && stage.getOwner() != owner &&
          stage.getOwner() != null && !stage.getOwner().isShowing()) {
        // just the wrong owner, which isn't showing:
        // remove from cache to avoid pollution
        iter.remove();
      }
    }

    // no unused cruds: create a new one
    PdoCrud<T> crud = RdcFactory.getInstance().createPdoCrud(pdo, editable, modality != Modality.NONE);
    crud.setPdoList(pdoList);
    cruds.add(crud);
    return crud;
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
  public <T extends PersistentDomainObject<T>> void displaySearchStage(
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
  public <T extends PersistentDomainObject<T>> void displaySearchStage(
                    T pdo, Modality modality, Window owner, boolean createPdoAllowed,
                    Consumer<ObservableList<T>> selectedItems,
                    Consumer<PdoSearch<T>> configurator) {

    PdoSearch<T> search = getSearch(pdo, modality, owner);
    search.setCreatePdoAllowed(createPdoAllowed);
    Stage stage = search.getStage();
    if (stage == null && Fx.getStage(search.getView()) == null) {
      // not bound to its own stage so far and not part of some other stage: create new stage
      stage = Fx.createStage(modality);
      if (owner != null) {
        stage.initOwner(owner);
      }
      Scene scene = Fx.createScene(search.getView());
      stage.setScene(scene);
      search.updateTitle();
      search.setSingleSelectMode(Fx.isModal(stage));
      final Stage s = stage;
      stage.addEventFilter(WindowEvent.WINDOW_SHOWN, e -> Platform.runLater(() -> {
        Point2D location = FxUtilities.getInstance().determinePreferredStageLocation(s);
        s.setX(location.getX());
        s.setY(location.getY());
        search.getFinder().requestInitialFocus();
      }));
      stage.addEventFilter(WindowEvent.WINDOW_HIDDEN,
              // run later to avoid clearing the modal return value
              e -> Platform.runLater(() -> search.setItems(null)));
    } // else: re-use stage

    if (configurator != null) {
      configurator.accept(search);
    }

    if (stage == null) {
      throw new FxRuntimeException("misconfigured search stage");
    }

    if (modality != Modality.NONE && selectedItems != null) {
      stage.setOnHidden(event -> selectedItems.accept(search.getSelectedItems()));
    }
    else {
      stage.setOnHidden(null);
    }
    stage.show();
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
  @SuppressWarnings("unchecked")
  public synchronized <T extends PersistentDomainObject<T>> PdoSearch<T>
         getSearch(T pdo, Modality modality, Window owner) {

    Class<T> pdoClass = pdo.getEffectiveClass();
    Set<PdoSearch<?>> searches = searchCache.computeIfAbsent(pdoClass, k -> new HashSet<>());

    for (Iterator<PdoSearch<?>> iter = searches.iterator(); iter.hasNext(); ) {
      PdoSearch<T> search = (PdoSearch<T>) iter.next();
      Stage stage = Fx.getStage(search.getView());
      if (stage == null || (!stage.isShowing() && stage.getModality() == modality && stage.getOwner() == owner)) {
        search.setItems(null);
        search.setPdo(pdo);
        if (search.getFinder().isTreeShowingInitially()) {
          search.showTree();
        }
        else {
          search.showTable();
        }
        return search;
      }
      if (!stage.isShowing() && stage.getModality() == modality && stage.getOwner() != owner &&
          stage.getOwner() != null && !stage.getOwner().isShowing()) {
        iter.remove();
      }
    }

    // no unused searches with requested modality: create a new one
    PdoSearch<T> search = RdcFactory.getInstance().createPdoSearch(pdo);
    searches.add(search);
    return search;
  }


  /**
   * Shows a question dialog whether to save, discard or cancel editing of a PDO.
   *
   * @param doIt consumer invoked with true to save, false to discard changes, null to cancel and do nothing
   */
  public void showSaveDiscardCancelDialog(Consumer<Boolean> doIt) {
    Alert alert = Fx.createAlert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(RdcFxRdcBundle.getString("QUESTION"));
    alert.setHeaderText(null);
    alert.setContentText(RdcFxRdcBundle.getString("DATA_HAS_BEEN_MODIFIED!_DISCARD,_SAVE_OR_CANCEL?"));
    ButtonType saveButtonType = new ButtonType(RdcFxRdcBundle.getString("SAVE"));
    ButtonType discardButtonType = new ButtonType(RdcFxRdcBundle.getString("DISCARD"));
    ButtonType cancelButtonType = new ButtonType(RdcFxRdcBundle.getString("CANCEL"), ButtonData.CANCEL_CLOSE);
    alert.getButtonTypes().setAll(saveButtonType, discardButtonType, cancelButtonType);
    Button cancelButton = (Button) alert.getDialogPane().lookupButton(cancelButtonType);
    cancelButton.setDefaultButton(true);
    alert.setOnHidden(event -> {
      ButtonType result = alert.getResult();
      if (result == saveButtonType) {
        doIt.accept(Boolean.TRUE);
      }
      else if (result == discardButtonType) {
        doIt.accept(Boolean.FALSE);
      }
      else {
        doIt.accept(null);
      }
    });
    alert.show();
  }


  /**
   * Shows the tree of a PDO in a modal scene.
   *
   * @param pdo the PDO
   * @param <T> the PDO type
   */
  public <T extends PersistentDomainObject<T>> void showTree(T pdo) {
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL);
    TreeView<T> tree = Fx.create(TreeView.class);
    GuiProvider<T> guiProvider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
    tree.setCellFactory(guiProvider.getTreeCellFactory());
    TreeItem<T> item = guiProvider.createTreeItem();
    item.setExpanded(true);
    tree.setRoot(item);
    Scene scene = Fx.createScene(tree);
    stage.setScene(scene);
    stage.setTitle(MessageFormat.format(RdcFxRdcBundle.getString("{0} {1} (modal)"),
                   pdo.getSingular(), pdo.toString()));
    stage.show();
  }


  /**
   * Runs some code in another thread and optionally updates the UI.<br>
   * The method is provided to hide the concrete implementation and make it replaceable.<br>
   * Use this method whenever some lengthy operation (database I/O, for example) would otherwise
   * block the event queue.
   * <p>
   * The default implementation creates a {@link AbstractSessionTask} and runs it
   * on behalf of the {@link ModificationTracker}. If the runner itself is a {@link SessionDependable}
   * it gets the session set as well before it is invoked. The result is then passed to {@code updateUI}
   * and executed via {@link Platform#runLater(Runnable)}.<br>
   *
   * @param runner the code running in another thread in parallel to the FX thread
   * @param updateUI optional UI updater invoked from within the FX event queue, null if none
   * @param failedUI optional UI updater invoked if execution failed, null if log only as error
   * @param <V> the type returned by the runner
   */
  public <V> void runInBackground(Supplier<V> runner, Consumer<V> updateUI, Consumer<RuntimeException> failedUI) {
    AbstractSessionTask task;
    if (runner instanceof AbstractSessionTask && updateUI == null && failedUI == null) {
      task = (AbstractSessionTask) runner;
    }
    else {
      task = new AbstractSessionTask() {
        @Override
        public void run() {
          try {
            if (runner instanceof SessionDependable) {
              ((SessionDependable) runner).setSession(getSession());
            }
            V value = runner.get();
            if (updateUI != null) {
              Platform.runLater(() -> updateUI.accept(value));
            }
          }
          catch (RuntimeException rx) {
            if (failedUI != null) {
              Platform.runLater(() -> failedUI.accept(rx));
            }
            else {
              LOGGER.severe("background task failed", rx);
            }
          }
        }
      };
    }

    ModificationTracker.getInstance().addTask(task);
  }


  /**
   * Creates the dragboard for a node and PDOs.
   *
   * @param node the drag source
   * @param pdos the PDOs
   * @return the dragboard, null if PDOs are not transferable
   */
  public Dragboard createDragboard(Node node, PersistentDomainObject<?>... pdos) {
    if (pdos != null) {
      StringBuilder buf = new StringBuilder();
      for (PersistentDomainObject<?> pdo : pdos) {
        if (pdo.getClassId() != 0 && !pdo.isNew()) {
          if (!buf.isEmpty()) {
            buf.append(',');
          }
          buf.append(pdo.toIdString());
        }
      }
      if (!buf.isEmpty()) {
        Dragboard dragboard = node.startDragAndDrop(TransferMode.COPY);
        ClipboardContent content = new ClipboardContent();
        content.putString(buf.toString());
        dragboard.setContent(content);
        return dragboard;
      }
    }
    return null;
  }


  /**
   * Gets all PDO keys from the dragboard.
   *
   * @param dragboard the dragboard
   * @return the keys, empty list if the dragboard's string is null or empty
   */
  public List<IdentifiableKey<PersistentDomainObject<?>>> getPdoKeysFromDragboard(Dragboard dragboard) {
    List<IdentifiableKey<PersistentDomainObject<?>>> keys = new ArrayList<>();
    String idString = dragboard.getString();
    if (idString != null) {
      StringTokenizer stok = new StringTokenizer(idString, ",");
      while (stok.hasMoreTokens()) {
        keys.add(PdoUtilities.getInstance().idStringToIdentifiableKey(stok.nextToken()));
      }
    }
    return keys;
  }


  /**
   * Gets the PDO key from the dragboard.<br>
   * If the dragboard contains more than one key, the first will be returned.
   *
   * @param dragboard the dragboard
   * @return the key, null if the dragboard's string is null or empty
   */
  public IdentifiableKey<PersistentDomainObject<?>> getPdoKeyFromDragboard(Dragboard dragboard) {
    List<IdentifiableKey<PersistentDomainObject<?>>> pdoKeysFromDragboard = getPdoKeysFromDragboard(dragboard);
    return pdoKeysFromDragboard.isEmpty() ? null : pdoKeysFromDragboard.get(0);
  }


  /**
   * Gets all PDOs from a dragboard.
   *
   * @param dragboard the dragboard
   * @param context the domain context
   * @return the PDOs, empty list if dragboard doesn't contain a pdo key or no such pdos
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public List<PersistentDomainObject<?>> getPdosFromDragboard(Dragboard dragboard, DomainContext context) {
    List<PersistentDomainObject<?>> pdos = new ArrayList<>();
    for (IdentifiableKey pdoKey : getPdoKeysFromDragboard(dragboard)) {
      PersistentDomainObject pdo = Pdo.create(pdoKey.getIdentifiableClass(), context).select(pdoKey.getIdentifiableId());
      if (pdo != null) {
        pdos.add(pdo);
      }
    }
    return pdos;
  }

  /**
   * Gets the PDO from a dragboard.<br>
   * If the dragboard contains more than one key, the first PDO will be returned.
   *
   * @param dragboard the dragboard
   * @param context the domain context
   * @return the PDO, null if dragboard doesn't contain a pdo key or no such pdo
   */
  public PersistentDomainObject<?> getPdoFromDragboard(Dragboard dragboard, DomainContext context) {
    List<PersistentDomainObject<?>> pdos = getPdosFromDragboard(dragboard, context);
    return pdos.isEmpty() ? null : pdos.get(0);
  }


  /**
   * Selects a file via file chooser dialog and remembers the decision in the user preferences.
   *
   * @param prefName the preferences name
   * @param prefKey the preferences key
   * @param fileExtension the filename extension (including the leading dot)
   * @param fileType the localized name of the file type
   * @param owner the dialog owner for the selection dialog, null if none
   * @return the selected file, null if aborted
   */
  public File selectFile(String prefName, String prefKey, String fileExtension, String fileType, Stage owner) {
    // prefs stored in the database don't make sense in this case.
    // the filename applies to a machine + user only!
    Preferences prefs = Preferences.userRoot();
    String applicationName = Application.getInstance().getName();
    if (!StringHelper.isAllWhitespace(applicationName)) {
      prefs = prefs.node(applicationName);
    }
    prefs = prefs.node(prefName);
    String lastName = prefs.get(prefKey, null);

    FileChooser fc = new FileChooser();
    if (lastName != null) {
      fc.setInitialFileName(lastName);
    }
    fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(fileType, fileExtension));
    File selectedFile = fc.showSaveDialog(owner);
    if (selectedFile != null) {
      if (!selectedFile.getName().toLowerCase(Locale.ROOT).endsWith(fileExtension)) {
        selectedFile = new File(selectedFile.getPath() + fileExtension);
      }
      // remember the last filename in userspace
      String filePath = selectedFile.getAbsolutePath();
      prefs.put(prefKey, filePath);
      try {
        prefs.flush();
      }
      catch (BackingStoreException bx) {
        // does no harm if not successful
        LOGGER.warning("cannot save user preferences {0}", prefs.absolutePath());
        // log stacktrace only if level is FINE
        LOGGER.fine(filePath, bx);
      }
    }
    return selectedFile;
  }


  /**
   * Gets the main stage of the current application.
   *
   * @return the main stage
   */
  public Stage getMainStage() {
    return ((DesktopApplication<?>) Application.getInstance()).getMainStage();
  }

}
