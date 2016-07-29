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

package org.tentackle.fx.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import javafx.scene.control.TableColumn;
import org.tentackle.common.LocaleProvider;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.bind.FxBindingFactory;
import org.tentackle.fx.bind.FxTableBinder;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.reflect.ReflectionHelper;

/**
 * Default implementation of a table configuration.<br>
 *
 * @param <S> type of the objects contained within the table's items list
 * @author harald
 */
public class DefaultTableConfiguration<S> implements TableConfiguration<S> {

  /** preferences key for table width. */
  private static final String PREF_TABLE_WIDTH = "width";

  /** preferences key for table height. */
  private static final String PREF_TABLE_HEIGHT = "height";

  /** preferences key for column width. */
  private static final String PREF_COLUMN_WIDTH = "w_";

  /** preferences key for column view index. */
  private static final String PREF_COLUMN_INDEX = "i_";

  /** preferences key for column visibility. */
  private static final String PREF_COLUMN_VISIBILITY = "v_";



  /**
   * Template object.
   */
  private final S template;

  /**
   * The table's name.
   */
  private final String name;

  /**
   * the row object's class.
   */
  private final Class<S> objectClass;

  /**
   * the column configs.
   */
  private final List<TableColumnConfiguration<S, ?>> columnConfigurations;

  /**
   * The binder.
   */
  private FxTableBinder<S> binder;


  /**
   * Creates a configuration.
   *
   * @param template a template object
   * @param name the table's name, null if basename from effective class of template
   */
  @SuppressWarnings("unchecked")
  public DefaultTableConfiguration(S template, String name) {
    this.template = template;
    this.objectClass = (Class<S>) EffectiveClassProvider.getEffectiveClass(template);
    this.name = name == null ? ReflectionHelper.getClassBaseName(objectClass) : name;
    this.columnConfigurations = new ArrayList<>();
  }

  /**
   * Creates a configuration.
   *
   * @param objectClass the object class
   * @param name the table's name, null if basename from effective class of template
   */
  @SuppressWarnings("unchecked")
  public DefaultTableConfiguration(Class<S> objectClass, String name) {
    this.template = null;
    this.objectClass = objectClass;
    this.name = name == null ? ReflectionHelper.getClassBaseName(objectClass) : name;
    this.columnConfigurations = new ArrayList<>();
  }

  /**
   * Gets the template.
   *
   * @return the template, null if only class given
   */
  public S getTemplate() {
    return template;
  }


  @Override
  public String toString() {
    return name;
  }


  /**
   * Adds a column.
   *
   * @param name the column name
   * @param displayedName the displayed column name
   * @return the created configuration
   */
  @Override
  public TableColumnConfiguration<S, ?> addColumn(String name, String displayedName) {
    TableColumnConfiguration<S, ?> columnConfiguration = new DefaultTableColumnConfiguration<>(this, name, displayedName);
    addColumnConfiguration(columnConfiguration);
    return columnConfiguration;
  }

  /**
   * Adds a column configuration.
   *
   * @param columnConfiguration the config
   */
  @Override
  public void addColumnConfiguration(TableColumnConfiguration<S, ?> columnConfiguration) {
    columnConfigurations.add(columnConfiguration);
  }


  @Override
  public List<TableColumnConfiguration<S, ?>> getColumnConfigurations() {
    return columnConfigurations;
  }

  @Override
  public TableColumnConfiguration<S, ?> getColumnConfiguration(String name) {
    if (name != null) {
      for (TableColumnConfiguration<S, ?> config: columnConfigurations) {
        if (name.equals(config.getName())) {
          return config;
        }
      }
    }
    return null;
  }

  @Override
  public TableColumnConfiguration<S, ?> getColumnConfiguration(TableColumn<S, ?> column) {
    if (name != null) {
      for (TableColumnConfiguration<S, ?> config: columnConfigurations) {
        if (column == config.getTableColumn()) {
          return config;
        }
      }
    }
    return null;
  }



  @Override
  public String getName() {
    return name;
  }

