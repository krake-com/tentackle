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

package org.tentackle.fx.table;

import org.tentackle.common.Service;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.reflect.ReflectionHelper;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default table cell type factory.
 *
 * @author harald
 */
@Service(TableCellTypeFactory.class)
public class DefaultTableCellTypeFactory implements TableCellTypeFactory {

  /**
   * Maps item classes to the table cell type instances.
   */
  private final ConcurrentHashMap<Class<?>, TableCellType<?>> cellTypeMap = new ConcurrentHashMap<>();

  /**
   * Maps item classes to cell type classes.
   */
  private final ClassMapper cellTypeMapper;

  /**
   * Creates the factory.
   */
  public DefaultTableCellTypeFactory() {
    cellTypeMapper = ClassMapper.create("table cell type", TableCellType.class);
  }


  @Override
  @SuppressWarnings("unchecked")
  public <T> TableCellType<T> getTableCellType(Class<T> itemClass) {
    Class<?> clazz = itemClass.isPrimitive() ? ReflectionHelper.primitiveToWrapperClass(itemClass) : itemClass;
    TableCellType<T> cellType = (TableCellType<T>) cellTypeMap.get(clazz);
    if (cellType == null) {
      // not created yet
      try {
        Class<TableCellType<T>> cellTypeClass = (Class<TableCellType<T>>) cellTypeMapper.mapLenient(clazz);
        cellType = cellTypeClass.getDeclaredConstructor().newInstance();
        cellTypeMap.put(clazz, cellType);
      }
      catch (ClassNotFoundException | InstantiationException |
             IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException ex) {
        throw new FxRuntimeException("cannot load table cell type for " + itemClass, ex);
      }
    }
    return cellType;
  }

}
