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

package org.tentackle.fx.table.filter;

import org.tentackle.common.LocaleProvider;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;

import javafx.beans.InvalidationListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Adds the column filter UI to a table or treetable view.<p>
 * The support attaches a filter button to the header of each
 * {@link TableColumnConfiguration#isFilterable() filterable} column and applies the
 * {@link TableFilter} to the view's rows. It is created and installed by the
 * {@link TableConfiguration} if {@link TableConfiguration#isFilterEnabled() filtering is enabled}.
 * <p>
 * <strong>Table views</strong> are filtered by inserting a {@link FilteredList} into the view's item
 * chain. The chain always ends in a {@link SortedList} bound to the table's comparator, since the
 * table's sort policy cannot sort any other kind of list. As a consequence, the list returned by
 * {@link javafx.scene.control.TableView#getItems()} is immutable as soon as filtering is enabled:
 * modifications must be applied to {@link FxUtilities#getRootSource(ObservableList)}.
 * <p>
 * <strong>Treetable views</strong> are filtered level by level. A tree item is hidden if its own row
 * does not pass the filter, together with its whole subtree. The children of an item are evaluated
 * when the item is expanded and not before, so that lazily loading tree items (such as the PDO tree
 * items of the rich desktop client) are not forced to load the entire tree. Note that an item all of
 * whose children are filtered out still shows its disclosure arrow, since being a leaf is determined
 * by the tree item itself.
 *
 * @param <S> type of the objects contained within the table's or treetable's item list
 * @author harald
 */
public class TableFilterSupport<S> {

  /*
   * As elsewhere in this package, TableView and TreeTableView don't inherit from each other,
   * which is why one support serves both, with one of the two view references always being null.
   */

  /** the style class of the filter button in a column header. */
  public static final String FILTER_BUTTON_STYLE_CLASS = "tt-filter-button";

  /** the style class of the filter button if the column is filtered. */
  public static final String FILTER_BUTTON_ACTIVE_STYLE_CLASS = "tt-filter-button-active";

  /** the style class added to a filterable column. */
  public static final String FILTER_COLUMN_STYLE_CLASS = "tt-filter-column";

  /** the default maximum number of distinct values offered in the filter popup. */
  public static final int DEFAULT_MAX_DISTINCT_VALUES = 1000;


  /**
   * The values offered in a column's filter popup.
   *
   * @param values the values, the pseudo values being the first entries
   * @param totalCount the total number of distinct values found
   * @param truncated true if not all distinct values are contained in {@code values}
   */
  public record CollectedValues(List<TableFilterValue> values, int totalCount, boolean truncated) { }


  private final FxTableView<S> table;              // the table view, null if treetable
  private final FxTreeTableView<S> treeTable;      // the treetable view, null if table
  private final TableConfiguration<S> configuration;
  private final TableFilter<S> filter;

  private final Map<TableColumnConfiguration<S,?>, StackPane> headerButtons = new LinkedHashMap<>();

  private int maxDistinctValues = DEFAULT_MAX_DISTINCT_VALUES;
  private boolean installed;
  private TableFilterPopup<S,?> popup;

  // --- table view ---
  private InvalidationListener itemsListener;
  private FilteredList<S> filteredList;
  private ObservableList<S> originalItems;
  private boolean adjustingItems;

  // --- treetable view ---
  private final TreeItemFilter<S> treeItemFilter;
  private InvalidationListener rootListener;

  private final InvalidationListener revisionListener = _ -> filterChanged();


  /**
   * Creates the filter support for a table view.
   *
   * @param table the table view
   */
  public TableFilterSupport(FxTableView<S> table) {
    this(Objects.requireNonNull(table, "table"), null);
  }

  /**
   * Creates the filter support for a treetable view.
   *
   * @param treeTable the treetable view
   */
  public TableFilterSupport(FxTreeTableView<S> treeTable) {
    this(null, Objects.requireNonNull(treeTable, "treeTable"));
  }

  private TableFilterSupport(FxTableView<S> table, FxTreeTableView<S> treeTable) {
    this.table = table;
    this.treeTable = treeTable;
    this.configuration = table != null ? table.getConfiguration() : treeTable.getConfiguration();
    this.filter = createFilter();
    this.treeItemFilter = treeTable == null ? null : createTreeItemFilter();
  }


  @Override
  public String toString() {
    return (configuration == null ? "?" : configuration.getName()) + "[" + filter + "]";
  }


  /**
   * Gets the table view.
   *
   * @return the table view, null if this support belongs to a treetable view
   */
  public FxTableView<S> getTable() {
    return table;
  }

  /**
   * Gets the treetable view.
   *
   * @return the treetable view, null if this support belongs to a table view
   */
  public FxTreeTableView<S> getTreeTable() {
    return treeTable;
  }

  /**
   * Gets the table configuration.
   *
   * @return the configuration
   */
  public TableConfiguration<S> getConfiguration() {
    return configuration;
  }

  /**
   * Gets the filter.
   *
   * @return the filter, never null
   */
  public TableFilter<S> getFilter() {
    return filter;
  }

  /**
   * Gets the maximum number of distinct values offered in the filter popup.
   *
   * @return the maximum, defaults to {@value #DEFAULT_MAX_DISTINCT_VALUES}
   */
  public int getMaxDistinctValues() {
    return maxDistinctValues;
  }

  /**
   * Sets the maximum number of distinct values offered in the filter popup.
   *
   * @param maxDistinctValues the maximum
   */
  public void setMaxDistinctValues(int maxDistinctValues) {
    this.maxDistinctValues = maxDistinctValues;
  }


  /**
   * Installs the filter UI and applies the current filter.
   */
  public void install() {
    if (installed) {
      return;
    }
    installed = true;
    filter.revisionProperty().addListener(revisionListener);
    installHeaderButtons();
    if (table != null) {
      itemsListener = _ -> installItems(table.getItems());
      table.itemsProperty().addListener(itemsListener);
      installItems(table.getItems());
    }
    else {
      rootListener = _ -> {
        treeItemFilter.setRoot(treeTable.getRoot());
        treeItemFilter.filterTree();
      };
      treeTable.rootProperty().addListener(rootListener);
      treeItemFilter.setRoot(treeTable.getRoot());
      treeItemFilter.filterTree();
    }
  }

  /**
   * Uninstalls the filter UI and restores the unfiltered rows.
   */
  public void uninstall() {
    if (!installed) {
      return;
    }
    installed = false;
    hidePopup();
    filter.revisionProperty().removeListener(revisionListener);
    uninstallHeaderButtons();
    if (table != null) {
      table.itemsProperty().removeListener(itemsListener);
      itemsListener = null;
      if (originalItems != null && table.getItems() != originalItems) {
        adjustingItems = true;
        try {
          unbindSortedList(table.getItems());
          table.setItems(originalItems);
        }
        finally {
          adjustingItems = false;
        }
      }
      filteredList = null;
      originalItems = null;
    }
    else {
      treeTable.rootProperty().removeListener(rootListener);
      rootListener = null;
      treeItemFilter.showAll();
      treeItemFilter.reset();
    }
  }

  /**
   * Clears all column filters and updates the view.
   */
  public void clear() {
    filter.clear();
    refilter();
  }

  /**
   * Applies the current filter to the view.
   */
  public void refilter() {
    if (table != null) {
      applyPredicate();
    }
    else {
      treeItemFilter.filterTree();
    }
  }


  // ------------------- the filter model -------------------

  /**
   * Creates the filter.
   *
   * @return the filter, never null
   */
  protected TableFilter<S> createFilter() {
    return new TableFilter<>(configuration);
  }

  /**
   * Invoked whenever the filter has been modified.
   */
  protected void filterChanged() {
    refilter();
    updateHeaderButtons();
  }


  // ------------------- the table view -------------------

  /**
   * Installs the item chain of the table view.<br>
   * The chain becomes {@code source -> FilteredList -> SortedList}.
   *
   * @param items the items currently set on the table view
   */
  protected void installItems(ObservableList<S> items) {
    if (adjustingItems || items == null) {
      return;
    }
    if (items instanceof SortedList<?> sorted && sorted.getSource() == filteredList) {
      return;   // already ours
    }
    adjustingItems = true;
    try {
      originalItems = items;
      ObservableList<S> source = FxUtilities.getInstance().getRootSource(items);
      unbindSortedList(items);
      filteredList = new FilteredList<>(source);
      SortedList<S> sortedList = new SortedList<>(filteredList);
      sortedList.comparatorProperty().bind(table.comparatorProperty());
      List<S> selectedItems = List.copyOf(table.getSelectionModel().getSelectedItems());
      table.setItems(sortedList);
      applyPredicate();
      for (S item : selectedItems) {
        table.getSelectionModel().select(item);
      }
    }
    finally {
      adjustingItems = false;
    }
  }

  /**
   * Applies the filter's predicate to the filtered list.
   */
  protected void applyPredicate() {
    if (filteredList != null) {
      filteredList.setPredicate(filter.isActive() ? TableFilterPredicate.of(filter) : null);
    }
  }

  private void unbindSortedList(ObservableList<S> items) {
    if (items instanceof SortedList<S> sorted) {
      sorted.comparatorProperty().unbind();
    }
  }


  // ------------------- the treetable view -------------------

  /**
   * Creates the filter applied to the tree items.
   *
   * @return the tree filter
   */
  protected TreeItemFilter<S> createTreeItemFilter() {
    return new TreeItemFilter<>(filter);
  }

  /**
   * Gets the filter applied to the tree items.
   *
   * @return the tree filter, null if this support belongs to a table view
   */
  public TreeItemFilter<S> getTreeItemFilter() {
    return treeItemFilter;
  }

  /**
   * Discards what is known about a tree item's children.<br>
   * To be invoked by applications that replace the children of an item while a filter is active.
   *
   * @param item the tree item, null to invalidate the whole tree
   */
  public void invalidate(TreeItem<S> item) {
    if (treeItemFilter != null) {
      treeItemFilter.invalidate(item);
    }
  }

  // ------------------- the column headers -------------------

  /**
   * Installs the filter buttons in the headers of all filterable columns.
   */
  protected void installHeaderButtons() {
    if (configuration == null) {
      return;
    }
    for (TableColumnConfiguration<S,?> columnConfig : configuration.getColumnConfigurations()) {
      if (columnConfig.isFilterable()) {
        StackPane button = createHeaderButton(columnConfig);
        headerButtons.put(columnConfig, button);
        if (table != null) {
          TableColumn<S,?> column = columnConfig.getTableColumn();
          column.setGraphic(button);
          column.getStyleClass().add(FILTER_COLUMN_STYLE_CLASS);
        }
        else {
          TreeTableColumn<S,?> column = columnConfig.getTreeTableColumn();
          column.setGraphic(button);
          column.getStyleClass().add(FILTER_COLUMN_STYLE_CLASS);
        }
      }
    }
    updateHeaderButtons();
  }

  /**
   * Removes the filter buttons from the column headers.
   */
  protected void uninstallHeaderButtons() {
    for (TableColumnConfiguration<S,?> columnConfig : headerButtons.keySet()) {
      if (table != null) {
        TableColumn<S,?> column = columnConfig.getTableColumn();
        column.setGraphic(null);
        column.getStyleClass().remove(FILTER_COLUMN_STYLE_CLASS);
      }
      else {
        TreeTableColumn<S,?> column = columnConfig.getTreeTableColumn();
        column.setGraphic(null);
        column.getStyleClass().remove(FILTER_COLUMN_STYLE_CLASS);
      }
    }
    headerButtons.clear();
  }

  /**
   * Creates the filter button for a column header.
   *
   * @param columnConfig the column configuration
   * @return the button
   */
  protected StackPane createHeaderButton(TableColumnConfiguration<S,?> columnConfig) {
    StackPane button = FxFactory.getInstance().createButton(FILTER_BUTTON_STYLE_CLASS, "filter", "FILTER",
                                                            () -> showPopup(columnConfig));
    // The button lives in the column header, which sorts the column on a mouse click. The factory
    // already opens the popup on (and consumes) the press, but the release and the synthesized click
    // still bubble up to the header and would sort the column, closing the popup again. Swallow the
    // whole gesture, so a click on the filter icon only opens the popup — sorting is triggered from
    // the popup alone.
    button.addEventHandler(MouseEvent.MOUSE_RELEASED, MouseEvent::consume);
    button.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
    return button;
  }

  /**
   * Updates the filter buttons according to the filter's state.
   */
  protected void updateHeaderButtons() {
    for (Map.Entry<TableColumnConfiguration<S,?>, StackPane> entry : headerButtons.entrySet()) {
      StackPane button = entry.getValue();
      boolean active = filter.getColumnFilter(entry.getKey()).isActive();
      button.getStyleClass().remove(FILTER_BUTTON_ACTIVE_STYLE_CLASS);
      if (active) {
        button.getStyleClass().add(FILTER_BUTTON_ACTIVE_STYLE_CLASS);
      }
      button.getChildren().setAll(Fx.createGraphic(active ? "filter-active" : "filter"));
    }
  }

  /**
   * Shows the filter popup for a column.
   *
   * @param columnConfig the column configuration
   */
  public void showPopup(TableColumnConfiguration<S,?> columnConfig) {
    hidePopup();
    Node anchor = headerButtons.get(columnConfig);
    if (anchor != null) {
      popup = createPopup(filter.getColumnFilter(columnConfig));
      popup.showPopup(anchor);
    }
  }

  /**
   * Hides the filter popup, if showing.
   */
  public void hidePopup() {
    if (popup != null) {
      popup.hide();
      popup = null;
    }
  }

  /**
   * Creates the filter popup for a column.
   *
   * @param <T> the column's value type
   * @param columnFilter the column filter
   * @return the popup
   */
  protected <T> TableFilterPopup<S,T> createPopup(TableColumnFilter<S,T> columnFilter) {
    return new TableFilterPopup<>(this, columnFilter);
  }


  // ------------------- the distinct values -------------------

  /**
   * Collects the values selectable in the filter popup of a column.<p>
   * As in spreadsheet applications, the values are those of the rows passing the filters of all
   * <em>other</em> columns. For treetable views, only the materialized part of the tree is scanned,
   * i.e., the value list grows as the user expands the tree.
   *
   * @param <T> the column's value type
   * @param columnFilter the column filter
   * @return the collected values
   */
  public <T> CollectedValues collectValues(TableColumnFilter<S,T> columnFilter) {
    TableColumnConfiguration<S,T> columnConfig = columnFilter.getColumnConfiguration();
    FxTableBinding<S,T> binding = columnConfig.getBinding();
    Map<String, T> valuesByText = new LinkedHashMap<>();
    // the texts beyond the maximum are counted here: they must not be counted twice,
    // and they are no longer in valuesByText to tell them apart
    Set<String> droppedTexts = new HashSet<>();
    boolean blankFound = false;

    if (binding != null) {
      for (S row : collectRows()) {
        if (!filter.test(row, columnFilter)) {
          continue;
        }
        binding.setBoundRootObject(row);
        T value = binding.getModelValue();
        String text = columnConfig.getText(value);
        if (text == null || text.isBlank()) {
          blankFound = true;
        }
        else if (!valuesByText.containsKey(text)) {
          if (valuesByText.size() < maxDistinctValues) {
            valuesByText.put(text, value);
          }
          else {
            droppedTexts.add(text);
          }
        }
      }
    }

    boolean truncated = !droppedTexts.isEmpty();
    int distinctCount = valuesByText.size() + droppedTexts.size();

    List<Map.Entry<String, T>> entries = new ArrayList<>(valuesByText.entrySet());
    entries.sort(this::compareValues);

    List<TableFilterValue> values = new ArrayList<>(entries.size() + 2);
    values.add(new TableFilterValue(TableFilterValue.KIND.SELECT_ALL, null,
                                    FxFxBundle.getString("(SELECT ALL)"), null));
    if (blankFound) {
      values.add(new TableFilterValue(TableFilterValue.KIND.BLANK, null,
                                      FxFxBundle.getString("(BLANK)"), null));
    }
    for (Map.Entry<String, T> entry : entries) {
      values.add(new TableFilterValue(TableFilterValue.KIND.VALUE, entry.getKey(), entry.getKey(),
                                      columnConfig.getGraphic(entry.getValue())));
    }

    applySelection(columnFilter, values);

    return new CollectedValues(values, distinctCount, truncated);
  }

  /**
   * Applies the current filter state to the collected values.
   *
   * @param columnFilter the column filter
   * @param values the collected values
   */
  protected void applySelection(TableColumnFilter<S,?> columnFilter, List<TableFilterValue> values) {
    boolean valueSetActive = columnFilter.isValueSetActive();
    boolean allSelected = true;
    for (TableFilterValue value : values) {
      boolean selected = switch (value.getKind()) {
        case SELECT_ALL -> true;
        case BLANK -> !valueSetActive || columnFilter.isBlankSelected();
        case VALUE -> !valueSetActive || columnFilter.getSelectedTexts().contains(value.getText());
      };
      value.setSelected(selected);
      if (!value.isPseudo() || value.getKind() == TableFilterValue.KIND.BLANK) {
        allSelected &= selected;
      }
    }
    if (!values.isEmpty()) {
      values.getFirst().setSelected(allSelected);
    }
  }

  /**
   * Gets the rows the distinct values are collected from.
   *
   * @return the rows
   */
  protected List<S> collectRows() {
    if (table != null) {
      return new ArrayList<>(FxUtilities.getInstance().getRootSource(table.getItems()));
    }
    return treeItemFilter.collectRows();
  }

  /**
   * Compares two distinct values.<br>
   * Values are ordered naturally, if possible, else by their displayed text.
   *
   * @param <T> the column's value type
   * @param first the first entry
   * @param second the second entry
   * @return the comparison result
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected <T> int compareValues(Map.Entry<String, T> first, Map.Entry<String, T> second) {
    T firstValue = first.getValue();
    T secondValue = second.getValue();
    if (firstValue instanceof Comparable && secondValue != null &&
        firstValue.getClass() == secondValue.getClass()) {
      return ((Comparable) firstValue).compareTo(secondValue);
    }
    return getCollator().compare(first.getKey(), second.getKey());
  }

  /**
   * Gets the collator to order the values by their displayed text.
   *
   * @return the collator, never null
   */
  protected Collator getCollator() {
    return Collator.getInstance(LocaleProvider.getInstance().getEffectiveLocale());
  }

}
