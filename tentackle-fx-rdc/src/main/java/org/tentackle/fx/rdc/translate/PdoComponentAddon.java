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

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.fx.rdc.RdcUtilities;
import org.tentackle.log.Logger;
import org.tentackle.misc.IdentifiableKey;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;

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
import javafx.scene.input.TransferMode;
import javafx.stage.Modality;
import javafx.util.Callback;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Adds style, context menu, drag and drop, and function keys to components bound to a PDO.<br>
 * <ul>
 *   <li>the style class {@link #PDO_STYLE}</li>
 *   <li>F2 or the context menu's edit item opens the PDO in a modal CRUD dialog</li>
 *   <li>F3 or the context menu's search item opens a modal search dialog for the component's type</li>
 *   <li>a PDO can be dragged out of the component, and a PDO of a matching type dropped into it</li>
 * </ul>
 * Removing the style class from the component disables these features.
 * They need the {@link DomainContext} as a binding property of the binder.
 *
 * @param <T> the PDO type
 */
public class PdoComponentAddon<T extends PersistentDomainObject<T>> {

  private static final Logger LOGGER = Logger.get(PdoComponentAddon.class);


  /**
   * The PDO style.<br>
   * Remove this style to disable PDO features.
   */
  public static final String PDO_STYLE = "tt-pdo-style";


  private final FxComponent component;
  private final Supplier<T> pdoSupplier;
  private final Consumer<T> pdoConsumer;
  private boolean inSearchOrEdit;
  private T pdo;
  private Callback<T, Collection<T>> loadAllPdosCallback;

  /**
   * Creates the addon.
   *
   * @param component the FX component
   * @param pdoSupplier a supplier for the currently displayed PDO
   * @param pdoConsumer a consumer for the PDO (changed by DnD)
   */
  @SuppressWarnings("unchecked")
  public PdoComponentAddon(FxComponent component, Supplier<T> pdoSupplier, Consumer<T> pdoConsumer) {
    this.component = component;
    this.pdoSupplier = pdoSupplier;
    this.pdoConsumer = pdoConsumer;
    this.loadAllPdosCallback = this::loadAllPdos;

    Control control = (Control) component;

    // register function keys:
    // F2 = edit/view current PDO
    // F3 = invoke search
    control.addEventFilter(KeyEvent.ANY, event -> {
      if (!event.isAltDown() && !event.isControlDown() && !event.isMetaDown() &&
          !event.isShiftDown() && !event.isShortcutDown() &&
          control.getStyleClass().contains(PDO_STYLE)) {

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

    control.setOnDragDetected(event -> {
      T pdo = this.pdoSupplier.get();
      if (pdo != null && RdcUtilities.getInstance().createDragboard(control, pdo) != null) {
        event.consume();
      }
    });

    control.setOnDragOver(e -> {
      if (e.getGestureSource() != component) {
        Class<?> type = component.getType();
        if (type != null) {
          List<IdentifiableKey<PersistentDomainObject<?>>> keys = RdcUtilities.getInstance().getPdoKeysFromDragboard(e.getDragboard());
          if (!keys.isEmpty()) {
            IdentifiableKey<PersistentDomainObject<?>> key = keys.get(0);
            if (type.isAssignableFrom(key.getIdentifiableClass())) {
              e.acceptTransferModes(TransferMode.COPY);
              e.consume();
            }
          }
        }
      }
    });

    control.setOnDragDropped(e -> {
      Class<?> type = component.getType();
      boolean dropped = false;
      if (type != null) {
        FxComponentBinding binding = component.getBinding();
        if (binding != null) {
          DomainContext context = binding.getBinder().getBindingProperty(DomainContext.class);
          if (context != null) {
            PersistentDomainObject<?> pdoToDrop = RdcUtilities.getInstance().getPdoFromDragboard(e.getDragboard(), context);
            if (pdoToDrop != null && type.isAssignableFrom(pdoToDrop.getEffectiveClass())) {
              this.pdoConsumer.accept((T) pdoToDrop);
              e.setDropCompleted(true);
              dropped = true;
            }
          }
        }
      }
      if (dropped) {
        // consume only what we handled (as in the DRAG_OVER handler above):
        // a drop we did not accept must go on bubbling to a parent that may handle it
        e.consume();
      }
    });

    if (!control.getStyleClass().contains(PDO_STYLE)) {
      control.getStyleClass().add(PDO_STYLE);

      MenuItem separatorItem;
      ContextMenu menu;

      if (control.getContextMenu() == null) {
        menu = Fx.create(ContextMenu.class);
        // this replaces the default context menu in TextFields, but that's ok.
        // otherwise we would have to quirk TextFieldBehaviour which is still private API
        control.setContextMenu(menu);
        separatorItem = null;
      }
      else {
        // append to existing menu
        separatorItem = Fx.create(SeparatorMenuItem.class);
        menu = control.getContextMenu();
        menu.getItems().add(separatorItem);
      }
      MenuItem editItem = Fx.create(MenuItem.class);
      editItem.setText(RdcFxRdcBundle.getString("EDIT"));
      editItem.setAccelerator(new KeyCodeCombination(KeyCode.F2));
      editItem.setOnAction(event -> edit());
      MenuItem searchItem = Fx.create(MenuItem.class);
      searchItem.setText(RdcFxRdcBundle.getString("SEARCH"));
      searchItem.setAccelerator(new KeyCodeCombination(KeyCode.F3));
      searchItem.setOnAction(event -> search());
      menu.getItems().addAll(editItem, searchItem);

      menu.setOnShowing(event -> {
        if (control.getStyleClass().contains(PDO_STYLE)) {
          editItem.setDisable(this.pdoSupplier.get() == null);
        }
        else {
          // remove items again (application removed PDO_STYLE after creation of this component)
          menu.getItems().remove(searchItem);
          menu.getItems().remove(editItem);
          if (separatorItem != null) {
            menu.getItems().remove(separatorItem);
          }
        }
      });
    }
  }

  /**
   * Gets the FX component.
   *
   * @return the FX component
   */
  public FxComponent getComponent() {
    return component;
  }

  /**
   * Gets the supplier for the currently displayed PDO.
   *
   * @return the supplier for the currently displayed PDO
   */
  public Supplier<T> getPdoSupplier() {
    return pdoSupplier;
  }

  /**
   * Gets the consumer for the PDO.
   *
   * @return the consumer for the PDO
   */
  public Consumer<T> getPdoConsumer() {
    return pdoConsumer;
  }

  /**
   * Gets the PDO.
   *
   * @return the PDO
   */
  public T getPdo() {
    return pdo;
  }

  /**
   * Sets the PDO.
   *
   * @param pdo the PDO
   */
  public void setPdo(T pdo) {
    this.pdo = pdo;
  }

  /**
   * Returns a collection of all PDOs.<br>
   * Used in combo boxes and choice boxes to initialize the component's item list.
   *
   * @param proxy the proxy PDO
   * @return the collection of all PDOs
   */
  public Collection<T> getAllPdos(T proxy) {
    return loadAllPdosCallback.call(proxy);
  }

  /**
   * Gets the callback to load all PDOs.<br>
   * Used by {@link #getAllPdos(PersistentDomainObject)}. Defaults to {@link #loadAllPdos(PersistentDomainObject)}.
   *
   * @return the callback
   */
  public Callback<T, Collection<T>> getLoadAllPdosCallback() {
    return loadAllPdosCallback;
  }

  /**
   * Sets the callback to load all PDOs.<br>
   * Used by {@link #getAllPdos(PersistentDomainObject)}.
   *
   * @param loadAllPdosCallback the callback
   */
  public void setLoadAllPdosCallback(Callback<T, Collection<T>> loadAllPdosCallback) {
    this.loadAllPdosCallback = Objects.requireNonNull(loadAllPdosCallback);
  }

  /**
   * Returns whether the search or edit dialog is currently displayed.
   *
   * @return true if within search or edit
   */
  public boolean isInSearchOrEdit() {
    return inSearchOrEdit;
  }

  /**
   * Edits or views the current PDO.<br>
   * The PDO is reloaded and shown in a modal CRUD dialog, editable unless the PDO is immutable.
   * The component is updated with the edited PDO afterward.
   */
  public void edit() {
    if (!inSearchOrEdit) {
      pdo = pdoSupplier.get();
      if (pdo != null) {
        pdo = pdo.reload();
        if (pdo != null) {
          inSearchOrEdit = true;
          try {
            ObservableList<T> pdoList = FXCollections.observableArrayList();    // PDO list will hide new, delete and find buttons!
            pdoList.add(pdo);

            RdcUtilities.getInstance().displayCrudStage(
                pdo, pdoList, !pdo.isImmutable(), Modality.APPLICATION_MODAL, Fx.getStage((Node) component),
                p -> {
                  pdo = p;
                  inSearchOrEdit = false;
                  component.setViewValue(pdo);
                  component.updateModel();
                },
                null);
          }
          catch (RuntimeException rx) {
            inSearchOrEdit = false;
            if (rx instanceof RdcRuntimeException) {
              LOGGER.warning(rx.getMessage());    // probably no GuiProvider or no editor available -> just log
            }
            else {
              throw rx;
            }
          }
        }
      }
    }
  }

  /**
   * Searches for a PDO.<br>
   * Opens a modal search dialog and sets the selected PDO into the component, if the component is changeable.
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
      try {
        searchPdo(proxy, found -> {
          inSearchOrEdit = false;
          if (found != null && component.isChangeable()) {
            pdo = found;
            component.setViewValue(pdo);
            component.updateModel();
          }
        });
      }
      catch (RuntimeException rx) {
        inSearchOrEdit = false;
        if (rx instanceof RdcRuntimeException) {
          LOGGER.warning(rx.getMessage());    // probably no GuiProvider or no finder -> just log
        }
        else {
          throw rx;
        }
      }
    }
  }

  /**
   * Creates a new PDO of the component's type.<br>
   * Throws {@link RdcRuntimeException} if the domain context is missing.
   *
   * @return the PDO
   */
  @SuppressWarnings("unchecked")
  public T createPdo() {
    Class<T> pdoClass = (Class<T>) component.getType();
    DomainContext context = getDomainContext();
    if (context == null) {
      throw new RdcRuntimeException("missing binding property for DomainContext");
    }
    return Pdo.create(pdoClass, context);
  }


  /**
   * Gets the domain context from the binding properties.<br>
   * Taken from the binder of the component's container, table cell, or tree table cell, or from the
   * node's properties if the component is not part of a container or table.
   *
   * @return the domain context, null if not defined in properties
   */
  public DomainContext getDomainContext() {
    DomainContext context;
    if (component.getParentContainer() != null) {
      context = component.getBinding().getBinder().getBindingProperty(DomainContext.class);
    }
    else if (component.getTableCell() != null) {
      context = component.getTableCell().getColumnConfiguration().getTableConfiguration().getBinder().getBindingProperty(DomainContext.class);
    }
    else if (component.getTreeTableCell() != null) {
      context = component.getTreeTableCell().getColumnConfiguration().getTableConfiguration().getBinder().getBindingProperty(DomainContext.class);
    }
    else {
      context = (DomainContext) ((Node) component).getProperties().get(DomainContext.class);
    }
    return context;
  }


  /**
   * Modal search of the PDO.
   *
   * @param proxy the proxy PDO (possibly preset with partial search criteria)
   * @param selectedItem the consumer for the selected PDO, invoked with null if no PDO was selected
   */
  public void searchPdo(T proxy, Consumer<T> selectedItem) {
    RdcUtilities.getInstance().displaySearchStage(proxy, Modality.APPLICATION_MODAL, Fx.getStage((Node) component), true,
                                                  list -> selectedItem.accept(list.isEmpty() ? null : list.get(0)));
  }


  /**
   * Default implementation for the {@link #getLoadAllPdosCallback()}.<br>
   * Uses {@code selectAllCached()} if the entity has a preloading cache, else {@code selectAll()}
   * and logs a warning recommending a cache.
   *
   * @param proxy the PDO proxy
   * @return the collection of PDOs
   */
  protected Collection<T> loadAllPdos(T proxy) {
    PdoCache<T> cache = proxy.getCache();
    if (cache != null && cache.isPreloading()) {
      return proxy.selectAllCached();
    }
    List<T> pdos = proxy.selectAll();
    LOGGER.warning("{0} {1} retrieved by selectAll() -> consider caching with preloading!",
    pdos.size(), proxy.getEffectiveClass().getName());
    return pdos;
  }

}
