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


package org.tentackle.fx.bind;

import org.tentackle.bind.AbstractBinder;
import org.tentackle.bind.BindableElement;
import org.tentackle.bind.Binding;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Default implementation of a table binder.<br>
 * Binds the columns of a table configuration to the members of the row objects.
 * The column names are the binding paths.
 *
 * @param <S> type of the objects contained within the table's items list
 * @author harald
 */
public class DefaultFxTableBinder<S> extends AbstractBinder implements FxTableBinder<S> {

  private final TableConfiguration<S> tableConfig;           // the table configuration to bind
  private final Map<String,FxTableBinding<S,?>> boundPaths;  // the bindings mapped by binding path / column name


  /**
   * Creates a binder for a table.
   *
   * @param tableConfiguration the table configuration
   */
  public DefaultFxTableBinder(TableConfiguration<S> tableConfiguration) {
    super();
    this.tableConfig = tableConfiguration;
    boundPaths = new TreeMap<>();
  }


  @Override
  public TableConfiguration<S> getTableConfiguration() {
    return tableConfig;
  }


  @Override
  public int bindAllInherited() {
    return doBind(null, null, getTableConfiguration().getObjectClass(), false);
  }


  @Override
  public int bind() {
    return doBind(null, null, getTableConfiguration().getObjectClass(), true);
  }


  /**
   * Removes all bindings of the table.
   */
  @Override
  public void unbind() {
    boundPaths.clear();
  }


  @Override
  public Collection<FxTableBinding<S,?>> getBindings() {
    return boundPaths.values();
  }


  @Override
  public Collection<String> getBoundColumns() {
    return boundPaths.keySet();
  }


  @Override
  public Collection<String> getUnboundColumns() {
    List<String> unboundColumns = new ArrayList<>();
    for (TableColumnConfiguration<S,?> column: tableConfig.getColumnConfigurations()) {
      if (!boundPaths.containsKey(column.getName())) {
        unboundColumns.add(column.getName());
      }
    }
    return unboundColumns;
  }


  @Override
  public void assertAllBound() {
    Collection<String> unboundColumns = getUnboundColumns();
    if (!unboundColumns.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      for (String columnName: unboundColumns) {
        if (!buf.isEmpty()) {
          buf.append(", ");
        }
        buf.append(columnName);
      }
      throw new BindingException("unbound columns in " + tableConfig + ": " + buf);
    }
  }


  @Override
  @SuppressWarnings("unchecked")
  public void addBinding(Binding binding) {
    if (binding instanceof FxTableBinding) {
      FxTableBinding<S,?> oldBinding = boundPaths.put(binding.getMember().getMemberPath(), (FxTableBinding<S,?>) binding);
      if (oldBinding != null) {
        throw new BindingException(binding + ": binding path '" + binding.getMember().getMemberPath() +
                                   "' already bound to column " + oldBinding.getConfiguration().getName());
      }
    }
  }


  @Override
  public FxTableBinding<S,?> getBinding(String bindingPath) {
    return boundPaths.get(bindingPath);
  }


  @Override
  public FxTableBinding<S,?> removeBinding(String bindingPath) {
    return boundPaths.remove(bindingPath);
  }


  @Override
  protected int doBind(BindingMember[] parents, String parentMemberPath, Class<?> parentClass, boolean declaredOnly) {

    /*
     * To detect recursion loops:
     * we don't need to recursively walk down the binding path more
     * than the max binding path length of all columns of the table.
     */
    if (parentMemberPath != null) {
      boolean found = false;
      for (TableColumnConfiguration<S,?> column: tableConfig.getColumnConfigurations()) {
        // check if the binding path is leading part of any column
        String bindingPath = column.getName();
        if (bindingPath != null && bindingPath.startsWith(parentMemberPath)) {
          found = true;
          break;
        }
      }
      if (!found) {
        return 0;
      }
    }


    int count = 0;

    // all fields and/or setter/getters annotated with the @Bindable annotation
    for (BindableElement element: FxBindingFactory.getInstance().getBindableCache().getBindableMap(parentClass, declaredOnly)) {

      String fieldMemberName = StringHelper.firstToLower(element.getCamelName());
      String fieldMemberPath = (parentMemberPath == null ? "" : (parentMemberPath + ".")) + fieldMemberName;

      BindingMember[] fieldParents = new BindingMember[parents == null ? 1 : (parents.length + 1)];
      BindingMember   fieldMember  = FxBindingFactory.getInstance().createBindingMember(
                                              parentClass, (parents == null ? null : parents[parents.length-1]),
                                              fieldMemberName, fieldMemberPath, element);

      if (parents != null) {
        System.arraycopy(parents, 0, fieldParents, 0, parents.length);
        fieldParents[parents.length] = fieldMember;
      }
      else  {
        fieldParents[0] = fieldMember;
      }

      try {
        // try to bind this member
        for (TableColumnConfiguration<S,?> columnConfig: tableConfig.getColumnConfigurations()) {
          String bindingPath = columnConfig.getName();
          if (bindingPath != null && bindingPath.equals(fieldMemberPath)) {
            addBinding(FxBindingFactory.getInstance().createTableBinding(
                    this, parents, fieldMember, columnConfig, element.getBindingOptions()));
            count++;
          }
        }

        // recursively try to bind sub-members
        count += doBind(fieldParents, fieldMemberPath, fieldMember.getType(), declaredOnly);
      }
      catch (RuntimeException ex) {
        throw new BindingException("binding " + fieldMemberPath + " failed", ex);
      }
    }

    return count;
  }

}