  @Override
  public void savePreferences(FxTableView<S> table, String suffix, boolean system) {

    StringBuilder prefName = new StringBuilder();
    prefName.append(name);
    if (suffix != null) {
      prefName.append('_').append(suffix);
    }
    // always add the locale since header sizes etc... may vary
    prefName.append('_').append(LocaleProvider.getInstance().getEffectiveLocale());

    try {
      PersistedPreferences prefs = system ?
                                      PersistedPreferences.systemRoot().node(prefName.toString()) :
                                      PersistedPreferences.userRoot().node(prefName.toString());

      int viewIndex = 0;
      for (TableColumn<S, ?> column : table.getColumns()) {
        TableColumnConfiguration<S, ?> columnConfig = getColumnConfiguration(column);
        if (columnConfig != null) {
          PersistedPreferences columnPrefs = prefs.node(columnConfig.getName());
          columnPrefs.putInt(PREF_COLUMN_VISIBILITY, column.isVisible() ? 1 : 0);
          columnPrefs.putDouble(PREF_COLUMN_WIDTH, column.getWidth());
          columnPrefs.putInt(PREF_COLUMN_INDEX, viewIndex);
        }
        viewIndex++;
      }

      // store also the dimensions of the table
      double height = table.getHeight();
      if (table.getTotalsTableView() != null) {
        // add the height of the totals table
        height += table.getTotalsTableView().getHeight();
      }
      prefs.putDouble(PREF_TABLE_HEIGHT, height);
      prefs.putDouble(PREF_TABLE_WIDTH, table.getWidth());

      prefs.flush();
    }
    catch (BackingStoreException | RuntimeException ex) {
      throw new FxRuntimeException("saving table preferences failed for '" + prefName + "'", ex);
    }
  }

  @Override
  public void loadPreferences(FxTableView<S> table, String suffix, boolean system, boolean noViewSize) {

    StringBuilder prefName = new StringBuilder();
    prefName.append(name);
    if (suffix != null) {
      prefName.append('_').append(suffix);
    }
    // always add the locale since header sizes etc... may vary
    prefName.append('_').append(LocaleProvider.getInstance().getEffectiveLocale());

    try {
      // get the preferences
      PersistedPreferences systemPref = PersistedPreferences.systemRoot().node(prefName.toString());
      PersistedPreferences userPref = system ? null : PersistedPreferences.userRoot().node(prefName.toString());

      // map of column name to settings
      Map<String, PersistedPreferences> columnMapByName = new HashMap<>();
      for (String columnName : systemPref.childrenNames()) {
        columnMapByName.put(columnName, systemPref.node(columnName));
      }
      if (userPref != null) {
        // replace by user settings, if present
        for (String columnName : userPref.childrenNames()) {
          columnMapByName.put(columnName, userPref.node(columnName));
        }
      }

      Map<Integer, TableColumn<S, ?>> columnMapByIndex = new TreeMap<>();
      for (Map.Entry<String, PersistedPreferences> entry : columnMapByName.entrySet()) {
        TableColumnConfiguration<S, ?> columnConfig = getColumnConfiguration(entry.getKey());
        if (columnConfig != null) {
          TableColumn<S, ?> column = columnConfig.getTableColumn();
          if (column != null) {
            PersistedPreferences columnPref = entry.getValue();
            int index = columnPref.getInt(PREF_COLUMN_INDEX, -1);
            if (index >= 0) {
              columnMapByIndex.put(index, column);
            }
            int visibility = columnPref.getInt(PREF_COLUMN_VISIBILITY, -1);
            column.setVisible(visibility != 0);
            double width = columnPref.getDouble(PREF_COLUMN_WIDTH, -1.0);
            if (width >= 0.0) {
              table.resizeColumn(column, width - column.getWidth());
            }
          }
        }
      }

      table.getColumns().removeAll(columnMapByIndex.values());
      table.getColumns().addAll(columnMapByIndex.values());

      // read table dimensions
      if (!noViewSize) {
        double width = -1.0;
        String key = PREF_TABLE_WIDTH;
        if (!system) {
          width = userPref.getDouble(key, width);
        }
        if (width < 0.0) {
          width = systemPref.getDouble(key, width);
        }
        if (width > 0.0) {
          table.setPrefWidth(width);
        }

        double height = -1.0;
        key = PREF_TABLE_HEIGHT;
        if (!system) {
          height = userPref.getDouble(key, height);
        }
        if (height < 0.0) {
          height = systemPref.getDouble(key, height);
        }
        if (height > 0.0) {
          table.setPrefHeight(height);
        }
      }

    }
    catch (BackingStoreException | RuntimeException ex) {
      throw new FxRuntimeException("loading table preferences failed for '" + prefName + "'", ex);
    }
  }

  @Override
  public void configure(FxTableView<S> table) {
    table.getColumns().clear();
    for (TableColumnConfiguration<S,?> config: columnConfigurations) {
      table.getColumns().add(config.getTableColumn());
    }
    if (table instanceof FxTableView) {
      table.setConfiguration(this);
    }
  }

  @Override
  public Class<S> getObjectClass() {
    return objectClass;
  }

  @Override
  public BINDING getBindingType() {
    return BINDING.YES;
  }

  @Override
  public FxTableBinder<S> getBinder() {
    if (binder == null) {
      binder = FxBindingFactory.getInstance().createTableBinder(this);
    }
    return binder;
  }

  @Override
  public <T> TableCellType<T> getCellType(Class<T> type) {
    return TableCellTypeFactory.getInstance().getTableCellType(type);
  }

}
