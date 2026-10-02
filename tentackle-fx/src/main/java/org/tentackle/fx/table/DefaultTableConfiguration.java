/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.common.LocaleProvider;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.bind.FxBindingFactory;
import org.tentackle.fx.bind.FxTableBinder;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.table.filter.TableColumnFilter;
import org.tentackle.fx.table.filter.TableFilter;
import org.tentackle.fx.table.filter.TableFilterSupport;
import org.tentackle.log.Logger;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.reflect.EffectiveClassProvider;

import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Default implementation of a table configuration.
 *
 * @param <S> type of the objects contained within the table's item list
 * @author harald
 */
public class DefaultTableConfiguration<S> implements TableConfiguration<S> {

  /*
   * TableView and TreeTableView don't inherit from each other, and thus
   * each spawns their own related types such as TableColumn, TreeTableColumn, etc.
   * As a result, since TableConfiguration and TableColumnConfiguration apply
   * to both tables and tree-tables, a lot of semantically identical code is necessary.
   * However, it is not really duplicate code. There's little we can do to reduce it.
   */

  /** preferences key for table and column width. */
  private static final String PREF_WIDTH = "width";

  /** preferences key for table height. */
  private static final String PREF_TABLE_HEIGHT = "height";

  /** preferences key for column view index. */
  private static final String PREF_COLUMN_INDEX = "index";

  /** preferences key for column visibility. */
  private static final String PREF_COLUMN_VISIBILITY = "visible";

  /** preferences key for the column sort type. */
  private static final String PREF_COLUMN_SORT_TYPE = "sort_type";

  /** preferences key for column sort order. */
  private static final String PREF_COLUMN_SORT_INDEX = "sort_ndx";

  /** preferences key for the column filter. */
  private static final String PREF_COLUMN_FILTER = "filter";

  private static final Logger LOGGER = Logger.get(DefaultTableConfiguration.class);


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
   * The column configs mapped by the column name.
   */
  private final Map<String, TableColumnConfiguration<S, ?>> nameColumnConfigMap = new LinkedHashMap<>();

  /**
   * The column configs mapped by table column.
   */
  private final Map<TableColumn<S, ?>, TableColumnConfiguration<S, ?>> tableColumnConfigMap = new LinkedHashMap<>();

  /**
   * The column configs mapped by treetable column.
   */
  private final Map<TreeTableColumn<S, ?>, TableColumnConfiguration<S, ?>> treeTableColumnConfigMap = new LinkedHashMap<>();

  /**
   * The binder.
   */
  private FxTableBinder<S> binder;

  /**
   * Whether preferences include sorting configuration.
   */
  private boolean sortingIncluded;

  /**
   * Whether preferences include the view size.
   */
  private boolean viewSizeIncluded = true;

  /**
   * Whether the columns provide a filter UI in their headers.
   */
  private boolean filterEnabled;

  /**
   * Whether preferences include the column filters.
   */
  private boolean filterIncluded;

  /**
   * The edit mode.
   */
  private EDITMODE editMode = EDITMODE.NO;

  /**
   * The binding type.
   */
  private BINDING bindingType = BINDING.YES;

