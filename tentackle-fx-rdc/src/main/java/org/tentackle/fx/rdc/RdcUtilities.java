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

package org.tentackle.fx.rdc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.rdc.crud.PdoCrud;
import org.tentackle.fx.rdc.search.PdoSearch;
import org.tentackle.pdo.PersistentDomainObject;

interface RdcUtilities$Singleton {
  RdcUtilities INSTANCE = ServiceFactory.createService(RdcUtilities.class);
}

/**
 * RDC-related utility methods.
 *
 * @author harald
 */
@Service(RdcUtilities.class)    // defaults to self
public class RdcUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static RdcUtilities getInstance() {
    return RdcUtilities$Singleton.INSTANCE;
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
   * CRUD of a PDO in a separate window.
   *
   * @param <T> the pdo type
   * @param pdo the pdo
   * @param editable true if user may edit the pdo, false if to view only
   * @param modality the modality
   * @param owner the owner, null if none
   * @return the possibly changed pdo if modal
   */
  public <T extends PersistentDomainObject<T>> T displayCrudStage(
          T pdo, boolean editable, Modality modality, Window owner) {
    return displayCrudStage(pdo, null, editable, modality, owner, null);
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
   * @param configurator optional crud configurator
   * @return the possibly changed pdo if modal
   */
  public <T extends PersistentDomainObject<T>> T displayCrudStage(
          T pdo, ObservableList<T> pdoList, boolean editable, Modality modality, Window owner,
          Consumer<PdoCrud<T>> configurator) {

    PdoCrud<T> crud = getCrud(pdo, pdoList, editable, modality, owner);
    if (crud != null) {   // if not aleady being edited
      Stage stage = crud.getStage();
      if (stage == null && Fx.getStage(crud.getView()) == null) {
        // not bound to its own stage so far and not part of some other stage: create new stage
        stage = Fx.createStage(modality);
        if (owner != null) {
          stage.initOwner(owner);
        }
        Scene scene = new Scene(crud.getView());
        stage.setScene(scene);
        crud.updateTitle();
        stage.addEventFilter(WindowEvent.WINDOW_SHOWN,
                e -> Platform.runLater(() -> crud.getEditor().requestInitialFocus()));
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, e -> {
          if (!crud.releasePdo()) {
            e.consume();    // don't close
          }
        });
        stage.addEventFilter(WindowEvent.WINDOW_HIDDEN, e -> {
          // clear all references to avoid memleaks and side effects
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

      if (modality != Modality.NONE) {
        stage.showAndWait();
        pdo = crud.getPdo();
      }
      else {
        stage.show();
      }
    }
    return pdo;
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
    Set<PdoCrud<?>> cruds = crudCache.get(pdoClass);
    if (cruds == null) {
      cruds = new HashSet<>();
      crudCache.put(pdoClass, cruds);
    }

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
      if (Objects.equals(pdo, crud.getPdo()) && stage.isShowing()) {
        // same PDO
        if (crud.isEditing() || !editable) {
          // already being edited or just shown and view-only requested
          stage.toFront();
          return null;
        }
        // else: being edited, being shown or requested editable does not match: get a fresh one
      }
      if (!stage.isShowing() && stage.getModality() == modality && stage.getOwner() != owner &&
          stage.getOwner() != null && !stage.getOwner().isShowing()) {
        // just the wrong owner, which isnt showing:
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
   * @return the selected PDOs if modal, null if not modal
   */
  public <T extends PersistentDomainObject<T>> ObservableList<T> displaySearchStage(
                    T pdo, Modality modality, Window owner, boolean createPdoAllowed) {
    return displaySearchStage(pdo, modality, owner, createPdoAllowed, null);
  }


  /**
   * Searches for PDOs in a separate window.
   *
   * @param <T> the pdo type
   * @param pdo the pdo as a template
   * @param modality the modality
   * @param owner the owner, null if none
   * @param createPdoAllowed true if allow to create a new PDO from within the search dialog
   * @param configurator the optional configurator for the PdoSearch
   * @return the selected PDOs if modal, null if not modal
   */
  public <T extends PersistentDomainObject<T>> ObservableList<T> displaySearchStage(
                    T pdo, Modality modality, Window owner, boolean createPdoAllowed,
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
      Scene scene = new Scene(search.getView());
      stage.setScene(scene);
      search.updateTitle();
      search.setSingleSelectMode(Fx.isModal(stage));
      stage.addEventFilter(WindowEvent.WINDOW_SHOWN,
              e -> Platform.runLater(() -> search.getFinder().requestInitialFocus()));
      stage.addEventFilter(WindowEvent.WINDOW_HIDDEN,
              // runlater to avoid clearing the modal return value
              e -> Platform.runLater(() -> search.setItems(null)));
    } // else: re-use stage

    if (configurator != null) {
      configurator.accept(search);
    }

    if (stage == null) {
      throw new FxRuntimeException("misconfigured search stage");
    }

    if (modality != Modality.NONE) {
      stage.showAndWait();
      return search.getSelectedItems();
    }
    else {
      stage.show();
      return null;
    }
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
    Set<PdoSearch<?>> searches = searchCache.get(pdoClass);
    if (searches == null) {
      searches = new HashSet<>();
      searchCache.put(pdoClass, searches);
    }

    for (Iterator<PdoSearch<?>> iter = searches.iterator(); iter.hasNext(); ) {
      PdoSearch<T> search = (PdoSearch<T>) iter.next();
      Stage stage = Fx.getStage(search.getView());
      if (stage == null || (!stage.isShowing() && stage.getModality() == modality && stage.getOwner() == owner)) {
        search.setItems(null);
        search.setPdo(pdo);
        search.showTable();
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
   * @return true to save, false to discard changes, null to cancel and do nothing
   */
  public Boolean showSaveDiscardCancelDialog() {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(FxFxBundle.getString("QUESTION"));
    alert.setHeaderText(null);
    alert.setContentText(RdcFxRdcBundle.getString("DATA_HAS_BEEN_MODIFIED!_DISCARD,_SAVE_OR_CANCEL?"));
    ButtonType saveButtonType = new ButtonType(RdcFxRdcBundle.getString("SAVE"));
    ButtonType discardButtonType = new ButtonType(RdcFxRdcBundle.getString("DISCARD"));
    ButtonType cancelButtonType = new ButtonType(RdcFxRdcBundle.getString("CANCEL"), ButtonData.CANCEL_CLOSE);
    alert.getButtonTypes().setAll(saveButtonType, discardButtonType, cancelButtonType);
    Button cancelButton = (Button) alert.getDialogPane().lookupButton(cancelButtonType);
    cancelButton.setDefaultButton(true);
    Optional<ButtonType> result = alert.showAndWait();
    if (result.get() == saveButtonType) {
      return Boolean.TRUE;
    }
    if (result.get() == discardButtonType) {
      return Boolean.FALSE;
    }
    return null;
  }

}
