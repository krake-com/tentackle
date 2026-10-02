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
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.NotificationBuilder;
import org.tentackle.fx.ThemeUtilities;
import org.tentackle.fx.rdc.app.DesktopApplication;
import org.tentackle.fx.rdc.crud.PdoCrud;
import org.tentackle.fx.rdc.search.PdoSearch;
import org.tentackle.log.Logger;
import org.tentackle.misc.Holder;
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
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Popup;
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
  RdcUtilities INSTANCE = ServiceFactory.createService(RdcUtilities.class, RdcUtilities.class);
}


/**
 * RDC-related utility methods.<br>
 * Displays the CRUD and search stages, caches and reuses their controllers per PDO class,
 * runs code in the background, and provides drag-and-drop and file selection helpers.
 * <p>
 * Replaceable via {@code @Service(RdcUtilities.class)}, for example by a subclass of
 * {@link RdcUtilitiesWithBackgroundPool}.
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

  private final boolean cachingEnabled;
  private boolean darkMode;


  /**
   * Creates the RDC utilities.
   */
  public RdcUtilities() {
    Application application = Application.getInstance();
    // we cannot use caching in servers like JRPO (different users, browser instances, etc...)
    // or when using BundleMonkey (application should be started w/o caching to see the changes immediately)
    cachingEnabled = application != null && !application.isServer() &&
                     application.getPropertyIgnoreCase("noRdcCache") == null;
    darkMode = ThemeUtilities.getInstance().isDarkMode();
  }


  /**
   * Invalidates the caches for the search and CRUD dialogs.
   */
  public synchronized void invalidateCaches() {
    crudCache.clear();
    searchCache.clear();
  }

  /**
   * CRUD of a PDO in a separate window.
   *
   * @param <T> the PDO type
   * @param pdo the PDO
   * @param pdoList the optional list of PDOs to navigate in the list
   * @param editable true if the user may edit the PDO, false if to view only
   * @param modality the modality
   * @param owner the owner window, null if none
   * @param updatedPdo the consumer for the possibly changed PDO if modal, null if no consumer
   * @param configurator optional CRUD configurator
   */
  public <T extends PersistentDomainObject<T>> void displayCrudStage(
          T pdo, ObservableList<T> pdoList, boolean editable, Modality modality, Window owner,
          Consumer<T> updatedPdo,
          Consumer<PdoCrud<T>> configurator) {

    PdoCrud<T> crud = getCrud(pdo, pdoList, editable, modality, owner);
    if (crud != null) {   // if not already being edited
      Stage stage = crud.getStage();
      if (stage == null && Fx.getStage(crud.getView()) == null) {
        // not bound to its own stage so far and not part of some other stage: create the new stage
        stage = Fx.createStage(modality, owner);
        Scene scene = Fx.createScene(crud.getView());
        stage.setScene(scene);
        crud.updateTitle();
        final Stage s = stage;
        stage.addEventFilter(WindowEvent.WINDOW_SHOWN, _ -> Platform.runLater(() -> {
          Point2D location = FxUtilities.getInstance().determinePreferredStageLocation(s);
          s.setX(location.getX());
          s.setY(location.getY());
          crud.getEditor().requestInitialFocus();
        }));
        // explicit close (pressing cancel or closing the window by a mouse click)
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, e -> {
          e.consume();
          crud.cancel();
        });
        stage.addEventFilter(WindowEvent.WINDOW_HIDING, _ -> {
          T oldPdo = crud.getPdo();
          if (oldPdo != null && oldPdo.getEditedBy() != 0 && oldPdo.isTokenLockableByMe()) {
            // My token lock is still present (even if timed out).
            // There was no WINDOW_CLOSE_REQUEST, the windows are forced to be closed by its owner.
            // Just unlock and don't ask the user (hiding event cannot be consumed).
            crud.releaseTokenLock(oldPdo);
          }
        });
        stage.addEventFilter(WindowEvent.WINDOW_HIDDEN, _ -> {
          // clear all references to avoid memory leaks and side effects
          crud.setPdoList(null);
          crud.removeAllPdoEventListeners();
        });
      } // else: re-use stage

      if (configurator != null) {
        configurator.accept(crud);
      }

      if (stage == null) {
        throw new RdcRuntimeException("misconfigured crud stage");
      }

      if (modality != Modality.NONE && updatedPdo != null) {
        stage.setOnHidden(_ -> updatedPdo.accept(crud.getPdo()));
      }
      else {
        stage.setOnHidden(null);
      }
      Fx.show(stage);
    }
  }

  /**
   * Gets a CRUD for a PDO.<br>
   * If the PDO is already being edited,
   * the corresponding stage will be brought to the front and null is returned.
   *
   * @param <T> the PDO type
   * @param pdo the PDO
   * @param pdoList the optional list of PDOs to navigate in the list
   * @param editable true if the user may edit the PDO, false if to view only
   * @param modality the modality
   * @param owner the owner window, null if none
   * @return the CRUD, null if there is already a CRUD editing this PDO.
   */
  @SuppressWarnings("unchecked")
  public synchronized <T extends PersistentDomainObject<T>> PdoCrud<T> getCrud(
                T pdo, ObservableList<T> pdoList, boolean editable, Modality modality, Window owner) {

    Set<PdoCrud<?>> cruds;
    if (isCrudCachingEnabled(pdo)) {
      detectThemeChanged();
      Class<T> pdoClass = pdo.getEffectiveClass();
      cruds = crudCache.computeIfAbsent(pdoClass, _ -> new HashSet<>());

      for (Iterator<PdoCrud<?>> iter = cruds.iterator(); iter.hasNext(); ) {
        PdoCrud<T> crud = (PdoCrud<T>) iter.next();
        Stage stage = Fx.getStage(crud.getView());
        if (stage == null || (!stage.isShowing() && stage.getModality() == modality && stage.getOwner() == owner)) {
          // not showing (unused), never used or same modality and owner
          crud.setEditable(editable);
          crud.setModal(modality != Modality.NONE);
          crud.setPdo(pdo);
          crud.setPdoList(pdoList);
          return crud;
        }
        // is showing or other modality or different owner, i.e., part of some window which is showing
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
    }
    else {
      cruds = null;
    }

    // no unused cruds: create a new one
    PdoCrud<T> crud = RdcFactory.getInstance().createPdoCrud(pdo, editable, modality != Modality.NONE);
    crud.setPdoList(pdoList);
    if (cruds != null) {
      cruds.add(crud);
    }
    return crud;
  }

  /**
   * Gets a CRUD that is being used for the PDO.
   *
   * @param pdo the PDO
   * @param editable true if the PDO is being edited, false if viewed only, null if don't care
   * @return the {@link PdoCrud}, null if not being shown or edited
   * @param <T> the PDO type
   */
  @SuppressWarnings("unchecked")
  public synchronized <T extends PersistentDomainObject<T>> PdoCrud<T> getCrudInUse(T pdo, Boolean editable) {
    if (isCrudCachingEnabled(pdo)) {
      Class<T> pdoClass = pdo.getEffectiveClass();
      Set<PdoCrud<?>> pdoCruds = crudCache.get(pdoClass);
      if (pdoCruds != null) {
        for (PdoCrud<?> pdoCrud : pdoCruds) {
          if (pdoCrud.getPdo() == pdo && (editable == null || editable == pdoCrud.isEditing())) {
            return (PdoCrud<T>) pdoCrud;
          }
        }
      }
    }
    return null;
  }

  /**
   * Gets a CRUD stage for a PDO.<br>
   * Useful to check whether a PDO is being edited or shown currently.
   * If found, the stage is brought to the front.
   *
   * @param <T> the PDO type
   * @param pdo the PDO
   * @param editable true if the user may edit the PDO, false if to view only
   * @return the CRUD-showing stage, null if there is no such stage
   */
  public synchronized <T extends PersistentDomainObject<T>> Stage getCrudStage(T pdo, boolean editable) {
    if (isCrudCachingEnabled(pdo)) {
      detectThemeChanged();
      Class<T> pdoClass = pdo.getEffectiveClass();
      Set<PdoCrud<?>> cruds = crudCache.get(pdoClass);
      if (cruds != null) {
        for (PdoCrud<?> crud : cruds) {
          Stage stage = Fx.getStage(crud.getView());
          if (stage != null) {
            // is showing or other modality or different owner, i.e., part of some window which is showing
            if (Objects.equals(pdo, crud.getPdo()) && stage.isShowing() &&    // same PDO
                (crud.isEditing() || !editable)) {    // already being edited or just shown and view-only requested
              stage.toFront();
              return stage;
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Searches for PDOs in a separate window.
   *
   * @param <T> the PDO type
   * @param pdo the PDO as a template
   * @param modality the modality
   * @param owner the owner window, null if none
   * @param createPdoAllowed true if allow creating a new PDO from within the search dialog
   * @param selectedItems the consumer for the selected PDOs if modal, null if not modal
   */
  public <T extends PersistentDomainObject<T>> void displaySearchStage(
                    T pdo, Modality modality, Window owner, boolean createPdoAllowed, Consumer<ObservableList<T>> selectedItems) {
    displaySearchStage(pdo, modality, owner, createPdoAllowed, selectedItems, null);
  }

  /**
   * Searches for PDOs in a separate window.
   *
   * @param <T> the PDO type
   * @param pdo the PDO as a template
   * @param modality the modality
   * @param owner the owner window, null if none
   * @param createPdoAllowed true if allow creating a new PDO from within the search dialog
   * @param selectedItems the consumer for the selected PDOs if modal, null if not modal
   * @param configurator the optional configurator for the {@link PdoSearch}
   */
  public <T extends PersistentDomainObject<T>> void displaySearchStage(
                    T pdo, Modality modality, Window owner, boolean createPdoAllowed,
                    Consumer<ObservableList<T>> selectedItems,
                    Consumer<PdoSearch<T>> configurator) {

    PdoSearch<T> search = getSearch(pdo, modality, owner);
    search.setCreatePdoAllowed(createPdoAllowed);
    Stage stage = search.getStage();
    if (stage == null && Fx.getStage(search.getView()) == null) {
      // not bound to its own stage so far and not part of some other stage: create the new stage
      stage = Fx.createStage(modality, owner);
      Scene scene = Fx.createScene(search.getView());
      stage.setScene(scene);
      search.updateTitle();
      search.setSingleSelectMode(Fx.isModal(stage));
      final Stage s = stage;
      stage.addEventFilter(WindowEvent.WINDOW_SHOWN, _ -> Platform.runLater(() -> {
        Point2D location = FxUtilities.getInstance().determinePreferredStageLocation(s);
        s.setX(location.getX());
        s.setY(location.getY());
        search.getFinder().requestInitialFocus();
      }));
      stage.addEventFilter(WindowEvent.WINDOW_HIDDEN,
              // run later to avoid clearing the modal return value
              _ -> Platform.runLater(() -> search.setItems(null)));
    } // else: re-use stage

    if (configurator != null) {
      configurator.accept(search);
    }

    if (stage == null) {
      throw new RdcRuntimeException("misconfigured search stage");
    }

    if (modality != Modality.NONE && selectedItems != null) {
      stage.setOnHidden(_ -> selectedItems.accept(search.getSelectedItems()));
    }
    else {
      stage.setOnHidden(null);
    }
    Fx.show(stage);
  }

  /**
   * Gets a search controller for a PDO.
   *
   * @param <T> the PDO type
   * @param pdo the PDO
   * @param modality the modality
   * @param owner the owner, null if none
   * @return the search controller, never null
   */
  @SuppressWarnings("unchecked")
  public synchronized <T extends PersistentDomainObject<T>> PdoSearch<T>
         getSearch(T pdo, Modality modality, Window owner) {

    Set<PdoSearch<?>> searches;
    if (isSearchCachingEnabled(pdo)) {
      detectThemeChanged();
      Class<T> pdoClass = pdo.getEffectiveClass();
      searches = searchCache.computeIfAbsent(pdoClass, _ -> new HashSet<>());

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
    }
    else {
      searches = null;
    }

    // no unused searches with requested modality: create a new one
    PdoSearch<T> search = RdcFactory.getInstance().createPdoSearch(pdo);
    if (searches != null) {
      searches.add(search);
    }
    return search;
  }

  /**
   * Shows a question dialog whether to save, discard, or cancel editing of a PDO.
   *
   * @param owner the owner window or node
   * @param doIt consumer invoked with true to save,
   *             false to discard the changes, or null to cancel
   */
  public void showSaveDiscardCancelDialog(Object owner, Consumer<Boolean> doIt) {
    Holder<Boolean> result = new Holder<>();
    Popup popup = new Popup();
    Parent notification =
      FxFactory.getInstance()
               .createNotificationBuilder()
               .type(NotificationBuilder.Type.QUESTION)
               .text(RdcFxRdcBundle.getString("DATA_HAS_BEEN_MODIFIED!_DISCARD,_SAVE_OR_CANCEL?"))
               .button(RdcFxRdcBundle.getString("SAVE"), null, false, () -> result.accept(Boolean.TRUE))
               .button(RdcFxRdcBundle.getString("DISCARD"), null, false, () -> result.accept(Boolean.FALSE))
               .button(RdcFxRdcBundle.getString("CANCEL"), null, true, null)
               .hide(popup::hide)
               .build();
    FxUtilities.getInstance().showNotification(owner, popup, notification, () -> doIt.accept(result.get()));
  }

  /**
   * Shows the tree of a PDO in an application-modal stage.
   *
   * @param owner the owner window or node
   * @param pdo the PDO
   * @param <T> the PDO type
   */
  public <T extends PersistentDomainObject<T>> void showTree(Object owner, T pdo) {
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL, owner);
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
    Fx.show(stage);
  }

  /**
   * Runs some code in another thread and optionally updates the UI.<br>
   * The method is provided to hide the concrete implementation and make it replaceable.<br>
   * Use this method whenever some lengthy operation (database I/O, for example) would otherwise
   * block the event queue.
   * <p>
   * The optional node will get the wait cursor set, while the task is executed in the background.
   * <p>
   * The default implementation creates an {@link AbstractSessionTask} and runs it
   * on behalf of the {@link ModificationTracker}, i.e. tasks are executed one after another
   * in the tracker's thread and session. If the runner itself is a {@link SessionDependable},
   * it gets the session set as well before it is invoked. The result is then passed to {@code updateUI}
   * and executed via {@link Platform#runLater(Runnable)}.<br>
   * If there is no application and the tracker is not running, the task is executed in the current thread.
   *
   * @param <V>      the type returned by the runner
   * @param node     optional node this background task is related to, null if none
   * @param runner   the code running in another thread in parallel to the FX thread
   * @param updateUI optional UI updater invoked from within the FX event queue, null if none
   * @param failedUI optional UI updater invoked from within the FX event queue if execution failed,
   *                 null to log the exception only
   */
  public <V> void runInBackground(Node node, Supplier<V> runner, Consumer<V> updateUI, Consumer<RuntimeException> failedUI) {
    AbstractSessionTask task;
    if (runner instanceof AbstractSessionTask && updateUI == null && failedUI == null && node == null) {
      task = (AbstractSessionTask) runner;
    }
    else {
      Cursor oldCursor;
      if (node != null) {
        Cursor waitCursor = FxUtilities.getInstance().getWaitCursor(node);
        Cursor cursor = node.getCursor();
        // don't restore the wait cursor if already set by another submitted background task
        oldCursor = waitCursor.equals(cursor) ? null : cursor;
        node.setCursor(waitCursor);
      }
      else {
        oldCursor = null;
      }

      task = new AbstractSessionTask() {
        @Override
        public void run() {
          try {
            if (runner instanceof SessionDependable) {
              ((SessionDependable) runner).setSession(getSession());
            }
            V value = runner.get();
            Platform.runLater(this::restoreCursor);
            if (updateUI != null) {
              Platform.runLater(() -> updateUI.accept(value));
            }
          }
          catch (RuntimeException rx) {
            Platform.runLater(this::restoreCursor);
            if (failedUI != null) {
              Platform.runLater(() -> failedUI.accept(rx));
            }
            else {
              LOGGER.severe("background task failed", rx);
            }
          }
        }

        private void restoreCursor() {
          if (node != null) {
            // restore what the node had before, which may be null, i.e., inherited from its parent
            node.setCursor(oldCursor);
          }
        }
      };
    }

    ModificationTracker modificationTracker = ModificationTracker.getInstance();
    if (Application.getInstance() != null ||
        modificationTracker.isAlive()) { // MT may be not alive yet while starting up the application
      ModificationTracker.getInstance().addTask(task);
    }
    else {
      // no application and no tracker running?
      task.run();   // corner case in generic tools like PdoBrowser
    }
  }

  /**
   * Creates the dragboard for a node and PDOs.<br>
   * The PDOs are transferred as a comma-separated list of their ID strings.
   * New PDOs and PDOs without a class ID are skipped.
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
   * @return the PDOs, empty list if the dragboard doesn't contain a PDO key or no such PDOs
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
   * @return the PDO, null if the dragboard doesn't contain a PDO key or no such PDO
   */
  public PersistentDomainObject<?> getPdoFromDragboard(Dragboard dragboard, DomainContext context) {
    List<PersistentDomainObject<?>> pdos = getPdosFromDragboard(dragboard, context);
    return pdos.isEmpty() ? null : pdos.get(0);
  }

  /**
   * Selects a file via the file chooser dialog and remembers the decision in the user preferences.
   *
   * @param prefName the preference name
   * @param prefKey the preference key
   * @param fileExtension the filename extension (including the leading dot)
   * @param fileType the localized name of the file type
   * @param owner the dialog owner for the selection dialog, null if none
   * @return the selected file, null if aborted
   */
  public File selectFile(String prefName, String prefKey, String fileExtension, String fileType, Stage owner) {
    // prefs stored in the database don't make sense in this case.
    // the filename applies to a machine + user only!
    Preferences prefs = Preferences.userRoot();
    Application application = Application.getInstance();
    String applicationName = application == null ? null : application.getName();
    if (!StringHelper.isAllWhitespace(applicationName)) {
      prefs = prefs.node(applicationName);
    }
    prefs = prefs.node(prefName);
    String lastName = prefs.get(prefKey, null);

    FileChooser fc = new FileChooser();
    if (lastName != null) {
      // the preference holds an absolute path: the chooser wants the directory and the
      // plain filename separately, else the whole path ends up in the filename field
      File lastFile = new File(lastName);
      fc.setInitialFileName(lastFile.getName());
      File lastDirectory = lastFile.getParentFile();
      if (lastDirectory != null && lastDirectory.isDirectory()) {
        fc.setInitialDirectory(lastDirectory);
      }
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
        // log stacktrace only if the level is FINE
        LOGGER.fine(filePath, bx);
      }
    }
    return selectedFile;
  }

  /**
   * Gets the main stage of the current application.
   *
   * @return the main stage, null if the application is not a {@link DesktopApplication}
   */
  public Stage getMainStage() {
    Application application = Application.getInstance();
    return application instanceof DesktopApplication<?> desktopApplication ? desktopApplication.getMainStage() : null;
  }


  /**
   * Determines whether the search controller is cached.<br>
   * By default, caching is enabled unless running in a server or the {@code noRdcCache} property is set.
   *
   * @param pdo the PDO
   * @return true if cached
   * @param <T> the PDO type
   */
  protected <T extends PersistentDomainObject<T>> boolean isSearchCachingEnabled(T pdo) {
    return cachingEnabled;
  }

  /**
   * Determines whether the CRUD controller is cached.<br>
   * By default, caching is enabled unless running in a server or the {@code noRdcCache} property is set.
   *
   * @param pdo the PDO
   * @return true if cached
   * @param <T> the PDO type
   */
  protected <T extends PersistentDomainObject<T>> boolean isCrudCachingEnabled(T pdo) {
    return cachingEnabled;
  }

  /**
   * Detects when the theme has changed and invalidates the caches.
   */
  protected void detectThemeChanged() {
    boolean currentDarkMode = ThemeUtilities.getInstance().isDarkMode();
    if (currentDarkMode != darkMode) {
      darkMode = currentDarkMode;
      invalidateCaches();
    }
  }

}