  /**
   * Optional resource bundle name for BundleMonkey navigation.
   */
  private String bundleName;


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
    this.name = name == null ? objectClass.getSimpleName() : name;
  }

  /**
   * Creates a configuration.
   *
   * @param objectClass the object class
   * @param name the table's name, null if basename of the object class
   */
  public DefaultTableConfiguration(Class<S> objectClass, String name) {
    this.template = null;
    this.objectClass = objectClass;
    this.name = name == null ? objectClass.getSimpleName() : name;
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


  @Override
  public TableColumnConfiguration<S, ?> addColumn(String name, String displayedName) {
    TableColumnConfiguration<S, ?> columnConfiguration = createTableColumnConfiguration(name, displayedName);
    addColumnConfiguration(columnConfiguration);
    return columnConfiguration;
  }

  @Override
  public TableColumnConfiguration<S, ?> removeColumn(String name) {
    TableColumnConfiguration<S, ?> columnConfiguration = nameColumnConfigMap.remove(name);
    if (columnConfiguration != null) {
      tableColumnConfigMap.remove(columnConfiguration.getTableColumn());
      treeTableColumnConfigMap.remove(columnConfiguration.getTreeTableColumn());
    }
    return columnConfiguration;
  }

  @Override
  public void addColumnConfiguration(TableColumnConfiguration<S, ?> columnConfiguration) {
    nameColumnConfigMap.put(columnConfiguration.getName(), columnConfiguration);
    tableColumnConfigMap.put(columnConfiguration.getTableColumn(), columnConfiguration);
    treeTableColumnConfigMap.put(columnConfiguration.getTreeTableColumn(), columnConfiguration);
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
    return column == null ? null : tableColumnConfigMap.get(column);
  }

  @Override
  public TableColumnConfiguration<S, ?> getColumnConfiguration(TreeTableColumn<S, ?> column) {
    return column == null ? null : treeTableColumnConfigMap.get(column);
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
  public boolean isViewSizeIncluded() {
    return viewSizeIncluded;
  }

  @Override
  public void setViewSizeIncluded(boolean viewSizeIncluded) {
    this.viewSizeIncluded = viewSizeIncluded;
  }

  @Override
  public boolean isFilterEnabled() {
    return filterEnabled;
  }

  @Override
  public void setFilterEnabled(boolean filterEnabled) {
    this.filterEnabled = filterEnabled;
  }

  @Override
  public boolean isFilterIncluded() {
    return filterIncluded;
  }

  @Override
  public void setFilterIncluded(boolean filterIncluded) {
    this.filterIncluded = filterIncluded;
  }

  @Override
  public void savePreferences(FxTableView<S> table, String suffix, boolean system) {

    StringBuilder prefName = new StringBuilder();
    prefName.append(name);
    if (suffix != null) {
      prefName.append('_').append(suffix);
    }
    // always add the locale since header sizes etc. may vary
    prefName.append('_').append(LocaleProvider.getInstance().getEffectiveLocale());

    ObservableList<TableColumn<S,?>> sortOrder = table.getSortOrder();

    try {
      PersistedPreferences prefs = system ?
                                      PersistedPreferences.systemRoot().node(prefName.toString()) :
                                      PersistedPreferences.userRoot().node(prefName.toString());

      TableFilter<S> filter = table.getFilter();
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
          saveColumnFilter(columnPrefs, filter, columnConfig);
        }
        viewIndex++;
      }

      // store also the dimensions of the table
      double height = table.getHeight();
      TotalsTableView<S> totalsTable = TotalsTableView.getTotalsTableView(table);
      if (totalsTable != null) {
        // add the height of the "totals" table
        height += totalsTable.getHeight();
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
  public void loadPreferences(FxTableView<S> table, String suffix, boolean system) {

    StringBuilder prefName = new StringBuilder();
    prefName.append(name);
    if (suffix != null) {
      prefName.append('_').append(suffix);
    }
    // always add the locale since header sizes etc. may vary
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
        // replace preference by user settings, if present
        for (String columnName : userPref.childrenNames()) {
          columnMapByName.put(columnName, userPref.node(columnName));
        }
      }

      TableFilter<S> filter = table.getFilter();
      Map<Integer, TableColumn<S, ?>> columnMapByIndex = new TreeMap<>();
      for (Map.Entry<String, PersistedPreferences> entry : columnMapByName.entrySet()) {
        TableColumnConfiguration<S, ?> columnConfig = getColumnConfiguration(entry.getKey());
        if (columnConfig != null) {
          TableColumn<S, ?> column = columnConfig.getTableColumn();
          if (column != null) {
            PersistedPreferences columnPref = entry.getValue();
            loadColumnFilter(columnPref, filter, columnConfig);
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

      if (table.getFilterSupport() != null) {
        table.getFilterSupport().refilter();
      }

      // read table dimensions
      if (isViewSizeIncluded()) {
        double width = -1.0;
        String key = PREF_WIDTH;
        if (!system && userPref != null) {
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
        if (!system && userPref != null) {
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
  public void savePreferences(FxTreeTableView<S> treeTable, String suffix, boolean system) {

    StringBuilder prefName = new StringBuilder();
    prefName.append(name);
    if (suffix != null) {
      prefName.append('_').append(suffix);
    }
    // always add the locale since header sizes etc. may vary
    prefName.append('_').append(LocaleProvider.getInstance().getEffectiveLocale());

    ObservableList<TreeTableColumn<S,?>> sortOrder = treeTable.getSortOrder();

    try {
      PersistedPreferences prefs = system ?
                                   PersistedPreferences.systemRoot().node(prefName.toString()) :
                                   PersistedPreferences.userRoot().node(prefName.toString());

      TableFilter<S> filter = treeTable.getFilter();
      int viewIndex = 0;
      for (TreeTableColumn<S, ?> column : treeTable.getColumns()) {
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
          saveColumnFilter(columnPrefs, filter, columnConfig);
        }
        viewIndex++;
      }

      // store also the dimensions of the table
      double height = treeTable.getHeight();
      prefs.putDouble(PREF_TABLE_HEIGHT, height);
      prefs.putDouble(PREF_WIDTH, treeTable.getWidth());
      prefs.flush();
    }
    catch (BackingStoreException | RuntimeException ex) {
      throw new FxRuntimeException("saving table preferences failed for '" + prefName + "'", ex);
    }
  }

  @Override
  public void loadPreferences(FxTreeTableView<S> treeTable, String suffix, boolean system) {

    StringBuilder prefName = new StringBuilder();
    prefName.append(name);
    if (suffix != null) {
      prefName.append('_').append(suffix);
    }
    // always add the locale since header sizes etc. may vary
    prefName.append('_').append(LocaleProvider.getInstance().getEffectiveLocale());

    Map<Integer, TreeTableColumn<S,?>> sortOrder = new TreeMap<>();   // sorted by sort index

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
        // replace preference by user settings, if present
        for (String columnName : userPref.childrenNames()) {
          columnMapByName.put(columnName, userPref.node(columnName));
        }
      }

      TableFilter<S> filter = treeTable.getFilter();
      Map<Integer, TreeTableColumn<S, ?>> columnMapByIndex = new TreeMap<>();
      for (Map.Entry<String, PersistedPreferences> entry : columnMapByName.entrySet()) {
        TableColumnConfiguration<S, ?> columnConfig = getColumnConfiguration(entry.getKey());
        if (columnConfig != null) {
          TreeTableColumn<S, ?> column = columnConfig.getTreeTableColumn();
          if (column != null) {
            PersistedPreferences columnPref = entry.getValue();
            loadColumnFilter(columnPref, filter, columnConfig);
            int visibility = columnPref.getInt(PREF_COLUMN_VISIBILITY, -1);
            column.setVisible(visibility != 0);
            double width = columnPref.getDouble(PREF_WIDTH, -1.0);
            if (width >= 0.0) {
              treeTable.resizeColumn(column, width - column.getWidth());
            }
            int index = columnPref.getInt(PREF_COLUMN_INDEX, -1);
            if (index >= 0) {
              columnMapByIndex.put(index, column);
              if (isSortingIncluded()) {
                int sorting = columnPref.getInt(PREF_COLUMN_SORT_TYPE, -1);
                if (sorting >= 0) {
                  column.setSortType(TreeTableColumn.SortType.values()[sorting]);
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

      treeTable.getColumns().removeAll(columnMapByIndex.values());
      treeTable.getColumns().addAll(columnMapByIndex.values());
      treeTable.getSortOrder().addAll(sortOrder.values());

      if (treeTable.getFilterSupport() != null) {
        treeTable.getFilterSupport().refilter();
      }

      // read table dimensions
      if (isViewSizeIncluded()) {
        double width = -1.0;
        String key = PREF_WIDTH;
        if (!system && userPref != null) {
          width = userPref.getDouble(key, width);
        }
        if (width < 0.0) {
          width = systemPref.getDouble(key, width);
        }
        if (width > 0.0) {
          treeTable.setPrefWidth(width);
        }

        double height = -1.0;
        key = PREF_TABLE_HEIGHT;
        if (!system && userPref != null) {
          height = userPref.getDouble(key, height);
        }
        if (height < 0.0) {
          height = systemPref.getDouble(key, height);
        }
        if (height > 0.0) {
          treeTable.setPrefHeight(height);
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
  public String getBaseBundleName() {
    return bundleName;
  }

  @Override
  public void setBaseBundleName(String bundleName) {
    this.bundleName = bundleName;
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
    if (isFilterEnabled()) {
      installFilterSupport(table);
    }
    else {
      table.setFilterSupport(null);   // re-configured without filter: drop the old support
    }
  }

  @Override
  public void configure(FxTreeTableView<S> treeTable) {
    treeTable.getColumns().clear();
    boolean tableEditable = false;
    for (TableColumnConfiguration<S,?> config: nameColumnConfigMap.values()) {
      boolean columnEditable = config.isEditable();
      tableEditable |= columnEditable;
      TreeTableColumn<S,?> column = config.getTreeTableColumn();
      column.setEditable(columnEditable);
      treeTable.getColumns().add(column);
    }
    treeTable.setEditable(tableEditable);
    treeTable.getSelectionModel().cellSelectionEnabledProperty().set(tableEditable);
    treeTable.setConfiguration(this);
    if (isFilterEnabled()) {
      installFilterSupport(treeTable);
    }
    else {
      treeTable.setFilterSupport(null);   // re-configured without filter: drop the old support
    }
  }

  @Override
  public Class<S> getObjectClass() {
    return objectClass;
  }

  @Override
  public BINDING getBindingType() {
    return bindingType;
  }

  @Override
  public void setBindingType(BINDING bindingType) {
    this.bindingType = bindingType;
  }

  @Override
  public FxTableBinder<S> getBinder() {
    if (binder == null) {
      binder = FxBindingFactory.getInstance().createTableBinder(this);
    }
    return binder;
  }

  @Override
  public <T> TableCellType<T> getTableCellType(Class<T> type) {
    return TableCellTypeFactory.getInstance().getTableCellType(type);
  }

  @Override
  public TreeItem<S> createTreeItem(S object) {
    return new TreeItem<>(object);
  }


  /**
   * Saves the filter of a column to the preferences.<br>
   * The filter is removed from the preferences if not {@link #isFilterIncluded() included} or inactive.
   *
   * @param columnPrefs the column's preference node
   * @param filter the table filter, null if the view provides no filter
   * @param columnConfig the column configuration
   */
  protected void saveColumnFilter(PersistedPreferences columnPrefs, TableFilter<S> filter,
                                  TableColumnConfiguration<S, ?> columnConfig) {
    TableColumnFilter<S, ?> columnFilter = filter == null ? null : filter.getColumnFilter(columnConfig);
    if (isFilterIncluded() && columnFilter != null && columnFilter.isActive()) {
      String prefValue = columnFilter.toPreferencesString();
      if (prefValue.length() <= Preferences.MAX_VALUE_LENGTH) {
        columnPrefs.put(PREF_COLUMN_FILTER, prefValue);
        return;
      }
      LOGGER.warning("filter of column {0} is too large for the preferences and will not be saved",
                     columnConfig.getName());
    }
    columnPrefs.remove(PREF_COLUMN_FILTER);
  }

  /**
   * Loads the filter of a column from the preferences.
   *
   * @param columnPrefs the column's preference node
   * @param filter the table filter, null if the view provides no filter
   * @param columnConfig the column configuration
   */
  protected void loadColumnFilter(PersistedPreferences columnPrefs, TableFilter<S> filter,
                                  TableColumnConfiguration<S, ?> columnConfig) {
    if (isFilterIncluded() && filter != null) {
      filter.getColumnFilter(columnConfig).fromPreferencesString(columnPrefs.get(PREF_COLUMN_FILTER, null));
    }
  }

  @Override
  public TableFilterSupport<S> installFilterSupport(FxTableView<S> table) {
    table.setFilterSupport(null);   // uninstall any previous support
    TableFilterSupport<S> support = createFilterSupport(table);
    table.setFilterSupport(support);
    support.install();
    return support;
  }

  @Override
  public TableFilterSupport<S> installFilterSupport(FxTreeTableView<S> treeTable) {
    treeTable.setFilterSupport(null);   // uninstall any previous support
    TableFilterSupport<S> support = createFilterSupport(treeTable);
    treeTable.setFilterSupport(support);
    support.install();
    return support;
  }

  /**
   * Creates the filter support for a table view.
   *
   * @param table the table view
   * @return the filter support
   */
  protected TableFilterSupport<S> createFilterSupport(FxTableView<S> table) {
    return new TableFilterSupport<>(table);
  }

  /**
   * Creates the filter support for a treetable view.
   *
   * @param treeTable the treetable view
   * @return the filter support
   */
  protected TableFilterSupport<S> createFilterSupport(FxTreeTableView<S> treeTable) {
    return new TableFilterSupport<>(treeTable);
  }

  /**
   * Creates a column configuration.
   *
   * @param name the column's binding path
   * @param displayedName the displayed column name
   * @return the created column configuration
   */
  protected TableColumnConfiguration<S, ?> createTableColumnConfiguration(String name, String displayedName) {
    return new DefaultTableColumnConfiguration<>(this, name, displayedName);
  }

}
