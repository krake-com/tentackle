/**
 * Tentackle - http://www.tentackle.org
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

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ClassToServicesMapper;

/**
 * The default GUI provider factory.
 *
 * @author harald
 */
@Service(PdoContextMenuFactory.class)
public class DefaultPdoContextMenuFactory implements PdoContextMenuFactory {

  /** maps PDO-classes to tree cell item classes. */
  private final ClassToServicesMapper<PdoTreeContextMenuItem<?>> treeItemClassMapper;

  /** maps PDO-classes to table cell items classes. */
  private final ClassToServicesMapper<PdoTableContextMenuItem<?,?>> tableItemClassMapper;


  /**
   * Creates the factory.
   */
  public DefaultPdoContextMenuFactory() {
    treeItemClassMapper = new ClassToServicesMapper<>("FX tree cell context menu",
            ServiceFactory.getServiceFinder().createNameMapToAll(PdoTreeContextMenuItem.class.getName()), null);
    tableItemClassMapper = new ClassToServicesMapper<>("FX table cell context menu",
            ServiceFactory.getServiceFinder().createNameMapToAll(PdoTableContextMenuItem.class.getName()), null);
  }


  @Override
  public <T extends PersistentDomainObject<T>> ContextMenu create(PdoTreeCell<T> cell) {
    Set<ItemConfig> items = new TreeSet<>();
    List<Class<? super T>> classes = cell.getItem().getEffectiveSuperClasses();
    classes.add(0, cell.getItem().getEffectiveClass());
    try {
      for (Class<?> cls: classes) {
        Set<Class<PdoTreeContextMenuItem<?>>> itemClasses = treeItemClassMapper.mapClass(cls.getName(), true);
        for (Class<PdoTreeContextMenuItem<?>> clazz: itemClasses) {
          PdoTreeContextMenuItemService anno = clazz.getAnnotation(PdoTreeContextMenuItemService.class);
          if (anno != null) {
            MenuItem item = (MenuItem) clazz.getConstructor(PdoTreeCell.class).newInstance(cell);
            // add if not already added
            items.add(new ItemConfig(anno.ordinal(), anno.separator(), item));
          }
        }
      }
    }
    catch (ClassNotFoundException |
           IllegalAccessException | InstantiationException | InvocationTargetException |
           NoSuchMethodException cx) {
      throw new RdcRuntimeException("cannot create tree context menu item", cx);
    }
    return createMenu(items);
  }

  @Override
  public <S, T extends PersistentDomainObject<T>> ContextMenu create(PdoTableCell<S, T> cell) {
    Set<ItemConfig> items = new TreeSet<>();
    List<Class<? super T>> classes = cell.getItem().getEffectiveSuperClasses();
    classes.add(0, cell.getItem().getEffectiveClass());
    try {
      for (Class<?> cls: classes) {
        Set<Class<PdoTableContextMenuItem<?,?>>> itemClasses = tableItemClassMapper.mapClass(cls.getName(), true);
        for (Class<PdoTableContextMenuItem<?,?>> clazz: itemClasses) {
          PdoTableContextMenuItemService anno = clazz.getAnnotation(PdoTableContextMenuItemService.class);
          if (anno != null) {
            // create item
            MenuItem item = (MenuItem) clazz.getConstructor(PdoTableCell.class).newInstance(cell);
            // add if not already added
            items.add(new ItemConfig(anno.ordinal(), anno.separator(), item));
          }
        }
      }
    }
    catch (ClassNotFoundException |
           IllegalAccessException | InstantiationException | InvocationTargetException |
           NoSuchMethodException cx) {
      throw new RdcRuntimeException("cannot create table context menu item", cx);
    }
    return createMenu(items);
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
      ContextMenu menu = new ContextMenu();
      for (ItemConfig item : items) {
        if (item.menuItem.isVisible()) {
          if (item.separator) {
            menu.getItems().add(new SeparatorMenuItem());
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
   * This allows to override a predefined item (such as EditItem) for a certain PDO,
   * for example.
   */
  private static class ItemConfig implements Comparable<ItemConfig> {

    private final int ordinal;
    private final boolean separator;
    private final MenuItem menuItem;

    public ItemConfig(int ordinal, boolean separator, MenuItem menuItem) {
      this.ordinal = ordinal;
      this.separator = separator;
      this.menuItem = menuItem;
    }

    @Override
    public int compareTo(ItemConfig o) {
      return ordinal - o.ordinal;
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
