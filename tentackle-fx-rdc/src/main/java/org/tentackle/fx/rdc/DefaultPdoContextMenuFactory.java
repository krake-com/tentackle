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

package org.tentackle.fx.rdc;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.fx.Fx;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ClassToServicesMapper;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The default PDO context menu factory.<br>
 * Collects the menu items registered via {@link PdoTreeContextMenuItemService},
 * {@link PdoTableContextMenuItemService} or {@link PdoTreeTableContextMenuItemService}
 * for the PDO's effective class and its superclasses, sorts them by ordinal, and separates
 * the groups. Invisible items are skipped.
 *
 * @author harald
 */
@Service(PdoContextMenuFactory.class)
public class DefaultPdoContextMenuFactory implements PdoContextMenuFactory {

  /** maps PDO classes to tree cell item classes. */
  private final ClassToServicesMapper<PdoTreeContextMenuItem<?>> treeItemClassMapper;

  /** maps PDO classes to table cell item classes. */
  private final ClassToServicesMapper<PdoTableContextMenuItem<?>> tableItemClassMapper;

  /** maps PDO classes to tree table cell item classes. */
  private final ClassToServicesMapper<PdoTreeTableContextMenuItem<?>> treeTableItemClassMapper;


  /**
   * Creates the factory.
   */
  public DefaultPdoContextMenuFactory() {
    treeItemClassMapper = new ClassToServicesMapper<>("FX tree cell context menu",
            ServiceFactory.getServiceFinder().createNameMapToAll(PdoTreeContextMenuItem.class.getName()), null);
    tableItemClassMapper = new ClassToServicesMapper<>("FX table cell context menu",
            ServiceFactory.getServiceFinder().createNameMapToAll(PdoTableContextMenuItem.class.getName()), null);
    treeTableItemClassMapper = new ClassToServicesMapper<>("FX treetable cell context menu",
            ServiceFactory.getServiceFinder().createNameMapToAll(PdoTreeTableContextMenuItem.class.getName()), null);
  }


  @Override
  public <T extends PersistentDomainObject<T>> ContextMenu create(PdoTreeCell<T> cell) {
    ContextMenu contextMenu = null;
    T pdo = cell.getItem();
    if (pdo != null) {
      List<Class<? super T>> classes = new ArrayList<>();
      classes.add(pdo.getEffectiveClass());
      classes.addAll(pdo.getEffectiveSuperClasses());
      Set<ItemConfig> items = new TreeSet<>();
      try {
        for (Class<?> cls : classes) {
          Set<Class<PdoTreeContextMenuItem<?>>> itemClasses = treeItemClassMapper.mapClass(cls.getName(), true);
          for (Class<PdoTreeContextMenuItem<?>> clazz : itemClasses) {
            PdoTreeContextMenuItemService anno = clazz.getAnnotation(PdoTreeContextMenuItemService.class);
            if (anno != null) {
              MenuItem item = (MenuItem) clazz.getConstructor(PdoTreeCell.class).newInstance(cell);
              // add if not already added
              items.add(new ItemConfig(anno.ordinal(), anno.group(), item));
            }
          }
        }
        contextMenu = createMenu(items);
      }
      catch (ClassNotFoundException nf) {
        // this is ok -> no mappings found
      }
      catch (IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException cx) {
        throw new RdcRuntimeException("cannot create tree context menu item", cx);
      }
    }
    return contextMenu;
  }

