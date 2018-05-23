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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import org.tentackle.common.LocaleProvider;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.bind.FxBindingFactory;
import org.tentackle.fx.bind.FxTableBinder;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.log.Logger;
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

  private static final Logger LOGGER = Logger.get(DefaultTableConfiguration.class);

  /** preferences key for table and column width. */
  private static final String PREF_WIDTH = "width";

  /** preferences key for table height. */
  private static final String PREF_TABLE_HEIGHT = "height";

  /** preferences key for column view index. */
  private static final String PREF_COLUMN_INDEX = "index";

  /** preferences key for column visibility. */
  private static final String PREF_COLUMN_VISIBILITY = "visible";

  /** preferences key for column sort type. */
  private static final String PREF_COLUMN_SORT_TYPE = "sort_type";

  /** preferences key for column sort order. */
  private static final String PREF_COLUMN_SORT_INDEX = "sort_ndx";



  /**
   * Template object.
   */
  private final S template;

  /**
   * The table's name.
   */
  private final String name;

  /**
   * The row object's class.
   */
  private final Class<S> objectClass;

  /**
   * The column configs mapped by column name.
   */
  private final Map<String, TableColumnConfiguration<S, ?>> nameColumnConfigMap;

  /**
   * The column configs mapped by table column.
   */
  private final Map<TableColumn<S, ?>, TableColumnConfiguration<S, ?>> columnColumnConfigMap;

  /**
   * The binder.
   */
  private FxTableBinder<S> binder;

  /**
   * Whether preferences include sorting configuration.
   */
  private boolean sortingIncluded;

  /**
   * The edit mode.
   */
  private EDITMODE editMode = EDITMODE.NO;


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
    this.nameColumnConfigMap = new LinkedHashMap<>();
    this.columnColumnConfigMap = new LinkedHashMap<>();
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
    this.nameColumnConfigMap = new LinkedHashMap<>();
    this.columnColumnConfigMap = new LinkedHashMap<>();
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

  @Override
  public void addColumnConfiguration(TableColumnConfiguration<S, ?> columnConfiguration) {
    nameColumnConfigMap.put(columnConfiguration.getName(), columnConfiguration);
    columnColumnConfigMap.put(columnConfiguration.getTableColumn(), columnConfiguration);
  }

  @Override
  public Collection<TableColumnConfiguration<S, ?>> getColumnConfigurations() {
    return nameColumnConfigMap.values();
  }

  @Override
  public TableColumnConfiguration<S, ?> getColumnConfiguration(String name) {
    return name == null ? null : nameColumnConfigMap.get(name);
  }

  @Override
  public TableColumnConfiguration<S, ?> getColumnConfiguration(TableColumn<S, ?> column) {
    return column == null ? null : columnColumnConfigMap.get(column);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isSortingIncluded() {
    return sortingIncluded;
  }

  @Override
  public void setSortingIncluded(boolean sortingIncluded) {
    this.sortingIncluded = sortingIncluded;
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

    ObservableList<TableColumn<S,?>> sortOrder = table.getSortOrder();

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
          columnPrefs.putDouble(PREF_WIDTH, column.getWidth());
          columnPrefs.putInt(PREF_COLUMN_INDEX, viewIndex);
          int sortIndex = sortOrder.indexOf(column);
          if (isSortingIncluded() && sortIndex >= 0) {
            columnPrefs.putInt(PREF_COLUMN_SORT_TYPE, column.getSortType().ordinal());
            columnPrefs.putInt(PREF_COLUMN_SORT_INDEX, sortIndex);
          }
          else {
            columnPrefs.remove(PREF_COLUMN_SORT_TYPE);
            columnPrefs.remove(PREF_COLUMN_SORT_INDEX);
          }
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
      prefs.putDouble(PREF_WIDTH, table.getWidth());
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

    Map<Integer, TableColumn<S,?>> sortOrder = new TreeMap<>();   // sorted by sort index

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
            int visibility = columnPref.getInt(PREF_COLUMN_VISIBILITY, -1);
            column.setVisible(visibility != 0);
            double width = columnPref.getDouble(PREF_WIDTH, -1.0);
            if (width >= 0.0) {
              table.resizeColumn(column, width - column.getWidth());
            }
            int index = columnPref.getInt(PREF_COLUMN_INDEX, -1);
            if (index >= 0) {
              columnMapByIndex.put(index, column);
              if (isSortingIncluded()) {
                int sorting = columnPref.getInt(PREF_COLUMN_SORT_TYPE, -1);
                if (sorting >= 0) {
                  column.setSortType(TableColumn.SortType.values()[sorting]);
                  int sortIndex = columnPref.getInt(PREF_COLUMN_SORT_INDEX, -1);
                  if (sortIndex == -1) {
                    sortIndex = Integer.MAX_VALUE - sortOrder.size();
                  }
                  sortOrder.put(sortIndex, column);
                }
              }
            }
          }
        }
      }

      table.getColumns().removeAll(columnMapByIndex.values());
      table.getColumns().addAll(columnMapByIndex.values());
      table.getSortOrder().addAll(sortOrder.values());

      // read table dimensions
      if (!noViewSize) {
        double width = -1.0;
        String key = PREF_WIDTH;
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
  public EDITMODE getEditMode() {
    return editMode;
  }

  @Override
  public void setEditMode(EDITMODE editMode) {
    this.editMode = editMode == null ? EDITMODE.NO : editMode;
  }

  @Override
  public void configure(FxTableView<S> table) {
    table.getColumns().clear();
    boolean tableEditable = false;
    for (TableColumnConfiguration<S,?> config: nameColumnConfigMap.values()) {
      boolean columnEditable = config.isEditable();
      tableEditable |= columnEditable;
      TableColumn<S,?> column = config.getTableColumn();
      column.setEditable(columnEditable);
      table.getColumns().add(column);
    }
    table.setEditable(tableEditable);
    table.getSelectionModel().cellSelectionEnabledProperty().set(tableEditable);
    table.setConfiguration(this);
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
