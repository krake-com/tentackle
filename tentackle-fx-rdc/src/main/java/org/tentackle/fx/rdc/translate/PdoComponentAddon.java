/*
 * Tentackle - https://tentackle.org
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

package org.tentackle.fx.rdc.translate;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.rdc.RdcUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Adds style, context-menu and function-keys to components bound to a PDO.
 *
 * @param <T> the PDO type
 */
public class PdoComponentAddon<T extends PersistentDomainObject<T>> {

  private static final String PDO_STYLE = "tt-pdo-style";

  private final FxComponent component;
  private final Supplier<T> pdoSupplier;
  private boolean inSearchOrEdit;
  private T pdo;

  /**
   * Creates the addon.
   *
   * @param component the fx component
   * @param pdoSupplier a supplier for the currently displayed PDO
   */
  public PdoComponentAddon(FxComponent component, Supplier<T> pdoSupplier) {
    this.component = component;
    this.pdoSupplier = pdoSupplier;

    // register function keys:
    // F2 = edit/view current PDO
    // F3 = invoke search
    Control control = (Control) component;
    control.addEventFilter(KeyEvent.ANY, event -> {
      if (!event.isAltDown() && !event.isControlDown() && !event.isMetaDown() &&
          !event.isShiftDown() && !event.isShortcutDown()) {

        if (event.getCode() == KeyCode.F2) {
          event.consume();
          if (event.getEventType() == KeyEvent.KEY_PRESSED) {
            Platform.runLater(this::edit);
          }
        }
        else if (event.getCode() == KeyCode.F3) {
          event.consume();
          if (event.getEventType() == KeyEvent.KEY_PRESSED) {
            Platform.runLater(this::search);
          }
        }
      }
    });

    if (!control.getStyleClass().contains(PDO_STYLE)) {
      control.getStyleClass().add(PDO_STYLE);

      ContextMenu menu = control.getContextMenu();
      if (menu == null) {
        menu = new ContextMenu();
        // this replaces the default context menu in TextFields, but that's ok.
        // otherwise we would have to quirk TextFieldBehaviour which is still private API
        control.setContextMenu(menu);
      }
      else {
        // append to existing menu
        menu.getItems().add(new SeparatorMenuItem());
      }
      MenuItem editItem = new MenuItem(RdcFxRdcBundle.getString("EDIT"));
      editItem.setAccelerator(new KeyCodeCombination(KeyCode.F2));
      editItem.setOnAction(event -> edit());
      menu.setOnShowing(event -> editItem.setDisable(pdoSupplier.get() == null));
      MenuItem searchItem = new MenuItem(RdcFxRdcBundle.getString("SEARCH"));
      searchItem.setAccelerator(new KeyCodeCombination(KeyCode.F3));
      searchItem.setOnAction(event -> search());
      menu.getItems().addAll(editItem, searchItem);
    }
  }

  /**
   * Gets the PDO.<br>
   *
   * @return the pdo
   */
  public T getPdo() {
    return pdo;
  }

  /**
   * Sets the PDO.<br>
   *
   * @param pdo the pdo
   */
  public void setPdo(T pdo) {
    this.pdo = pdo;
  }

  /**
   * Returns whether search or edit dialog is currently displayed.
   *
   * @return true if within search or edit
   */
  public boolean isInSearchOrEdit() {
    return inSearchOrEdit;
  }

  /**
   * Edit or view current pdo.
   */
  public void edit() {
    if (!inSearchOrEdit) {
      pdo = pdoSupplier.get();
      if (pdo != null) {
        inSearchOrEdit = true;
        ObservableList<T> pdoList = FXCollections.observableArrayList();    // pdo list will disable new,delete and find buttons!
        pdoList.add(pdo);
        RdcUtilities.getInstance().displayCrudStage(
            pdo, pdoList, !pdo.isImmutable(), Modality.APPLICATION_MODAL, Fx.getStage((Node) component),
            p -> {
              pdo = p;
              inSearchOrEdit = false;
            },
            null);
      }
    }
  }

  /**
   * Search for a PDO.
   */
  public void search() {
    if (!inSearchOrEdit) {
      T proxy;
      try {
        proxy = createPdo();    // before setting inSearchOrEdit in case T is not mapped to an entity
      }
      catch (PdoRuntimeException px) {
        if (pdo == null) {
          return;   // do nothing: cannot search unmapped type such as PersistentDomainObject.class itself
        }
        // try the supplier's type
        proxy = pdo.on();
      }
      inSearchOrEdit = true;
      searchPdo(proxy, found -> {
        if (found != null && component.isChangeable()) {
          pdo = found;
          component.setViewValue(pdo);
          component.updateModel();
        }
        inSearchOrEdit = false;
      });
    }
  }

  /**
   * Creates a new PDO.
   *
   * @return the pdo
   */
  @SuppressWarnings("unchecked")
  public T createPdo() {
    Class<T> pdoClass = (Class<T>) component.getType();
    DomainContext context;
    if (component.getParentContainer() != null) {
      context = component.getBinding().getBinder().getBindingProperty(DomainContext.class);
    }
    else if (component.getTableCell() != null) {
      context = component.getTableCell().getColumnConfiguration().getTableConfiguration().getBinder().getBindingProperty(DomainContext.class);
    }
    else {
      context = (DomainContext) ((Node) component).getProperties().get(DomainContext.class);
    }
    if (context == null) {
      throw new FxRuntimeException("missing binding property for DomainContext");
    }
    return Pdo.create(pdoClass, context);
  }


  /**
   * Modal search of the pdo.
   *
   * @param proxy the proxy pdo (possibly presetted with partial search criteria)
   * @param selectedItem the consumer for the selected pdo, invoked with null if no pdo found
   */
  public void searchPdo(T proxy, Consumer<T> selectedItem) {
    RdcUtilities.getInstance().displaySearchStage(proxy, Modality.APPLICATION_MODAL, Fx.getStage((Node) component), true, list -> {
      selectedItem.accept(list.isEmpty() ? null : list.get(0));
    });
  }

  /**
   * Returns a list of all pdos.<br>
   * Used in comboboxes and choiceboxes to initialize the component's item list.
   *
   * @param proxy the proxy pdo
   * @return the list of all PDOs
   */
  public List<T> selectAll(T proxy) {
    return proxy.selectAllCached();
  }

}
