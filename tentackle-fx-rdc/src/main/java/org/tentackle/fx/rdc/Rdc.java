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

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.stage.Modality;
import javafx.stage.Window;

import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.rdc.app.DesktopApplication;
import org.tentackle.fx.rdc.crud.PdoCrud;
import org.tentackle.fx.rdc.search.PdoSearch;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.function.Consumer;

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
   * Creates a tree cell for a pdo type.
   *
   * @param <T> the pdo type
   * @return the tree cell
   */
  public static <T extends PersistentDomainObject<T>> TreeCell<T> createTreeCell() {
    return RdcFactory.getInstance().createTreeCell();
  }

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
  public static <T extends PersistentDomainObject<T>> T displayCrudStage(
          T pdo, boolean editable, Modality modality, Window owner) {
    return RdcUtilities.getInstance().displayCrudStage(pdo, editable, modality, owner);
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
  public static <T extends PersistentDomainObject<T>> T displayCrudStage(
          T pdo, ObservableList<T> pdoList, boolean editable, Modality modality, Window owner, Consumer<PdoCrud<T>> configurator) {
    return RdcUtilities.getInstance().displayCrudStage(pdo, pdoList, editable, modality, owner, configurator);
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
  public static <T extends PersistentDomainObject<T>> PdoCrud<T> getCrud(
          T pdo, ObservableList<T> pdoList, boolean editable, Modality modality, Window owner) {
    return RdcUtilities.getInstance().getCrud(pdo, pdoList, editable, modality, owner);
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
  public static <T extends PersistentDomainObject<T>> ObservableList<T> displaySearchStage(
                    T pdo, Modality modality, Window owner, boolean createPdoAllowed) {
    return RdcUtilities.getInstance().displaySearchStage(pdo, modality, owner, createPdoAllowed);
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
  public static <T extends PersistentDomainObject<T>> ObservableList<T> displaySearchStage(
                    T pdo, Modality modality, Window owner, boolean createPdoAllowed, Consumer<PdoSearch<T>> configurator) {
    return RdcUtilities.getInstance().displaySearchStage(pdo, modality, owner, createPdoAllowed, configurator);
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
  public static <T extends PersistentDomainObject<T>> PdoSearch<T> getSearch(T pdo, Modality modality, Window owner) {
    return RdcUtilities.getInstance().getSearch(pdo, modality, owner);
  }

  /**
   * Shows a question dialog whether to save, discard or cancel editing of a PDO.
   *
   * @return true to save, false to discard changes, null to cancel and do nothing
   */
  public static Boolean showSaveDiscardCancelDialog() {
    return RdcUtilities.getInstance().showSaveDiscardCancelDialog();
  }

  /**
   * Closes the stage hierarchy of given node, except the main stage.
   *
   * @param node the node
   */
  public static void closeStageHierarchy(Node node) {
    FxUtilities.getInstance().closeStageHierarchy(node, DesktopApplication.getDesktopApplication().getMainStage());
  }

  private Rdc() {}
}