  @Override
  public <S, T extends PersistentDomainObject<T>> ContextMenu create(PdoTableCell<S, T> cell) {
    ContextMenu contextMenu = null;
    T pdo = cell.getItem();
    if (pdo != null) {
      List<Class<? super T>> classes = new ArrayList<>();
      classes.add(pdo.getEffectiveClass());
      classes.addAll(pdo.getEffectiveSuperClasses());
      Set<ItemConfig> items = new TreeSet<>();
      try {
        for (Class<?> cls : classes) {
          Set<Class<PdoTableContextMenuItem<?>>> itemClasses = tableItemClassMapper.mapClass(cls.getName(), true);
          for (Class<PdoTableContextMenuItem<?>> clazz : itemClasses) {
            PdoTableContextMenuItemService anno = clazz.getAnnotation(PdoTableContextMenuItemService.class);
            if (anno != null) {
              MenuItem item = (MenuItem) clazz.getConstructor(PdoTableCell.class).newInstance(cell);
              // add if not already added
              items.add(new ItemConfig(anno.ordinal(), anno.group(), item));
            }
          }
        }
        contextMenu = createMenu(items);
      }
      catch (ClassNotFoundException nf) {
        // this is ok -> no mappings found
      }
      catch (IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException cx) {
        throw new RdcRuntimeException("cannot create table cell context menu item", cx);
      }
    }
    return contextMenu;
  }

  @Override
  public <S, T extends PersistentDomainObject<T>> ContextMenu create(PdoTreeTableCell<S, T> cell) {
    ContextMenu contextMenu = null;
    T pdo = cell.getItem();
    if (pdo != null) {
      List<Class<? super T>> classes = new ArrayList<>();
      classes.add(pdo.getEffectiveClass());
      classes.addAll(pdo.getEffectiveSuperClasses());
      Set<ItemConfig> items = new TreeSet<>();
      try {
        for (Class<?> cls : classes) {
          Set<Class<PdoTreeTableContextMenuItem<?>>> itemClasses = treeTableItemClassMapper.mapClass(cls.getName(), true);
          for (Class<PdoTreeTableContextMenuItem<?>> clazz : itemClasses) {
            PdoTreeTableContextMenuItemService anno = clazz.getAnnotation(PdoTreeTableContextMenuItemService.class);
            if (anno != null) {
              MenuItem item = (MenuItem) clazz.getConstructor(PdoTreeTableCell.class).newInstance(cell);
              // add if not already added
              items.add(new ItemConfig(anno.ordinal(), anno.group(), item));
            }
          }
        }
        contextMenu = createMenu(items);
      }
      catch (ClassNotFoundException nf) {
        // this is ok -> no mappings found
      }
      catch (IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException cx) {
        throw new RdcRuntimeException("cannot create tree table cell context menu item", cx);
      }
    }
    return contextMenu;
  }


  /**
   * Creates the context menu.
   *
   * @param items the config items
   * @return the menu
   */
  private ContextMenu createMenu(Set<ItemConfig> items) {
    if (!items.isEmpty()) {
      // create context menu
      ContextMenu menu = Fx.create(ContextMenu.class);
      String group = "";    // current group
      for (ItemConfig item : items) {
        if (item.menuItem.isVisible()) {
          if (!Objects.equals(group, item.group)) {
            menu.getItems().add(Fx.create(SeparatorMenuItem.class));
            group = item.group;
          }
          menu.getItems().add(item.menuItem);
        }
      }
      return menu;
    }
    return null;
  }


  /**
   * Item configuration.<br>
   * The configuration is unique per ordinal.
   * Since the items of the PDO's effective class are added first, this allows overriding
   * a predefined item (such as EditItem) for a certain PDO, for example.
   */
  private static class ItemConfig implements Comparable<ItemConfig> {

    private final int ordinal;
    private final String group;
    private final MenuItem menuItem;

    public ItemConfig(int ordinal, String group, MenuItem menuItem) {
      this.ordinal = ordinal;
      this.group = group;
      this.menuItem = menuItem;
    }

    @Override
    public int compareTo(ItemConfig o) {
      return Integer.compare(ordinal, o.ordinal);
    }

    @Override
    public int hashCode() {
      int hash = 3;
      hash = 29 * hash + this.ordinal;
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final ItemConfig other = (ItemConfig) obj;
      return this.ordinal == other.ordinal;
    }

  }

}
